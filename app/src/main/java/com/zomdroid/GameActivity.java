package com.zomdroid;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.system.ErrnoException;
import android.util.Log;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.zomdroid.input.GLFWBinding;
import com.zomdroid.input.GamepadManager;
import com.zomdroid.input.InputNativeInterface;
import com.zomdroid.databinding.ActivityGameBinding;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;
import com.zomdroid.input.InputControlsView;
import com.zomdroid.input.KeyboardManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

import org.fmod.FMOD;

import java.io.File;

/**
 * Main game activity. Handles UI, surface, and input.
 * Integrates GamepadManager for hotplug and routes all gamepad input to the native interface.
 * Hides the virtual controller UI when a physical gamepad is connected.
 */
public class GameActivity extends AppCompatActivity implements GamepadManager.GamepadListener, KeyboardManager.KeyboardListener {
    public static final String EXTRA_GAME_INSTANCE_NAME = "com.zomdroid.GameActivity.EXTRA_GAME_INSTANCE_NAME";
    private static final String LOG_TAG = GameActivity.class.getName();

    private ActivityGameBinding binding;
    private Surface gameSurface;
    private static boolean isGameStarted = false;
    private static final int REQUEST_CONTROLS_EDITOR = 4101;
    private static volatile String activeGameInstanceName;

    // Handles all gamepad connection/disconnection and input events
    private GamepadManager gamepadManager;
    private KeyboardManager keyboardManager;

    // Tracks whether a physical gamepad/kb is currently connected (for UI logic)
    private boolean isGamepadConnected = false;
    private boolean isKeyboardConnected = false;

    private boolean leftMouseDown  = false;
    private boolean rightMouseDown = false;

    private boolean systemKeyboardVisible = false;
    private String gameInstanceName;
    private GameInstance gameInstance;
    private boolean exitInProgress = false;
    // Helps to calculate mouse cursor position
    private float renderScale = 1f;

    @SuppressLint({"UnsafeDynamicallyLoadedCode", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    0);
        }

        binding = ActivityGameBinding.inflate(getLayoutInflater());
        // Give focus to game surface to ensure it receives input events
        setContentView(binding.getRoot());
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // set instanceName before any inputControlsV calls
        gameInstanceName = getIntent().getStringExtra(EXTRA_GAME_INSTANCE_NAME);
        if (gameInstanceName != null) {
            binding.inputControlsV.setInstanceName(gameInstanceName);
        }

        binding.gameSv.setFocusable(true);
        binding.gameSv.setFocusableInTouchMode(true);
        binding.gameSv.requestFocus();

        // Initializing the cursor calsulation pos helper
        renderScale = LauncherPreferences.requireSingleton().getRenderScale();

        // Initialize and register GamepadManager for gamepad hotplug and input events
        try {
            gamepadManager = new GamepadManager(this, this);
            //gamepadManager.register();

            // Apply touch override based on saved preference
            boolean isTouchEnabled = LauncherPreferences.requireSingleton().isTouchControlsEnabled();
            GamepadManager.setTouchOverride(isTouchEnabled);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to initialize GamepadManager", e);
            gamepadManager = null;
        }
        // Initialize KeyboardManager
        try {
            keyboardManager = new KeyboardManager(this, this);
            //keyboardManager.register();

          // Apply touch override based on saved preference
          boolean isTouchEnabled = LauncherPreferences.requireSingleton().isTouchControlsEnabled();
          KeyboardManager.setTouchOverride(isTouchEnabled);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to initialize keyboardManager", Toast.LENGTH_SHORT).show();
            keyboardManager = null;
        }
        // Display on/off buttons overlay
        applyInputOverlay();
        binding.inputControlsV.setKeyboardToggleListener(() -> toggleSystemKeyboard());
        binding.inputControlsV.setRenderScale(renderScale);

        getWindow().setDecorFitsSystemWindows(false);
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        // Orientation is fixed in the manifest (screenOrientation + configChanges) rather than here.
        // Calling setRequestedOrientation() from onCreate() on a phone held in portrait recreated the
        // activity immediately, so onCreate() ran twice and fired the RECORD_AUDIO request twice —
        // Android drops the second one ("Can request only one set of permissions at a time", present
        // in every bug report we have). The manifest form starts the activity in landscape to begin
        // with, and configChanges keeps a rotation from tearing down the GL surface underneath us.

        if (gameInstanceName == null)
            throw new RuntimeException("Expected game instance name to be passed as intent extra");
        gameInstance = GameInstanceManager.requireSingleton().getInstanceByName(gameInstanceName);
        if (gameInstance == null)
            throw new RuntimeException("Game instance with name " + gameInstanceName + " not found");

        activeGameInstanceName = gameInstanceName;
        setupGameDrawer();

        // Build 42.20 binds trigger actions to thresholds that assume a real pad's axis range,
        // e.g. "Melee > -0.80" on the left trigger. A released trigger has to read as -1 for that
        // to mean "not pressed"; sending 0 like we do everywhere else leaves Melee permanently on.
        // Scoped to 42.20+ on purpose: the older builds work with the current range and are frozen,
        // so they keep the exact code path they have today and need no retesting.
        GamepadManager.setBipolarTriggers(gameInstance.isBuild4220Plus());

        System.loadLibrary("zomdroid");

        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmod.so");
        System.load(AppStorage.requireSingleton().getHomePath() + "/" + gameInstance.getFmodLibraryPath() + "/libfmodstudio.so");

        FMOD.init(this);

        binding.gameSv.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface created.");
                renderScale = LauncherPreferences.requireSingleton().getRenderScale();
                int width = (int) (binding.gameSv.getWidth() * renderScale);
                int height = (int) (binding.gameSv.getHeight() * renderScale);
                binding.gameSv.getHolder().setFixedSize(width, height);
                binding.inputControlsV.setRenderScale(renderScale);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(LOG_TAG, "Game surface changed.");
                gameSurface = binding.gameSv.getHolder().getSurface();
                //gameSurface = holder.getSurface();
                if (gameSurface == null) throw new RuntimeException();

                if (format != PixelFormat.RGBA_8888) {
                    Log.w(LOG_TAG, "Using unsupported pixel format " + format); // LIAMELUI seems like default is RGB_565
                }

                GameLauncher.setSurface(gameSurface, width, height);

                if (!isGameStarted) {
                    Thread thread = new Thread(() -> {
                        try {
                            GameLauncher.launch(gameInstance);
                        } catch (ErrnoException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    thread.start();
                    isGameStarted = true;
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                Log.d(LOG_TAG, "Game surface destroyed.");
                GameLauncher.destroySurface();
            }
        });

      binding.gameSv.setOnTouchListener(new View.OnTouchListener() {
        //float renderScale = LauncherPreferences.requireSingleton().getRenderScale();
        int activePointerId = -1;
        boolean leftPressedFinger = false;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (binding.inputControlsV != null
                    && binding.inputControlsV.getVisibility() == View.VISIBLE
                    && binding.inputControlsV.onTouchEvent(e)) {
                //Log.v("ZomdroidTouch", "inputControlsV consumed event");
                return true;
            }
            //Log.v("ZomdroidTouch", "inputControlsV did NOT consume, visibility="  + binding.inputControlsV.getVisibility());

          int action = e.getActionMasked();
          int idx = e.getActionIndex();

          switch (action) {
              case MotionEvent.ACTION_DOWN:
              case MotionEvent.ACTION_POINTER_DOWN: {
                activePointerId = e.getPointerId(idx);
                float x = e.getX(idx), y = e.getY(idx);
                InputNativeInterface.sendCursorPos(x * renderScale, y * renderScale);

                leftPressedFinger = true;
                leftMouseDown = true;
                InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, true);
                if (isMouseEvent(e, idx)) {
                    syncMouseReleaseFromMask(e.getButtonState()); // тихий релиз, press не генерим
                }
                return true;
              }
              case MotionEvent.ACTION_MOVE: {
                if (activePointerId < 0) return false;
                int p = e.findPointerIndex(activePointerId);
                if (p < 0) { activePointerId = -1; return false; }
                float x = e.getX(p), y = e.getY(p);
                //dbg("ACTION_MOVE");
                InputNativeInterface.sendCursorPos(x * renderScale, y * renderScale);
                if (isMouseEvent(e, p)) {
                    syncMouseReleaseFromMask(e.getButtonState());
                }

                return true;
              }
              case MotionEvent.ACTION_UP:
              case MotionEvent.ACTION_POINTER_UP: {
                if (activePointerId < 0) return false;
                float x = e.getX(idx), y = e.getY(idx);
                if (leftPressedFinger) {
                  InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                  leftPressedFinger = false;
                }
                leftMouseDown = false;
                InputNativeInterface.sendCursorPos(x * renderScale, y * renderScale);
                if (isMouseEvent(e, idx)) {
                    syncMouseReleaseFromMask(e.getButtonState());
                }
                activePointerId = -1;
                return true;
              }
              case MotionEvent.ACTION_CANCEL: {
                if (leftPressedFinger || leftMouseDown) {
                    InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
                    leftPressedFinger = false;
                    leftMouseDown = false;
                }
                activePointerId = -1;
                return true;
            }
          }
          return false;
        }
      });
    }

    private void setupGameDrawer() {
        binding.gameDrawerInstance.setText(getString(R.string.game_menu_instance_format, gameInstanceName));

        binding.gameDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        binding.gameDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                // Keep the game surface focused only while the drawer is closed. This prevents
                // keyboard/game input from leaking through the drawer rows.
                drawerView.requestFocus();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                binding.gameSv.requestFocus();
            }
        });

        binding.gameEditControlsRow.setOnClickListener(v -> openControlsEditor());

        boolean touchEnabled = LauncherPreferences.requireSingleton().isTouchControlsEnabled();
        binding.gameTouchControlsSwitch.setChecked(touchEnabled);
        binding.gameTouchControlsSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                applyTouchControlsSetting(isChecked));
        binding.gameTouchControlsRow.setOnClickListener(v ->
                binding.gameTouchControlsSwitch.toggle());

        binding.gameVibrateSwitch.setChecked(LauncherPreferences.requireSingleton().isVibrateOnTouch());
        binding.gameVibrateSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                LauncherPreferences.requireSingleton().setVibrateOnTouch(isChecked));
        binding.gameVibrateRow.setOnClickListener(v -> binding.gameVibrateSwitch.toggle());

        updateQuickSaveMenuState();
        binding.gameQuickSaveRow.setOnClickListener(v -> triggerQuickSave());
        binding.gameReturnLauncherRow.setOnClickListener(v -> returnToLauncher());
        binding.gameExitRow.setOnClickListener(v -> confirmExitToLauncher());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.gameDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.gameDrawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    binding.gameDrawerLayout.openDrawer(GravityCompat.START);
                }
            }
        });
    }

    private void applyTouchControlsSetting(boolean enabled) {
        LauncherPreferences.requireSingleton().setTouchControlsEnabled(enabled);
        GamepadManager.setTouchOverride(enabled);
        KeyboardManager.setTouchOverride(enabled);
        // Re-scan devices immediately. Without this, turning the override off after a controller
        // was already connected would leave the overlay visible until the next hotplug event.
        if (gamepadManager != null) {
            gamepadManager.unregister();
            gamepadManager.register();
            isGamepadConnected = gamepadManager.hasConnectedGamepad();
        }
        if (keyboardManager != null) {
            keyboardManager.unregister();
            keyboardManager.register();
            isKeyboardConnected = keyboardManager.hasConnectedKeyboard();
        }
        applyInputOverlay();
    }

    private void updateQuickSaveMenuState() {
        boolean build42 = gameInstance.getBuildVersion() != null
                && gameInstance.getBuildVersion().startsWith("42");
        binding.gameQuickSaveRow.setEnabled(build42);
        binding.gameQuickSaveRow.setAlpha(build42 ? 1f : 0.5f);
        if (!build42) {
            binding.gameQuickSaveStatus.setText(R.string.game_menu_quick_save_build42_only);
        } else if (LauncherPreferences.requireSingleton().isQuickSaveBackup()) {
            binding.gameQuickSaveStatus.setText(R.string.game_menu_quick_save_enabled);
        } else {
            binding.gameQuickSaveStatus.setText(R.string.game_menu_quick_save_disabled);
        }
    }

    private void triggerQuickSave() {
        boolean build42 = gameInstance.getBuildVersion() != null
                && gameInstance.getBuildVersion().startsWith("42");
        if (!build42) {
            Toast.makeText(this, R.string.game_menu_quick_save_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        LauncherPreferences preferences = LauncherPreferences.requireSingleton();
        if (!preferences.isQuickSaveBackup()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.backup_warning_title)
                    .setMessage(R.string.backup_warning_message)
                    .setPositiveButton(R.string.dialog_button_confirm, (dialog, which) -> {
                        preferences.setQuickSaveBackup(true);
                        updateQuickSaveMenuState();
                        sendQuickSaveKey();
                    })
                    .setNegativeButton(R.string.dialog_button_cancel, null)
                    .show();
            return;
        }
        sendQuickSaveKey();
    }

    private void sendQuickSaveKey() {
        binding.gameDrawerLayout.closeDrawer(GravityCompat.START);
        InputNativeInterface.sendKeyboard(GLFWBinding.KEY_F10.code, true);
        binding.gameSv.postDelayed(
                () -> InputNativeInterface.sendKeyboard(GLFWBinding.KEY_F10.code, false), 50L);
        Toast.makeText(this, R.string.game_menu_quick_save_started, Toast.LENGTH_SHORT).show();
    }

    private void openControlsEditor() {
        try {
            Intent intent = new Intent(this, ControlsEditorActivity.class);
            intent.putExtra(ControlsEditorActivity.EXTRA_INSTANCE_NAME, gameInstanceName);
            File background = new File(gameInstance.getHomePath(), "game/controls/editor_background.jpg");
            if (background.isFile()) {
                intent.putExtra(ControlsEditorActivity.EXTRA_BACKGROUND_PATH, background.getAbsolutePath());
            }
            binding.gameDrawerLayout.closeDrawer(GravityCompat.START);
            startActivityForResult(intent, REQUEST_CONTROLS_EDITOR);
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Unable to open controls editor", e);
            Toast.makeText(this, R.string.game_menu_controls_editor_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmExitToLauncher() {
        if (exitInProgress) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.game_menu_exit_title)
                .setMessage(R.string.game_menu_exit_message)
                .setNegativeButton(R.string.game_menu_return_to_game, null)
                .setPositiveButton(R.string.game_menu_exit_confirm, (dialog, which) -> exitToLauncher())
                .show();
    }

    private void returnToLauncher() {
        binding.gameDrawerLayout.closeDrawers();
        // Reorder the existing launcher Activity to the front. GameActivity stays underneath with
        // its JVM and surface alive, so selecting the same instance or pressing Back can resume it.
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    public static boolean hasActiveGame() {
        return activeGameInstanceName != null;
    }

    public static boolean isActiveGameInstance(String instanceName) {
        return instanceName != null && instanceName.equals(activeGameInstanceName);
    }

    public static void resumeActiveGame(Context context) {
        Intent intent = new Intent(context, GameActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        context.startActivity(intent);
    }

    private void exitToLauncher() {
        if (exitInProgress) return;
        if (!GameLauncher.requestGameExit()) {
            Toast.makeText(this, R.string.game_menu_exit_failed, Toast.LENGTH_LONG).show();
            return;
        }
        exitInProgress = true;
        binding.gameExitRow.setEnabled(false);
        binding.gameDrawerLayout.closeDrawers();
        waitForGameExit();
    }

    private void waitForGameExit() {
        if (!exitInProgress) return;
        if (!GameLauncher.isGameRunning()) {
            GameLauncher.destroyZomdroidWindow();
            finish();
            return;
        }
        binding.gameSv.postDelayed(this::waitForGameExit, 100L);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONTROLS_EDITOR && binding != null) {
            binding.inputControlsV.loadControlElementsFromDisk();
            applyInputOverlay();
            binding.gameSv.requestFocus();
        }
    }

    @Override
    protected void onDestroy() {
      isGameStarted = false;
      if (gameInstanceName != null && gameInstanceName.equals(activeGameInstanceName)) {
          activeGameInstanceName = null;
      }
      super.onDestroy();
      // Unregister GamepadManager to avoid leaks
      if (gamepadManager != null) {
          gamepadManager.unregister();
      }

      // Unregister Keyboard to avoid leaks
      if (keyboardManager != null) {
          keyboardManager.unregister();
      }
    }

    // GamepadManager.GamepadListener implementation

    // Called when any physical gamepad is connected: hide the virtual controller UI
    @Override
    public void onGamepadConnected() {
        runOnUiThread(() -> {
            isGamepadConnected = true;
            applyInputOverlay();
        });
    }

    // Called when all physical gamepads are disconnected: show the virtual controller UI
    @Override
    public void onGamepadDisconnected() {
        runOnUiThread(() -> {
            isGamepadConnected = false;
            applyInputOverlay();
        });
    }

    // Forward every gamepad button event to the native input interface
    @Override
    public void onGamepadButton(int button, boolean pressed) {
        InputNativeInterface.sendJoystickButton(button, pressed);
    }

    // Forward every gamepad axis event to the native input interface
    @Override
    public void onGamepadAxis(int axis, float value) {
        InputNativeInterface.sendJoystickAxis(axis, value);
    }

    // Forward every gamepad dpad event to the native input interface
    @Override
    public void onGamepadDpad(int dpad, char state) {
        InputNativeInterface.sendJoystickDpad(dpad, state);
    }

    // Handle gamepad key events
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = false;
        //if (isKeyboardConnected && (keyboardManager != null)) handled |= keyboardManager.handleKeyEvent(event);
        if (keyboardManager != null) handled |= keyboardManager.handleKeyEvent(event);
        if (isGamepadConnected && (gamepadManager  != null)) handled |= gamepadManager.handleKeyEvent(event);
        if (handled) return true;
        //if (isKeyboardConnected) return true; // if physical kb connected not sending to typing
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean handled = false;
        //if (isKeyboardConnected && (keyboardManager != null)) handled |= keyboardManager.handleKeyEvent(event);
        if (keyboardManager != null) handled |= keyboardManager.handleKeyEvent(event);
        if (isGamepadConnected && (gamepadManager  != null)) handled |= gamepadManager.handleKeyEvent(event);
        if (handled) return true;
        //if (isKeyboardConnected) return true;
        return super.onKeyUp(keyCode, event);
    }


    // Handle gamepad/keyboard motion events
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
      //float renderScale = LauncherPreferences.requireSingleton().getRenderScale();

      boolean isPointerDevice = event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD) || event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;

      if (!isPointerDevice) {
          if (gamepadManager != null && gamepadManager.handleMotionEvent(event)) return true;
          return super.onGenericMotionEvent(event);
      }

      int action = event.getActionMasked();
      int btn = event.getActionButton();

      // Cursor movement: always update position and if LMB/RMB is held — it's a drag of crosshair/objects
      //if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_MOVE) {
      if (action == MotionEvent.ACTION_HOVER_MOVE) {
        float x = event.getX();
        float y = event.getY();
        InputNativeInterface.sendCursorPos(x * renderScale, y * renderScale);
        syncMouseReleaseFromMask(event.getButtonState());

        if (leftMouseDown || rightMouseDown) {
          //dbg("DRAG move");
          return true;
        }
        return true;
      }

      if (action == MotionEvent.ACTION_SCROLL) {
        float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (v == 0) {
            v = event.getAxisValue(MotionEvent.AXIS_WHEEL);
        }

        if (v != 0) {
            InputNativeInterface.sendMouseScroll(0.0, v > 0 ? 1.0 : -1.0);
        }
        return true;
      }

      if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
          boolean pressed = (action == MotionEvent.ACTION_BUTTON_PRESS);
          InputNativeInterface.sendCursorPos(event.getX() * renderScale, event.getY() * renderScale);

        if (btn == MotionEvent.BUTTON_PRIMARY) {
          //dbg(pressed ? "LMB PRESS" : "LMB RELEASE");
          leftMouseDown = pressed;
          InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, pressed);
          syncMouseReleaseFromMask(event.getButtonState());
          return true;
        } else if (btn == MotionEvent.BUTTON_SECONDARY) {
          //dbg(pressed ? "RMB PRESS" : "RMB RELEASE");
          rightMouseDown = pressed;
          InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_RIGHT.code, pressed);
          syncMouseReleaseFromMask(event.getButtonState());
          return true;
        }
      }
      return super.onGenericMotionEvent(event);
    }

    @Override
    public void onKeyboardConnected() {
        runOnUiThread(() -> {
            if (binding == null) return;
            isKeyboardConnected = true;
            // 1) Жёстко выключаем IME-режим SurfaceView
            systemKeyboardVisible = false;
            if (binding.gameSv != null) {
                binding.gameSv.setAcceptingTextInput(false);
            }
            hideSystemKeyboard(); // на всякий случай
            binding.inputControlsV.setKeyboardConnected(true);
            reapplyImmersiveMode();
            applyInputOverlay();
        });
    }

    @Override
    public void onKeyboardDisconnected() {
        runOnUiThread(() -> {
            if (binding == null) return;
            isKeyboardConnected = false;
            binding.inputControlsV.setKeyboardConnected(false);
            applyInputOverlay();
        });
    }

    @Override
    public void onKeyboardKey(int glfwCode, boolean pressed) {
      InputNativeInterface.sendKeyboard(glfwCode, pressed);
    }

    private void applyInputOverlay() {
      if (binding == null || binding.inputControlsV == null) return;
      binding.inputControlsV.setGamepadConnected(isGamepadConnected);

      boolean touchEnabled = LauncherPreferences.requireSingleton().isTouchControlsEnabled();
      if (touchEnabled) {
        binding.inputControlsV.setVisibility(View.VISIBLE);
        binding.inputControlsV.applyInputMode(InputControlsView.InputMode.ALL);
      } else if (isKeyboardConnected) {
        binding.inputControlsV.setVisibility(View.GONE);
      } else if (isGamepadConnected) {
        binding.inputControlsV.setVisibility(View.VISIBLE);
        binding.inputControlsV.applyInputMode(InputControlsView.InputMode.MNK);
      } else {
        binding.inputControlsV.setVisibility(View.VISIBLE);
        binding.inputControlsV.applyInputMode(InputControlsView.InputMode.ALL);
      }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gamepadManager != null)  gamepadManager.register();
        if (keyboardManager != null) keyboardManager.register();
    }

    @Override
    protected void onPause() {
        if (gamepadManager != null)  gamepadManager.unregister();
        if (keyboardManager != null) keyboardManager.unregister();
        super.onPause();
    }

    private boolean isMouseEvent(MotionEvent e, int pointerIndex) {
        return e.isFromSource(InputDevice.SOURCE_MOUSE)
            || e.isFromSource(InputDevice.SOURCE_TOUCHPAD)
            || (pointerIndex >= 0 && pointerIndex < e.getPointerCount()
                && e.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_MOUSE);
    }

    private void syncMouseReleaseFromMask(int mask) {
        boolean leftNow  = (mask & MotionEvent.BUTTON_PRIMARY)   != 0;
        boolean rightNow = (mask & MotionEvent.BUTTON_SECONDARY) != 0;

        if (!leftNow && leftMouseDown) {
            leftMouseDown = false;
            InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_LEFT.code, false);
        }
        if (!rightNow && rightMouseDown) {
            rightMouseDown = false;
            InputNativeInterface.sendMouseButton(GLFWBinding.MOUSE_BUTTON_RIGHT.code, false);
        }
    }

    public void showSystemKeyboard() {
        binding.gameSv.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.gameSv, InputMethodManager.SHOW_FORCED);
        }
    }

    public void hideSystemKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.gameSv.getWindowToken(), 0);
        }
    }

    private void toggleSystemKeyboard() {
        if (isKeyboardConnected) return; // физическая клавиатура — не трогаем
        boolean next = !systemKeyboardVisible;
        binding.gameSv.setAcceptingTextInput(next);
        systemKeyboardVisible = next;
    }

    private void reapplyImmersiveMode() {
        final WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars()
                    | WindowInsets.Type.ime());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        binding.gameSv.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BACK
                || kc == KeyEvent.KEYCODE_VOLUME_UP
                || kc == KeyEvent.KEYCODE_VOLUME_DOWN
                || kc == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        }

        boolean physicalKeyboardEvent = isTruePhysicalKeyboardEvent(event);
        boolean textInputMode = binding != null
                && binding.gameSv != null
                && binding.gameSv.isAcceptingTextInput();

        // Блокируем "утечку" физических клавиш в IME только когда НЕ идёт осознанный text input.
        if (isKeyboardConnected && physicalKeyboardEvent && !textInputMode) {
            if (keyboardManager != null && keyboardManager.handleKeyEvent(event)) {
                return true;
            }
            return true; // глушим, чтобы Gboard не превращал аппаратный ввод в typing
        }

        return super.dispatchKeyEvent(event);
    }

    private boolean isTruePhysicalKeyboardEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return false;

        // Геймпад тоже репортит SOURCE_KEYBOARD для кнопок A/B/X/Y/Start —
        // исключаем его явно, иначе геймпадные кнопки тоже будут заглушены
        boolean isGamepad = (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        if (isGamepad) return false;

        return !device.isVirtual()
                && (event.isFromSource(InputDevice.SOURCE_KEYBOARD)
                || (device.getSources() & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD);
    }
}

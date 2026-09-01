package com.zomdroid;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;

import com.zomdroid.input.AbstractControlElement;
import com.zomdroid.input.ButtonControlElement;
import com.zomdroid.input.DpadControlElement;
import com.zomdroid.input.InputControlsView;
import com.zomdroid.input.RadialMenuControlElement;
import com.zomdroid.ui.controls.ControlsEditorHost;
import com.zomdroid.ui.controls.ControlsEditorScreenKt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Hosts the legacy input canvas and the Compose editor shell.
 *
 * The canvas remains an Android View because it owns hit testing, gesture detection and the
 * persisted control model. Everything the user can configure around it is rendered by Compose.
 */
public class ControlsEditorActivity extends AppCompatActivity implements ControlsEditorHost {
    public static final String EXTRA_BACKGROUND_PATH = "com.zomdroid.ControlsEditorActivity.EXTRA_BACKGROUND_PATH";
    public static final String EXTRA_INSTANCE_NAME = "com.zomdroid.ControlsEditorActivity.EXTRA_INSTANCE_NAME";

    private InputControlsView inputControlsView;
    private ActivityResultLauncher<String> pickIconLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        ImageView background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));

        inputControlsView = new InputControlsView(this, null);
        String instanceName = getIntent().getStringExtra(EXTRA_INSTANCE_NAME);
        if (instanceName != null) inputControlsView.setInstanceName(instanceName);
        inputControlsView.setEditMode(true);
        inputControlsView.setBackgroundColor(0x00000000);

        ComposeView composeRoot = new ComposeView(this);
        composeRoot.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        root.addView(composeRoot, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        String backgroundPath = getIntent().getStringExtra(EXTRA_BACKGROUND_PATH);
        if (backgroundPath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(backgroundPath);
            if (bitmap != null) background.setImageBitmap(bitmap);
        }

        pickIconLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) onIconPicked(uri);
        });

        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        ControlsEditorScreenKt.installControlsEditorScreen(composeRoot, inputControlsView, this);
    }

    @Override public void onPickCustomIcon() { pickIconLauncher.launch("image/*"); }

    @Override public void onExit() { finish(); }

    private void onIconPicked(Uri uri) {
        AbstractControlElement element = inputControlsView.getSelectedElement();
        if (!(element instanceof ButtonControlElement)
                && !(element instanceof DpadControlElement)
                && !(element instanceof RadialMenuControlElement)) {
            Toast.makeText(this, R.string.control_element_custom_icon_select_button, Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = inputControlsView.getControlsIconsDir();
        if (dir == null) {
            Toast.makeText(this, R.string.control_element_custom_icon_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, R.string.control_element_custom_icon_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(stream, null, options);
            }
            if (bitmap == null) throw new IllegalArgumentException("bitmap");
            bitmap = trimTransparentBorder(bitmap);
            String fileName = "icon_" + System.currentTimeMillis() + ".png";
            try (FileOutputStream output = new FileOutputStream(new File(dir, fileName))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            boolean noTint = element instanceof ButtonControlElement
                    ? ((ButtonControlElement) element).isNoTint()
                    : element instanceof DpadControlElement
                    ? ((DpadControlElement) element).isNoTint()
                    : ((RadialMenuControlElement) element).isNoTint();
            if (element instanceof ButtonControlElement) ((ButtonControlElement) element).setCustomIcon(fileName, noTint);
            else if (element instanceof DpadControlElement) ((DpadControlElement) element).setCustomIcon(fileName, noTint);
            else ((RadialMenuControlElement) element).setCustomIcon(fileName, noTint);
            inputControlsView.invalidate();
        } catch (Exception error) {
            Toast.makeText(this, R.string.control_element_custom_icon_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static Bitmap trimTransparentBorder(Bitmap source) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((pixels[y * width + x] >>> 24) & 0xff) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX) return source;
        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) return source;
        return Bitmap.createBitmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Override
    protected void onPause() {
        if (inputControlsView != null) inputControlsView.saveControlElementsToDisk();
        super.onPause();
    }
}

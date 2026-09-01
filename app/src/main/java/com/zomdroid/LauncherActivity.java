package com.zomdroid;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;

import com.zomdroid.game.BackupManager;
import com.zomdroid.game.GameInstance;
import com.zomdroid.game.GameInstanceManager;
import com.zomdroid.input.GamepadManager;
import com.zomdroid.ui.ZomdroidAppKt;
import com.zomdroid.ui.ZomdroidHostCallbacks;
import com.zomdroid.ui.settings.LauncherAppearanceModeStore;
import com.zomdroid.ui.settings.UiSettingsRepository;
import com.zomdroid.ui.theme.AppThemeMode;
import com.zomdroid.ui.viewmodel.AppViewModel;

/** Compose-only management host. GameActivity remains a separate rendering/touch boundary. */
public class LauncherActivity extends AppCompatActivity {
    private AppViewModel appViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        GamepadManager.loadCustomMapping(this);

        appViewModel = new AppViewModel(new UiSettingsRepository(
                new LauncherAppearanceModeStore(LauncherPreferences.requireSingleton())));
        ComposeView root = new ComposeView(this);
        root.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed.INSTANCE);
        setContentView(root);
        ZomdroidAppKt.installZomdroidApp(root, appViewModel, composeThemeMode(), new ZomdroidHostCallbacks() {
            @Override public void onOpenLegacy(com.zomdroid.ui.model.LegacyDestination destination) {
                // Compatibility events are now reduced to Compose module navigation in ZomdroidApp.
            }

            @Override public void onRequestPermission(String permission) {
                if (permission != null && !permission.trim().isEmpty()
                        && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{permission}, 1001);
                }
            }

            @Override public void onOpenLegacyMenu() { /* Compose owns the module drawer. */ }

            @Override public void onLaunchGame(String instanceName) {
                GameInstance instance = GameInstanceManager.requireSingleton().getInstanceByName(instanceName);
                if (instance == null) return;
                BackupManager.cleanupInterruptedRestore(instance);
                Intent intent = new Intent(LauncherActivity.this, GameActivity.class)
                        .putExtra(GameActivity.EXTRA_GAME_INSTANCE_NAME, instanceName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override public void onOpenStorage(String homePath) {
                Uri folderUri = DocumentsContract.buildDocumentUri(C.STORAGE_PROVIDER_AUTHORITY, homePath);
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, null));
            }

            @Override public void onOpenWiki() { /* Wiki is a Compose settings destination. */ }

            @Override public void onOpenGamepadMapper() {
                startActivity(new Intent(LauncherActivity.this, GamepadMapperActivity.class));
            }

            @Override public void onOpenControlsEditor(String instanceName, String backgroundPath) {
                Intent intent = new Intent(LauncherActivity.this, ControlsEditorActivity.class)
                        .putExtra(ControlsEditorActivity.EXTRA_INSTANCE_NAME, instanceName);
                if (backgroundPath != null) intent.putExtra(ControlsEditorActivity.EXTRA_BACKGROUND_PATH, backgroundPath);
                startActivity(intent);
            }

            @Override public void onOpenExternalUrl(String url) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception error) { Toast.makeText(LauncherActivity.this, R.string.workshop_open_steam_failed, Toast.LENGTH_SHORT).show(); }
            }

            @Override public void onRequestAllFilesAccess() {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (Exception error) { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            }
        });
    }

    private AppThemeMode composeThemeMode() {
        switch (LauncherPreferences.requireSingleton().getThemeMode()) {
            case LIGHT: return AppThemeMode.Light;
            case DARK: return AppThemeMode.Dark;
            default: return AppThemeMode.FollowSystem;
        }
    }
}

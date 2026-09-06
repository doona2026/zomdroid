package com.zomdroid;

import android.app.Activity;

import androidx.annotation.NonNull;

/**
 * Applies the selected colour palette before an Activity inflates its layout.
 *
 * <p>This class only owns colour selection. Night mode continues to be managed by
 * {@link LauncherPreferences.ThemeMode}; the two settings must not become coupled.</p>
 */
public final class ThemeManager {
    private ThemeManager() {}

    public static void applyColorTheme(@NonNull Activity activity) {
        LauncherPreferences preferences = LauncherPreferences.getSingleton();
        LauncherPreferences.ColorTheme colorTheme = preferences == null
                ? LauncherPreferences.ColorTheme.BLUE_GRAY
                : preferences.getColorTheme();
        activity.getTheme().applyStyle(styleFor(colorTheme), true);
    }

    private static int styleFor(LauncherPreferences.ColorTheme colorTheme) {
        switch (colorTheme) {
            case FOREST:
                return R.style.AppThemePaletteForest;
            case PURPLE:
                return R.style.AppThemePalettePurple;
            case CLASSIC_AMBER:
                return R.style.AppThemePaletteClassicAmber;
            case MONET_AQUA:
                return R.style.AppThemePaletteMonetAqua;
            case MONET_ROSE:
                return R.style.AppThemePaletteMonetRose;
            case MONET_LAVENDER:
                return R.style.AppThemePaletteMonetLavender;
            case MONET_SAGE:
                return R.style.AppThemePaletteMonetSage;
            case IVORY:
                return R.style.AppThemePaletteIvory;
            case PURE_WHITE:
                return R.style.AppThemePalettePureWhite;
            case BLUE_GRAY:
            default:
                return R.style.AppThemePaletteBlueGray;
        }
    }
}

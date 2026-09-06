package com.zomdroid.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.zomdroid.LauncherPreferences;
import com.zomdroid.R;
import com.zomdroid.databinding.FragmentAppSettingsBinding;

/**
 * App-wide settings: appearance and language. Per-instance runtime settings stay behind the
 * instance card's gear button.
 *
 * <p>Renderer, Vulkan driver, JVM arguments, environment variables, render scale, the memory
 * saver, quick-save backup, debug and the on-screen control toggles all belong to a single game
 * instance now and are edited behind each instance card's gear button
 * ({@link SettingsFragment}). The audio API stopped being a setting when OpenSL ES was retired.
 */
public class AppSettingsFragment extends Fragment {
    private FragmentAppSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAppSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LauncherPreferences preferences = LauncherPreferences.requireSingleton();

        String[] colorThemeLabels = {
                getString(R.string.settings_color_theme_blue_gray),
                getString(R.string.settings_color_theme_forest),
                getString(R.string.settings_color_theme_purple),
                getString(R.string.settings_color_theme_classic_amber),
                getString(R.string.settings_color_theme_monet_aqua),
                getString(R.string.settings_color_theme_monet_rose),
                getString(R.string.settings_color_theme_monet_lavender),
                getString(R.string.settings_color_theme_monet_sage),
                getString(R.string.settings_color_theme_ivory),
                getString(R.string.settings_color_theme_pure_white)
        };
        ArrayAdapter<String> colorThemeAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item,
                colorThemeLabels);
        colorThemeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.appSettingsColorThemeS.setAdapter(colorThemeAdapter);
        binding.appSettingsColorThemeS.setSelection(preferences.getColorTheme().ordinal());
        binding.appSettingsColorThemeS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LauncherPreferences.ColorTheme colorTheme =
                        LauncherPreferences.ColorTheme.values()[position];
                if (colorTheme != preferences.getColorTheme()) {
                    preferences.setColorTheme(colorTheme);
                    // Theme overlays are applied before layout inflation, so recreate once after
                    // the preference changes instead of trying to recolour individual views.
                    requireActivity().recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        ArrayAdapter<LauncherPreferences.ThemeMode> themeAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item,
                LauncherPreferences.ThemeMode.values());
        themeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.appSettingsThemeS.setAdapter(themeAdapter);
        binding.appSettingsThemeS.setSelection(themeAdapter.getPosition(preferences.getThemeMode()));
        binding.appSettingsThemeS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LauncherPreferences.ThemeMode mode =
                        (LauncherPreferences.ThemeMode) parent.getSelectedItem();
                preferences.setThemeMode(mode);
                AppCompatDelegate.setDefaultNightMode(mode.nightMode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Language is app-wide as well. Keep it beside the theme so the instance settings page
        // only contains values that affect the selected game instance.
        String[] languageLabels = {
                getString(R.string.settings_language_system),
                getString(R.string.settings_language_english),
                getString(R.string.settings_language_simplified_chinese),
                getString(R.string.settings_language_indonesian),
                getString(R.string.settings_language_portuguese_brazil),
                getString(R.string.settings_language_russian)
        };
        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item,
                languageLabels);
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.appSettingsLanguageS.setAdapter(languageAdapter);
        binding.appSettingsLanguageS.setSelection(preferences.getLanguageMode().ordinal());
        binding.appSettingsLanguageS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LauncherPreferences.LanguageMode mode =
                        LauncherPreferences.LanguageMode.values()[position];
                preferences.setLanguageMode(mode);
                LocaleListCompat locales = mode == LauncherPreferences.LanguageMode.SYSTEM
                        ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(mode.localeTag);
                if (!AppCompatDelegate.getApplicationLocales().equals(locales)) {
                    AppCompatDelegate.setApplicationLocales(locales);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

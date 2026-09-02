package com.zomdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun ZomdroidTheme(content: @Composable () -> Unit) {
    val themeController = remember {
        ThemeController(colorSchemeMode = ColorSchemeMode.System)
    }
    MiuixTheme(controller = themeController, content = content)
}

package com.zomdroid.ui

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

object ZomdroidComposeHost {
    @JvmStatic
    fun attach(view: ComposeView) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            ZomdroidApp()
        }
    }
}

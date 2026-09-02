package com.zomdroid.ui

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.zomdroid.ui.startup.StartupUiAdapter

object ZomdroidComposeHost {
    @JvmStatic
    fun attach(view: ComposeView, startupUiAdapter: StartupUiAdapter) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val owner = remember { RootNavigationEventOwner() }
            DisposableEffect(owner) {
                onDispose { owner.dispatcher.dispose() }
            }
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
            ) {
                ZomdroidApp(startupUiAdapter)
            }
        }
    }
}

private class RootNavigationEventOwner : NavigationEventDispatcherOwner {
    val dispatcher = NavigationEventDispatcher()
    override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
}

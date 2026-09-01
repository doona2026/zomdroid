package com.zomdroid.ui

import com.google.common.truth.Truth.assertThat
import com.zomdroid.ui.model.AppAction
import com.zomdroid.ui.model.AppDestination
import com.zomdroid.ui.model.AppEvent
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppUiReducer
import com.zomdroid.ui.model.AppUiState
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.model.LegacyDestination
import org.junit.Test

class AppUiStateTest {
    @Test fun selectingModuleResetsDetailsToThatModuleHome() {
        var state = AppUiState()
        state = AppUiReducer.reduce(state, AppAction.OpenDestination(AppDestination.WorkshopDetail(42L)))
        state = AppUiReducer.reduce(state, AppAction.NavigateToModule(AppModule.Settings))

        assertThat(state.selectedModule).isEqualTo(AppModule.Settings)
        assertThat(state.backStack).containsExactly(AppDestination.ModuleHome(AppModule.Settings))
    }

    @Test fun detailNavigationAndBackUseOneStackEntryPerDestination() {
        val detail = AppDestination.DownloadTaskDetail("task-1")
        var state = AppUiReducer.reduce(AppUiState(), AppAction.OpenDestination(detail))
        state = AppUiReducer.reduce(state, AppAction.Back)

        assertThat(state.selectedModule).isEqualTo(AppModule.Downloads)
        assertThat(state.backStack).containsExactly(AppDestination.ModuleHome(AppModule.Downloads))
    }

    @Test fun appearanceChangeIsPartOfTheSameStateMachine() {
        val state = AppUiReducer.reduce(AppUiState(), AppAction.SetAppearanceMode(AppearanceMode.Classic))
        assertThat(state.appearanceMode).isEqualTo(AppearanceMode.Classic)
    }

    @Test fun legacyNavigationAndTaskEventsAreOneShotAndConsumable() {
        var state = AppUiReducer.reduce(
            AppUiState(),
            AppAction.OpenLegacyModule(AppModule.Workshop, LegacyDestination.Workshop),
        )
        state = AppUiReducer.reduce(state, AppAction.PublishDownloadTask("download"))
        assertThat(state.events).hasSize(2)
        assertThat(state.events.filterIsInstance<AppEvent.OpenLegacy>()).hasSize(1)

        val firstId = state.events.first().id
        state = AppUiReducer.reduce(state, AppAction.ConsumeEvent(firstId))
        assertThat(state.events).hasSize(1)
    }
}

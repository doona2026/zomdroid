package com.zomdroid.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

class AppNavigationStateTest {
    @Test
    fun nestedRoutesMapToTheirRootDestination() {
        assertThat(AppRoute.InstanceDetail("build-42").rootDestination())
            .isEqualTo(RootDestination.Instances)
        assertThat(AppRoute.WorkshopDownloads.rootDestination())
            .isEqualTo(RootDestination.Workshop)
        assertThat(AppRoute.ToolDetail("box64").rootDestination())
            .isEqualTo(RootDestination.Tools)
        assertThat(AppRoute.SettingsCategory("appearance").rootDestination())
            .isEqualTo(RootDestination.Settings)
    }

    @Test
    fun selectingRootReplacesNestedBackStack() {
        val backStack = navBackStackOf(AppRoute.InstanceDetail("build-42"))

        backStack.selectRoot(RootDestination.Workshop)

        assertThat(backStack).containsExactly(AppRoute.Workshop)
    }

    @Test
    fun selectingCurrentRootWithSingleEntryIsNoOp() {
        val backStack = navBackStackOf(AppRoute.Instances)

        backStack.selectRoot(RootDestination.Instances)

        assertThat(backStack).containsExactly(AppRoute.Instances)
    }
}

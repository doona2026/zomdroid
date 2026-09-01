package com.zomdroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.R
import com.zomdroid.ui.model.AppDestination
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppUiState
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage7UiMatrixTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allPrimaryModulesRenderAtPhoneTabletAndLargeWidthsInEveryAppearance() {
        val widths = listOf(360.dp, 600.dp, 840.dp)
        val moduleLabels = listOf(
            R.string.app_module_launcher,
            R.string.app_module_workshop,
            R.string.app_module_downloads,
            R.string.app_module_mod_library,
            R.string.app_module_settings,
        ).map { composeRule.activity.getString(it) }

        AppearanceMode.entries.forEach { appearance ->
            widths.forEach { width ->
                composeRule.setContent {
                    ZomdroidTheme(appearanceMode = appearance) {
                        Box(Modifier.size(width = width, height = if (width < 600.dp) 640.dp else 420.dp)) {
                                AppScaffold(
                                    state = AppUiState(),
                                    onModuleSelected = {},
                                    onBack = {},
                                    snackbarHostState = SnackbarHostState(),
                                )
                        }
                    }
                }
                if (width < 600.dp) {
                    composeRule.onAllNodesWithTag("primary_navigation_bar").assertCountEquals(1)
                    composeRule.onAllNodesWithTag("primary_navigation_drawer").assertCountEquals(0)
                } else {
                    composeRule.onAllNodesWithTag("primary_navigation_rail").assertCountEquals(1)
                }
                moduleLabels.forEach { label ->
                    composeRule.onNodeWithText(label).assertIsDisplayed()
                }
            }
        }
    }

    @Test
    fun detailDestinationExposesBackNavigationWithoutChangingModuleShell() {
        var backPressed = false
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.LiteLiquidGlass) {
                AppScaffold(
                    state = AppUiState(backStack = listOf(AppDestination.ModuleHome(AppModule.Workshop), AppDestination.WorkshopDetail(42L))),
                    onModuleSelected = {},
                    onBack = { backPressed = true },
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        composeRule.onNodeWithText("Workshop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.app_shell_back)).performClick()
        composeRule.runOnIdle { check(backPressed) }
    }
}

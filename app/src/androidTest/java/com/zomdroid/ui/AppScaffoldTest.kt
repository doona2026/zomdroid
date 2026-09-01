package com.zomdroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppUiState
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppScaffoldTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun narrowLayoutUsesOnlyTheBottomNavigationBar() {
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.LiquidGlass) {
                Box(Modifier.size(500.dp)) {
                    AppScaffold(AppUiState(), {}, {}, SnackbarHostState())
                }
            }
        }
        listOf("Launcher", "Workshop", "Downloads", "Mod library", "Settings").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag("primary_navigation_bar").assertCountEquals(1)
        composeRule.onAllNodesWithTag("primary_navigation_drawer").assertCountEquals(0)
    }

    @Test
    fun wideLayoutUsesOnlyThePersistentSideNavigation() {
        AppearanceMode.entries.forEach { mode ->
            composeRule.setContent {
                ZomdroidTheme(appearanceMode = mode) {
                    Box(Modifier.size(width = 700.dp, height = 420.dp)) {
                        AppScaffold(AppUiState(), {}, {}, SnackbarHostState())
                    }
                }
            }
            composeRule.onNodeWithText("Launcher").assertIsDisplayed()
            composeRule.onAllNodesWithTag("primary_navigation_rail").assertCountEquals(1)
            composeRule.onAllNodesWithTag("primary_navigation_bar").assertCountEquals(0)
        }
    }
}

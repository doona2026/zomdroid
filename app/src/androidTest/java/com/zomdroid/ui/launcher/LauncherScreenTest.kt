package com.zomdroid.ui.launcher

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyLauncherOffersThePrimaryCreateAction() {
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.Classic) {
                LauncherScreen(LauncherUiState(), {})
            }
        }
        composeRule.onNodeWithText("No game instances yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add game instance").assertIsDisplayed()
    }

    @Test
    fun instanceCardRendersAndLaunchActionLeavesBusinessDecisionToCaller() {
        var received: LauncherAction? = null
        val instance = LauncherInstanceUiModel("Main", "42", "Build 42", "/instances/Main", true, true, true, null)
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.Classic) {
                LauncherScreen(LauncherUiState(instances = listOf(instance)), { received = it })
            }
        }
        composeRule.onNodeWithText("Main").assertIsDisplayed()
        composeRule.onNodeWithText("Launch").performClick()
        assertEquals(LauncherAction.Launch("Main"), received)
    }

    @Test
    fun multipleInstancesAreAllRendered() {
        val instances = listOf(
            LauncherInstanceUiModel("One", "41", "Build 41", "/instances/One", true, true, true, null),
            LauncherInstanceUiModel("Two", "42", "Build 42", "/instances/Two", true, true, true, null),
        )
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.Classic) {
                LauncherScreen(LauncherUiState(instances = instances), {})
            }
        }
        composeRule.onNodeWithText("One").assertIsDisplayed()
        composeRule.onNodeWithText("Two").assertIsDisplayed()
    }
}

package com.zomdroid.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.R
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun allThreeAppearanceModesAreSelectable() {
        var selected = AppearanceMode.LiquidGlass
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = selected) {
                MaterialTheme { AppearanceModePicker(selected) { selected = it } }
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.frontend_mode_liquid)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.frontend_mode_lite)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.frontend_mode_classic)).performClick()
        composeRule.runOnIdle { assertEquals(AppearanceMode.Classic, selected) }
    }
}

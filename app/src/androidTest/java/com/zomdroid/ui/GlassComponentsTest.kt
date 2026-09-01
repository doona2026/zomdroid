package com.zomdroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.ui.component.ZomdroidGlassSurface
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassComponentsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allAppearanceModesRenderSurfaceAndKeepInteraction() {
        AppearanceMode.entries.forEach { mode ->
            var clicks = 0
            composeRule.setContent {
                ZomdroidTheme(appearanceMode = mode) {
                    Box(Modifier.fillMaxSize()) {
                        ZomdroidGlassSurface(Modifier.testTag("surface")) {
                            androidx.compose.material3.Button(onClick = { clicks++ }) { Text("Action") }
                        }
                    }
                }
            }
            composeRule.onNodeWithTag("surface").assertIsDisplayed()
            composeRule.onNodeWithText("Action").performClick()
            assertEquals(1, clicks)
        }
    }
}


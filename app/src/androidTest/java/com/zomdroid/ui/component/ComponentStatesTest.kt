package com.zomdroid.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.ui.theme.ZomdroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentStatesTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun emptyStateIsRendered() {
        composeRule.setContent { ZomdroidTheme { ZomdroidEmptyState("Nothing here") } }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    @Test fun errorRetryIsRendered() {
        composeRule.setContent { ZomdroidTheme { ZomdroidErrorState("Network failed", onRetry = {}) } }
        composeRule.onNodeWithText("Network failed").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test fun confirmDialogExposesBothActions() {
        composeRule.setContent {
            ZomdroidTheme { ZomdroidConfirmDialog("Confirmation", "Proceed?", {}, {}) }
        }
        composeRule.onNodeWithText("Confirmation").assertIsDisplayed()
        composeRule.onNodeWithText("Proceed?").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }
}

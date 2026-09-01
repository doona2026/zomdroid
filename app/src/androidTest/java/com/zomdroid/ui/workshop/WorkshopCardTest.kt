package com.zomdroid.ui.workshop

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.ZomdroidTheme
import com.zomdroid.workshop.data.WorkshopBrowseItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkshopCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun browseCardRendersWorkshopIdentity() {
        val item = WorkshopBrowseItem(108600u, 123456u, "Riverside Radio", "Author", "", "Survival radio")
        composeRule.setContent {
            ZomdroidTheme(appearanceMode = AppearanceMode.Classic) { WorkshopCard(item) {} }
        }
        composeRule.onNodeWithText("Riverside Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Author: Author").assertIsDisplayed()
        composeRule.onNodeWithText("Survival radio").assertIsDisplayed()
    }
}

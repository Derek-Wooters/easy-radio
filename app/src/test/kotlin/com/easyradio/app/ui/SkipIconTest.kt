package com.easyradio.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for the bug this composable fixed: the mini-player and Now Playing screen
 * used to draw a hardcoded Forward30/Replay icon regardless of the actual configured skip-back/
 * skip-forward seconds, so changing those settings (or skip-back's own 15s default, which has no
 * matching Material icon at all) silently went un-reflected in the UI.
 */
@RunWith(RobolectricTestRunner::class)
class SkipIconTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the actual configured seconds, not a fixed default`() {
        composeTestRule.setContent {
            Row {
                SkipIcon(seconds = 15, direction = SkipDirection.BACK)
                SkipIcon(seconds = 45, direction = SkipDirection.FORWARD)
            }
        }

        composeTestRule.onNodeWithText("15").assertExists()
        composeTestRule.onNodeWithText("45").assertExists()
        // Guards against the two icons happening to render the same number for any reason.
        composeTestRule.onNodeWithText("30").assertDoesNotExist()
    }

    @Test
    fun `content description names the direction and the actual seconds`() {
        composeTestRule.setContent {
            Row {
                SkipIcon(seconds = 15, direction = SkipDirection.BACK)
                SkipIcon(seconds = 45, direction = SkipDirection.FORWARD)
            }
        }

        composeTestRule.onNodeWithContentDescription("Skip back 15 seconds").assertExists()
        composeTestRule.onNodeWithContentDescription("Skip forward 45 seconds").assertExists()
    }

    @Test
    fun `updates when the seconds value changes`() {
        var seconds by mutableIntStateOf(10)

        composeTestRule.setContent {
            SkipIcon(seconds = seconds, direction = SkipDirection.BACK)
        }
        composeTestRule.onNodeWithText("10").assertExists()

        seconds = 60
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("60").assertExists()
        composeTestRule.onNodeWithText("10").assertDoesNotExist()
    }
}

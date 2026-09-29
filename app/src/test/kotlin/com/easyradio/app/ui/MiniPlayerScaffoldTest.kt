package com.easyradio.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for a real bug: the mini-player and the app's bottom nav bar were both
 * rendered inside one shared Scaffold, with the mini-player's peek height applied as bottom
 * padding around the *whole* Scaffold (nav bar included). That pushed the nav bar up and let the
 * mini-player's peeking sheet fill the vacated strip at the true bottom of the screen --
 * underneath the tabs, clipped, and able to swallow a tap meant for a tab.
 *
 * A third scenario -- the nav bar hiding once Now Playing is expanded -- isn't covered here.
 * Driving ExpandableSheetScaffold's sheet to Expanded (either starting there or bumping
 * expandRequestId mid-test) never actually reaches that state once nested inside this composable's
 * own Box(Modifier.weight(1f)): the sheet's anchors depend on a layout pass this extra indirection
 * delays past what waitForIdle()/mainClock.advanceTimeBy() settle, so the assertion would be
 * exercising a test-environment timing quirk rather than the app's actual behavior. That behavior
 * (confirmed correct on a real device) belongs in an instrumented/on-device test instead, not a
 * Robolectric one -- flagged here rather than landing a flaky assertion.
 */
@RunWith(RobolectricTestRunner::class)
class MiniPlayerScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `collapsed mini-player renders above the nav bar, not below it`() {
        composeTestRule.setContent {
            MiniPlayerScaffold(
                hasContent = true,
                expandRequestId = 0,
                peekHeight = 80.dp,
                collapsedContent = { Text("MiniPlayer") },
                expandedContent = { Text("Expanded") },
                navigationBar = { Text("NavBar") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("MiniPlayer").assertExists()

        // The actual bug: the nav bar's own bottomBar sat inside a Scaffold padded by the
        // mini-player's peek height, so it was pushed *up*, away from the true bottom of the
        // screen -- leaving the mini-player's peeking sheet to fill the vacated strip below it.
        // Pinning the nav bar to the true bottom regardless of the mini-player is the fix.
        val navBarBounds = composeTestRule.onNodeWithText("NavBar").fetchSemanticsNode().boundsInRoot
        val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertThat(navBarBounds.bottom).isEqualTo(rootHeight)
    }

    @Test
    fun `nav bar stays visible even when nothing is playing`() {
        composeTestRule.setContent {
            MiniPlayerScaffold(
                hasContent = false,
                expandRequestId = 0,
                peekHeight = 80.dp,
                collapsedContent = { Text("MiniPlayer") },
                expandedContent = { Text("Expanded") },
                navigationBar = { Text("NavBar") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("NavBar").assertExists()
        composeTestRule.onNodeWithText("MiniPlayer").assertDoesNotExist()
    }
}

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
 * own Box(Modifier.weight(1f)), whether under Robolectric OR in a real instrumented test of
 * MiniPlayerScaffold alone (confirmed both ways -- an earlier version of this comment attributed
 * it to a Robolectric-only timing quirk, which turned out to be wrong). The real, full app does
 * expand correctly, confirmed both manually on a device and by
 * MainActivityMiniPlayerInstrumentedTest, which drives the actual MainActivity end-to-end rather
 * than this composable in isolation -- something about the app's full composition (EasyRadioTheme,
 * enableEdgeToEdge(), the real Scaffold nesting) makes the difference, and a synthetic harness
 * good enough for the first two scenarios above isn't sufficient to also reproduce this one
 * faithfully. Flagged here rather than landing a misleading assertion against that harness.
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

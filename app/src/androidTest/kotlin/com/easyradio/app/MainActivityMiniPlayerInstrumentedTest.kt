package com.easyradio.app

import android.Manifest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real, end-to-end coverage for the regression MiniPlayerScaffold was extracted to fix (nav bar
 * hiding once Now Playing is expanded), run against the actual MainActivity rather than an
 * isolated MiniPlayerScaffold harness.
 *
 * A standalone instrumented test of MiniPlayerScaffold alone (bare ComponentActivity, no theme,
 * no edge-to-edge) was tried first and found that ExpandableSheetScaffold's sheet never reaches
 * Expanded when nested inside MiniPlayerScaffold's own Box(Modifier.weight(1f)) -- confirmed via
 * a semantics-tree dump showing no "Expanded" node ever appears, even from a real tap, even
 * though the exact same ExpandableSheetScaffold reaches Expanded fine when composed directly
 * without that wrapper. That contradicts many manual on-device confirmations earlier this session
 * that the real app's mini-player does expand correctly. Rather than trust a synthetic harness
 * that may not faithfully reproduce the real app's window/theme setup (EasyRadioTheme,
 * enableEdgeToEdge(), the real Scaffold nesting), this test drives the real MainActivity instead
 * -- higher fidelity, and it settles the question directly rather than debugging a harness
 * discrepancy.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityMiniPlayerInstrumentedTest {

    // Must run (and grant) before the compose rule launches MainActivity: without it,
    // requestNotificationPermissionIfNeeded()'s runtime permission dialog pops up mid-test
    // (API 33+), stealing focus from the activity and breaking the Compose test's semantics tree
    // lookups outright ("No compose hierarchies found in the app") -- confirmed by hitting exactly
    // that failure before adding this rule.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun playingAStationExpandsNowPlayingAndHidesTheNavBar() {
        // The system splash screen, the onboarding-vs-main-UI decision (an async DataStore read
        // in MainActivity), and whichever one a fresh app install actually lands on all delay --
        // or replace -- the nav bar's first appearance past what Compose's own idling sync
        // accounts for, so wait for either explicitly rather than assuming the nav bar is there.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Curated stations (Radio tab's default, no-query view) are a fixed local list -- no
        // network call, so this doesn't depend on network access or real station availability.
        composeTestRule.onNodeWithText("Radio").performClick()

        composeTestRule.onAllNodes(hasContentDescription("Play", substring = true))[0].performClick()

        // expandRequestId++ (inside MainActivity.playStation()) should drive the sheet to
        // Expanded, which hides the nav bar -- this is the actual regression being verified.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("LIVE").assertExists()
    }

    @Test
    fun recreatingTheActivityWhileAStationPlaysStillShowsTheMiniPlayer() {
        // Regression test for the actual bug report this fix was built for: MainActivity being
        // recreated (e.g. backing out of the app, or the process being reclaimed) while a
        // station keeps playing independently in the background service previously left the
        // mini-player permanently missing. Two separate root causes combined to cause this,
        // both fixed: the service never published a station media id for MainActivity to resync
        // from, and -- even once it did -- ExpandableSheetScaffold's sheet state starts Hidden on
        // every new Activity instance, and nothing ever told it to leave Hidden outside of the
        // expandRequestId/playStation() path, which a resync (as opposed to a direct user tap)
        // doesn't go through.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip").performClick()
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isNotEmpty()
            }
        }

        composeTestRule.onNodeWithText("Radio").performClick()
        composeTestRule.onAllNodes(hasContentDescription("Play", substring = true))[0].performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("LIVE").fetchSemanticsNodes().isNotEmpty()
        }
        // The Recently Played write playStation() kicks off is what the resync below reads back
        // to reconstruct the playing station -- give it a moment to land before recreating.
        Thread.sleep(500)

        composeTestRule.activityRule.scenario.recreate()

        // The new Activity instance starts with no nav bar/mini-player shown at all while its
        // onboarding-vs-main-UI DataStore read and session resync are in flight -- assert they
        // both end up showing together, which only happens once currentStation has been resynced
        // AND the sheet has actually left Hidden to show it.
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("LIVE").fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText("Radio").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

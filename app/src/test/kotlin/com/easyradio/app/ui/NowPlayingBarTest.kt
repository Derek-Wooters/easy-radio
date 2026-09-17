package com.easyradio.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.media.PlaybackUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NowPlayingBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping the bar outside any button fires onExpand`() {
        var expanded = false

        composeTestRule.setContent {
            NowPlayingBar(
                title = "KFAN FM 100.3",
                tagline = "Audio Home For Minnesota Sports",
                tintSeed = "kfan",
                imageUrl = null,
                badgeText = "LIVE",
                playbackState = PlaybackUiState.PLAYING,
                onPlayClick = {},
                onPauseClick = {},
                onExpand = { expanded = true },
            )
        }

        // The title text isn't a button -- tapping it exercises the whole-bar tap target
        // (see NowPlayingBar's Surface-level clickable), not a dedicated click handler.
        composeTestRule.onNodeWithText("KFAN FM 100.3").performClick()
        composeTestRule.waitForIdle()

        assert(expanded) { "Expected tapping the bar to fire onExpand" }
    }

    @Test
    fun `tapping the pause button fires onPauseClick and does not fire onExpand`() {
        var expanded = false
        var paused = false

        composeTestRule.setContent {
            NowPlayingBar(
                title = "KFAN FM 100.3",
                tagline = "Audio Home For Minnesota Sports",
                tintSeed = "kfan",
                imageUrl = null,
                badgeText = "LIVE",
                playbackState = PlaybackUiState.PLAYING,
                onPlayClick = {},
                onPauseClick = { paused = true },
                onExpand = { expanded = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        composeTestRule.waitForIdle()

        assert(paused) { "Expected tapping Pause to fire onPauseClick" }
        assert(!expanded) { "Expected tapping Pause to NOT also fire onExpand" }
    }

    @Test
    fun `tapping skip back and skip forward fire their own callbacks, not onExpand`() {
        var expanded = false
        var skippedBack = false
        var skippedForward = false

        composeTestRule.setContent {
            NowPlayingBar(
                title = "Episode One",
                tagline = "Planet Money",
                tintSeed = "p1",
                imageUrl = null,
                badgeText = null,
                playbackState = PlaybackUiState.PLAYING,
                onPlayClick = {},
                onPauseClick = {},
                onSkipBackClick = { skippedBack = true },
                onSkipForwardClick = { skippedForward = true },
                skipBackSeconds = 15,
                skipForwardSeconds = 30,
                onExpand = { expanded = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Skip back 15 seconds").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Skip forward 30 seconds").performClick()
        composeTestRule.waitForIdle()

        assert(skippedBack) { "Expected tapping skip-back to fire onSkipBackClick" }
        assert(skippedForward) { "Expected tapping skip-forward to fire onSkipForwardClick" }
        assert(!expanded) { "Expected tapping skip buttons to NOT also fire onExpand" }
    }
}

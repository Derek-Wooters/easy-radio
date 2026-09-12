package com.easyradio.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NowPlayingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `radio header shows queue icon and no sleep timer icon when only onQueueClick is set`() {
        var queueClicked = false

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Live Radio",
                title = "KFAN FM 100.3",
                subtitle = "Sports",
                imageUrl = null,
                tintSeed = "kfan",
                isLive = true,
                isPlaying = true,
                progress = null,
                positionLabel = null,
                durationLabel = null,
                speedLabel = null,
                onCollapse = {},
                onPlayPause = {},
                onQueueClick = { queueClicked = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Sleep timer").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Up Next").performClick()
        composeTestRule.waitForIdle()

        assert(queueClicked) { "Expected the header queue icon click to fire onQueueClick" }
    }

    @Test
    fun `podcast header sleep timer icon fires onSleepTimerClick`() {
        var sleepTimerClicked = false

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Planet Money",
                title = "Episode One",
                subtitle = "Planet Money",
                imageUrl = null,
                tintSeed = "p1",
                isLive = false,
                isPlaying = true,
                progress = 0.5f,
                positionLabel = "10:00",
                durationLabel = "20:00",
                speedLabel = "1.0x",
                onCollapse = {},
                onPlayPause = {},
                onSleepTimerClick = { sleepTimerClicked = true },
                onQueueClick = null,
            )
        }

        composeTestRule.onNodeWithContentDescription("Up Next").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Sleep timer").performClick()
        composeTestRule.waitForIdle()
        assert(sleepTimerClicked) { "Expected the header sleep-timer icon click to fire onSleepTimerClick" }
    }

    @Test
    fun `podcast control row queue icon fires onQueueClick`() {
        var queueClicked = false

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Planet Money",
                title = "Episode One",
                subtitle = "Planet Money",
                imageUrl = null,
                tintSeed = "p1",
                isLive = false,
                isPlaying = true,
                progress = 0.5f,
                positionLabel = "10:00",
                durationLabel = "20:00",
                speedLabel = "1.0x",
                onCollapse = {},
                onPlayPause = {},
                onSleepTimerClick = {},
                onQueueClick = { queueClicked = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Up Next").performClick()
        composeTestRule.waitForIdle()
        assert(queueClicked) { "Expected the control-row queue icon click to fire onQueueClick" }
    }

    @Test
    fun `favorite star reflects isFavorite and clicking it fires onFavoriteClick`() {
        var favoriteClicked = false

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Live Radio",
                title = "KFAN FM 100.3",
                subtitle = "Sports",
                imageUrl = null,
                tintSeed = "kfan",
                isLive = true,
                isPlaying = true,
                progress = null,
                positionLabel = null,
                durationLabel = null,
                speedLabel = null,
                onCollapse = {},
                onPlayPause = {},
                isFavorite = false,
                onFavoriteClick = { favoriteClicked = true },
            )
        }

        composeTestRule.onNodeWithContentDescription("Add to favorites").assertExists()
        composeTestRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeTestRule.waitForIdle()

        assert(favoriteClicked) { "Expected the favorite star click to fire onFavoriteClick" }
    }

    @Test
    fun `collapse chevron fires onCollapse`() {
        var collapsed = false

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Live Radio",
                title = "KFAN FM 100.3",
                subtitle = "Sports",
                imageUrl = null,
                tintSeed = "kfan",
                isLive = true,
                isPlaying = true,
                progress = null,
                positionLabel = null,
                durationLabel = null,
                speedLabel = null,
                onCollapse = { collapsed = true },
                onPlayPause = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Collapse").performClick()
        composeTestRule.waitForIdle()

        assert(collapsed) { "Expected the collapse chevron click to fire onCollapse" }
    }

    @Test
    fun `dragging the podcast progress slider fires onSeek with the released fraction`() {
        var seekedTo: Float? = null

        composeTestRule.setContent {
            NowPlayingScreen(
                topLabel = "Planet Money",
                title = "Episode One",
                subtitle = "Planet Money",
                imageUrl = null,
                tintSeed = "p1",
                isLive = false,
                isPlaying = true,
                progress = 0.5f,
                positionLabel = "10:00",
                durationLabel = "20:00",
                durationMs = 1_200_000L,
                onSeek = { seekedTo = it },
                speedLabel = "1.0x",
                onCollapse = {},
                onPlayPause = {},
            )
        }

        composeTestRule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        composeTestRule.waitForIdle()

        assert(seekedTo == 0.75f) { "Expected onSeek to fire with the released slider fraction, was $seekedTo" }
    }
}

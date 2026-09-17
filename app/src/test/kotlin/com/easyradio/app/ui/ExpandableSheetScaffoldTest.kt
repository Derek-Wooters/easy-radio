package com.easyradio.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpandableSheetScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `nothing is shown when hasContent is false`() {
        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = false,
                expandRequestId = 0,
                peekHeight = 80.dp,
                collapsedContent = { Text("Collapsed") },
                expandedContent = { Text("Expanded") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("Collapsed").assertDoesNotExist()
        composeTestRule.onNodeWithText("Expanded").assertDoesNotExist()
        composeTestRule.onNodeWithText("TabContent").assertExists()
    }

    @Test
    fun `collapsed content shows by default once hasContent is true`() {
        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = true,
                expandRequestId = 0,
                peekHeight = 80.dp,
                collapsedContent = { Text("Collapsed") },
                expandedContent = { Text("Expanded") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("Collapsed").assertExists()
        composeTestRule.onNodeWithText("Expanded").assertDoesNotExist()
    }

    @Test
    fun `bumping expandRequestId switches from collapsed to expanded`() {
        var expandRequestId by mutableStateOf(0)

        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = true,
                expandRequestId = expandRequestId,
                peekHeight = 80.dp,
                collapsedContent = { Text("Collapsed") },
                expandedContent = { Text("Expanded") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("Collapsed").assertExists()

        expandRequestId++
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Expanded").assertExists()
        composeTestRule.onNodeWithText("Collapsed").assertDoesNotExist()
    }

    @Test
    fun `invoking the collapsed content's onExpand callback switches to expanded`() {
        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = true,
                expandRequestId = 0,
                peekHeight = 80.dp,
                collapsedContent = { onExpand -> Button(onClick = onExpand) { Text("Expand") } },
                expandedContent = { Text("Expanded") },
            ) {
                Text("TabContent")
            }
        }

        composeTestRule.onNodeWithText("Expand").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Expanded").assertExists()
    }

    @Test
    fun `invoking the expanded content's onCollapse callback switches back to collapsed`() {
        var expandRequestId by mutableStateOf(0)

        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = true,
                expandRequestId = expandRequestId,
                peekHeight = 80.dp,
                collapsedContent = { Text("Collapsed") },
                expandedContent = { onCollapse -> Button(onClick = onCollapse) { Text("Collapse") } },
            ) {
                Text("TabContent")
            }
        }

        expandRequestId++
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Collapse").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Collapsed").assertExists()
    }

    @Test
    fun `content is never disposed while the sheet transitions between states`() {
        // Regression test for the original bug this component fixed: a full top-level
        // screen swap disposed the tab content (losing its own navigation/scroll state)
        // every time Now Playing was shown. This asserts the tab content composable is
        // mounted exactly once, never torn down and recreated, across a full
        // collapsed -> expanded -> collapsed cycle.
        var expandRequestId by mutableStateOf(0)
        var mountCount = 0

        composeTestRule.setContent {
            ExpandableSheetScaffold(
                hasContent = true,
                expandRequestId = expandRequestId,
                peekHeight = 80.dp,
                collapsedContent = { Text("Collapsed") },
                expandedContent = { onCollapse -> Button(onClick = onCollapse) { Text("Collapse") } },
            ) {
                LaunchedEffect(Unit) { mountCount++ }
                Text("TabContent")
            }
        }

        composeTestRule.waitForIdle()
        assert(mountCount == 1) { "Expected tab content to mount once, mounted $mountCount times before any sheet interaction" }

        expandRequestId++
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Collapse").performClick()
        composeTestRule.waitForIdle()

        assert(mountCount == 1) { "Expected tab content to stay mounted across sheet transitions, mounted $mountCount times" }
    }
}

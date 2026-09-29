package com.easyradio.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Wraps [ExpandableSheetScaffold] so the mini-player sits above [navigationBar] when collapsed,
 * and hides [navigationBar] entirely while Now Playing is expanded (so the full player still
 * covers the whole screen, tabs included).
 *
 * Extracted after a real regression: the mini-player and the app's bottom nav bar were both
 * rendered inside the same inner Scaffold, with the mini-player's peek height applied as bottom
 * padding around the *whole* Scaffold (nav bar included). That pushed the nav bar up and let the
 * mini-player's peeking sheet fill the vacated strip at the true bottom of the screen --
 * underneath the tabs, clipped, and able to swallow a tap meant for a tab. See
 * MiniPlayerScaffoldTest for the regression coverage this fix didn't originally have.
 */
@Composable
fun MiniPlayerScaffold(
    hasContent: Boolean,
    expandRequestId: Int,
    peekHeight: Dp,
    collapsedContent: @Composable ColumnScope.(onExpand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    navigationBar: @Composable () -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var sheetExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            ExpandableSheetScaffold(
                hasContent = hasContent,
                expandRequestId = expandRequestId,
                peekHeight = peekHeight,
                onExpandedChange = {
                    sheetExpanded = it
                    onExpandedChange(it)
                },
                collapsedContent = collapsedContent,
                expandedContent = expandedContent,
                content = content,
            )
        }
        if (!sheetExpanded) {
            navigationBar()
        }
    }
}

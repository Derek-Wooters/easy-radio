package com.easyradio.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Wraps [ExpandableSheetScaffold] so the mini-player sits above [navigationBar] when collapsed,
 * and [navigationBar] stays visible and tappable above the expanded Now Playing content too --
 * the sheet's expanded content is confined to the space above the nav bar rather than covering
 * the whole screen, so tabs remain reachable without backing out of Now Playing first. A tab tap
 * while expanded is expected to bump [collapseRequestId] (see [ExpandableSheetScaffold]) so the
 * sheet collapses back down to the mini-player as the new tab becomes visible.
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
    collapseRequestId: Int = 0,
    visibilityRefreshToken: Int = 0,
    peekHeight: Dp,
    collapsedContent: @Composable ColumnScope.(onExpand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    navigationBar: @Composable () -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            ExpandableSheetScaffold(
                hasContent = hasContent,
                expandRequestId = expandRequestId,
                collapseRequestId = collapseRequestId,
                visibilityRefreshToken = visibilityRefreshToken,
                peekHeight = peekHeight,
                onExpandedChange = onExpandedChange,
                collapsedContent = collapsedContent,
                expandedContent = expandedContent,
                content = content,
            )
        }
        navigationBar()
    }
}

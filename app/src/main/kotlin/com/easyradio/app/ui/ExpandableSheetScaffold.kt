package com.easyradio.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/**
 * A bottom sheet that stays permanently mounted over [content] rather than replacing it, so
 * [content]'s own state (scroll position, internal navigation, etc.) is never disposed by
 * expanding or collapsing -- unlike a full top-level screen swap would be. Collapsed shows
 * [collapsedContent] at [peekHeight]; expanded shows [expandedContent] full-screen. Nothing is
 * shown (and the sheet isn't draggable) while [hasContent] is false.
 *
 * Bump [expandRequestId] (e.g. a counter incremented from outside composition) to request the
 * sheet expand, since callers that aren't themselves composables can't call the underlying
 * suspend `SheetState.expand()` directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableSheetScaffold(
    hasContent: Boolean,
    expandRequestId: Int,
    peekHeight: Dp,
    collapsedContent: @Composable ColumnScope.(onExpand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    // Starts Hidden (nothing has played yet) rather than PartiallyExpanded so peekHeight can
    // stay constant -- toggling it between 0.dp and a real height left the PartiallyExpanded
    // anchor stale at its old (zero) offset after the first expand.
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()

    LaunchedEffect(expandRequestId) {
        if (expandRequestId > 0) sheetState.expand()
    }

    BackHandler(enabled = sheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.partialExpand() }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetSwipeEnabled = hasContent,
        // The collapsed content is expected to be fully tappable to expand itself, so the
        // default drag handle -- which visually implies dragging is required -- is removed.
        // Dragging the sheet still works without it.
        sheetDragHandle = null,
        sheetContent = {
            if (hasContent) {
                if (sheetState.targetValue == SheetValue.Expanded) {
                    expandedContent { scope.launch { sheetState.partialExpand() } }
                } else {
                    collapsedContent { scope.launch { sheetState.expand() } }
                }
            }
        },
    ) {
        content()
    }
}

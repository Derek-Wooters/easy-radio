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
 *
 * [onExpandedChange] fires whenever the sheet switches between showing [collapsedContent] and
 * [expandedContent] (mirroring that same switch, not the drag animation), so a caller that needs
 * to hide other bottom-anchored UI (e.g. a nav bar) while the sheet is full-screen can do so in
 * lockstep rather than guessing at animation timing.
 *
 * Bump [visibilityRefreshToken] (e.g. from onStart(), every time the app regains focus) to give
 * the sheet another explicit chance to leave Hidden if [hasContent] is already true. The
 * hasContent-keyed effect below only fires on an actual false-to-true transition; if the app
 * gained focus for reasons other than that transition (e.g. the whole Activity's lifecycle cycled
 * while hasContent had already been true the entire time), that effect never re-runs, and reports
 * from the field show the sheet can end up stuck invisible despite hasContent/collapsedContent
 * both being correct. This token is the fix: it treats "the app just gained focus" as its own
 * reason to re-verify the sheet matches reality, independent of whether hasContent itself changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableSheetScaffold(
    hasContent: Boolean,
    expandRequestId: Int,
    visibilityRefreshToken: Int = 0,
    peekHeight: Dp,
    collapsedContent: @Composable ColumnScope.(onExpand: () -> Unit) -> Unit,
    expandedContent: @Composable (onCollapse: () -> Unit) -> Unit,
    onExpandedChange: (Boolean) -> Unit = {},
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

    // hasContent flipping true is the only other way the sheet should ever leave Hidden --
    // besides an explicit expandRequestId bump, which jumps straight to full-screen. Without
    // this, a caller that adopts now-playing state without going through expandRequestId (e.g.
    // MainActivity resyncing what's already playing in the background after being recreated)
    // left the sheet stuck at its initial Hidden value forever: hasContent correctly became
    // true, sheetContent correctly started emitting collapsedContent, but the sheet itself was
    // never told to actually become visible, so the peek area stayed a blank, untappable strip
    // despite the state being otherwise fully correct. Only promote out of Hidden, never out of
    // Expanded/PartiallyExpanded, so this can't fight a drag or an explicit expand in progress.
    LaunchedEffect(hasContent) {
        if (hasContent && sheetState.currentValue == SheetValue.Hidden) {
            sheetState.partialExpand()
        }
    }

    LaunchedEffect(visibilityRefreshToken) {
        if (hasContent && sheetState.currentValue == SheetValue.Hidden) {
            sheetState.partialExpand()
        }
    }

    LaunchedEffect(sheetState.targetValue) {
        onExpandedChange(sheetState.targetValue == SheetValue.Expanded)
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

package com.callagent.host.ui

/** A small, Android-free description of a reported separating fold. */
data class FoldGeometry(
    val separating: Boolean,
    val axis: Axis,
    val startPx: Int,
    val endPx: Int,
) {
    enum class Axis { VERTICAL, HORIZONTAL }
}

data class AdaptiveLayoutDecision(
    val mode: Mode,
    val contentMaxWidthDp: Int,
    val hingeGapDp: Int,
    val leadingWeight: Float,
    val trailingWeight: Float,
) {
    enum class Mode { SINGLE_COLUMN, TWO_COLUMNS, HORIZONTAL_FOLD }
}

data class HorizontalPaneSelection(
    val topHeightPx: Int,
    val bottomHeightPx: Int,
    val selectedPane: Pane,
) {
    enum class Pane { TOP, BOTTOM }

    val selectedHeightPx: Int
        get() = if (selectedPane == Pane.TOP) topHeightPx else bottomHeightPx
}

/**
 * Chooses a layout from the current app window, safe insets, and an optional WindowManager fold.
 * Width thresholds use dp window classes; no device model or display resolution is assumed.
 */
object AdaptiveWindowLayoutPolicy {
    private const val TWO_COLUMN_MIN_WIDTH_DP = 720f
    private const val SINGLE_CONTENT_MAX_WIDTH_DP = 760
    private const val WIDE_CONTENT_MAX_WIDTH_DP = 1200
    private const val STANDARD_COLUMN_GAP_DP = 24
    private const val FOLD_CLEARANCE_DP = 24

    /**
     * Finds the usable physical panes around a horizontal separating hinge and selects the
     * larger one. Safe insets include the IME, so a keyboard-covered lower pane has zero height
     * and cannot accidentally become the fallback viewport.
     */
    fun largestSafeHorizontalPane(
        windowHeightPx: Int,
        safeTopPx: Int,
        safeBottomPx: Int,
        fold: FoldGeometry,
        topClearancePx: Int,
        bottomClearancePx: Int,
    ): HorizontalPaneSelection {
        require(fold.separating && fold.axis == FoldGeometry.Axis.HORIZONTAL)
        val topHeight = (fold.startPx - safeTopPx - topClearancePx.coerceAtLeast(0)).coerceAtLeast(0)
        val bottomHeight = (windowHeightPx - safeBottomPx - fold.endPx - bottomClearancePx.coerceAtLeast(0)).coerceAtLeast(0)
        return HorizontalPaneSelection(
            topHeightPx = topHeight,
            bottomHeightPx = bottomHeight,
            selectedPane = if (topHeight >= bottomHeight) {
                HorizontalPaneSelection.Pane.TOP
            } else {
                HorizontalPaneSelection.Pane.BOTTOM
            },
        )
    }

    fun calculate(
        windowWidthPx: Int,
        windowHeightPx: Int,
        density: Float,
        safeLeftPx: Int = 0,
        safeTopPx: Int = 0,
        safeRightPx: Int = 0,
        safeBottomPx: Int = 0,
        contentSidePaddingDp: Int = 20,
        fold: FoldGeometry? = null,
    ): AdaptiveLayoutDecision {
        val scale = density.takeIf { it > 0f } ?: 1f
        val usableWidth = (windowWidthPx - safeLeftPx - safeRightPx).coerceAtLeast(0)
        val usableWidthDp = usableWidth / scale
        if (fold?.separating == true && fold.axis == FoldGeometry.Axis.HORIZONTAL) {
            val leading = (fold.startPx - safeTopPx).coerceAtLeast(0)
            val trailing = (windowHeightPx - safeBottomPx - fold.endPx).coerceAtLeast(0)
            val physicalGap = ((fold.endPx - fold.startPx).coerceAtLeast(0) / scale).toInt()
            return AdaptiveLayoutDecision(
                mode = AdaptiveLayoutDecision.Mode.HORIZONTAL_FOLD,
                contentMaxWidthDp = WIDE_CONTENT_MAX_WIDTH_DP,
                hingeGapDp = maxOf(STANDARD_COLUMN_GAP_DP, physicalGap + FOLD_CLEARANCE_DP),
                leadingWeight = leading.toFloat().coerceAtLeast(1f),
                trailingWeight = trailing.toFloat().coerceAtLeast(1f),
            )
        }

        val verticalFold = fold?.takeIf { it.separating && it.axis == FoldGeometry.Axis.VERTICAL }
        if (verticalFold != null) {
            val physicalGapPx = (verticalFold.endPx - verticalFold.startPx).coerceAtLeast(0)
            val physicalGapDp = (physicalGapPx / scale).toInt()
            val physicalGap = physicalGapDp + FOLD_CLEARANCE_DP
            val clearancePx = (physicalGap * scale).toInt() - physicalGapPx
            val leadingClearancePx = clearancePx / 2
            val trailingClearancePx = clearancePx - leadingClearancePx
            val panePaddingPx = (contentSidePaddingDp * scale).toInt()
            val leading = (
                verticalFold.startPx - safeLeftPx - panePaddingPx - leadingClearancePx
            ).coerceAtLeast(1).toFloat()
            val trailing = (
                windowWidthPx - safeRightPx - verticalFold.endPx - panePaddingPx - trailingClearancePx
            ).coerceAtLeast(1).toFloat()
            return AdaptiveLayoutDecision(
                mode = AdaptiveLayoutDecision.Mode.TWO_COLUMNS,
                // Fill the safe window so the centered pane gap remains aligned with an off-center fold.
                contentMaxWidthDp = usableWidthDp.toInt(),
                hingeGapDp = maxOf(STANDARD_COLUMN_GAP_DP, physicalGap),
                leadingWeight = leading,
                trailingWeight = trailing,
            )
        }

        if (usableWidthDp >= TWO_COLUMN_MIN_WIDTH_DP) {
            return AdaptiveLayoutDecision(
                mode = AdaptiveLayoutDecision.Mode.TWO_COLUMNS,
                contentMaxWidthDp = WIDE_CONTENT_MAX_WIDTH_DP,
                hingeGapDp = STANDARD_COLUMN_GAP_DP,
                leadingWeight = 1f,
                trailingWeight = 1f,
            )
        }

        return AdaptiveLayoutDecision(
            mode = AdaptiveLayoutDecision.Mode.SINGLE_COLUMN,
            contentMaxWidthDp = SINGLE_CONTENT_MAX_WIDTH_DP,
            hingeGapDp = 0,
            leadingWeight = 1f,
            trailingWeight = 1f,
        )
    }
}

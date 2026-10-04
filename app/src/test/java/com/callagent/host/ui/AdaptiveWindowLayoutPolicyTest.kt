package com.callagent.host.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWindowLayoutPolicyTest {
    @Test
    fun narrowCoverUsesOneColumnAndAccountsForSystemInsets() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 1_260,
            windowHeightPx = 2_400,
            density = 3f,
            safeLeftPx = 30,
            safeRightPx = 30,
            safeTopPx = 90,
            safeBottomPx = 84,
        )

        assertEquals(AdaptiveLayoutDecision.Mode.SINGLE_COLUMN, result.mode)
        assertEquals(760, result.contentMaxWidthDp)
    }

    @Test
    fun wideLandscapeWindowUsesTwoBalancedColumns() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 1_800,
            windowHeightPx = 1_000,
            density = 2f,
        )

        assertEquals(AdaptiveLayoutDecision.Mode.TWO_COLUMNS, result.mode)
        assertEquals(24, result.hingeGapDp)
        assertEquals(result.leadingWeight, result.trailingWeight, 0f)
    }

    @Test
    fun separatingVerticalFoldCreatesAProtectedGapBetweenPanes() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 1_800,
            windowHeightPx = 1_800,
            density = 2f,
            safeLeftPx = 24,
            safeRightPx = 24,
            fold = FoldGeometry(
                separating = true,
                axis = FoldGeometry.Axis.VERTICAL,
                startPx = 890,
                endPx = 910,
            ),
        )

        assertEquals(AdaptiveLayoutDecision.Mode.TWO_COLUMNS, result.mode)
        assertEquals(34, result.hingeGapDp)
        assertTrue(result.leadingWeight > 0f)
        assertTrue(result.trailingWeight > 0f)
        assertEquals(result.leadingWeight, result.trailingWeight, 1f)
    }

    @Test
    fun asymmetricVerticalFoldKeepsTheGapAlignedWhenSafePaddingIsApplied() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 2_000,
            windowHeightPx = 1_600,
            density = 2f,
            safeLeftPx = 20,
            safeRightPx = 20,
            fold = FoldGeometry(
                separating = true,
                axis = FoldGeometry.Axis.VERTICAL,
                startPx = 700,
                endPx = 730,
            ),
        )

        assertEquals(39, result.hingeGapDp)
        assertEquals(980, result.contentMaxWidthDp)
        assertEquals(616f, result.leadingWeight, 0f)
        assertEquals(1_186f, result.trailingWeight, 0f)
        assertEquals(1_802f, result.leadingWeight + result.trailingWeight, 0f)
    }

    @Test
    fun separatingHorizontalFoldGetsIndependentTopAndBottomRegions() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 1_700,
            windowHeightPx = 1_400,
            density = 2f,
            safeTopPx = 40,
            safeBottomPx = 36,
            fold = FoldGeometry(
                separating = true,
                axis = FoldGeometry.Axis.HORIZONTAL,
                startPx = 720,
                endPx = 740,
            ),
        )

        assertEquals(AdaptiveLayoutDecision.Mode.HORIZONTAL_FOLD, result.mode)
        assertEquals(34, result.hingeGapDp)
        assertTrue(result.leadingWeight > result.trailingWeight)
    }

    @Test
    fun imeConsumingLowerHorizontalPaneFallsBackToTheLargerPhysicalPane() {
        val fold = FoldGeometry(
            separating = true,
            axis = FoldGeometry.Axis.HORIZONTAL,
            startPx = 950,
            endPx = 1_030,
        )

        val panes = AdaptiveWindowLayoutPolicy.largestSafeHorizontalPane(
            windowHeightPx = 2_400,
            safeTopPx = 80,
            safeBottomPx = 1_400,
            fold = fold,
            topClearancePx = 24,
            bottomClearancePx = 24,
        )

        assertEquals(846, panes.topHeightPx)
        assertEquals(0, panes.bottomHeightPx)
        assertEquals(HorizontalPaneSelection.Pane.TOP, panes.selectedPane)
        assertTrue("fallback viewport must remain scrollable", panes.selectedHeightPx > 0)
    }

    @Test
    fun flatFoldGuideDoesNotSplitACompactWindow() {
        val result = AdaptiveWindowLayoutPolicy.calculate(
            windowWidthPx = 1_200,
            windowHeightPx = 2_000,
            density = 3f,
            fold = FoldGeometry(
                separating = false,
                axis = FoldGeometry.Axis.VERTICAL,
                startPx = 590,
                endPx = 610,
            ),
        )

        assertEquals(AdaptiveLayoutDecision.Mode.SINGLE_COLUMN, result.mode)
    }
}

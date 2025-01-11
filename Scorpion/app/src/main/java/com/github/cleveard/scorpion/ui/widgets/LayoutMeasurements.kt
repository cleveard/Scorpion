package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measurements for laying out card groups
 * This is used to calculate how cards are overlapped
 * In each dimension we use two values to calculate the space
 *    ratio - The proportion of the lower card that should be visible
 *    minimum - A minimum visible size to insure tapping is consistent
 */
class LayoutMeasurements {
    /** Vertical spacing and size info */
    val verticalSpacing: Spacing = Spacing(0.15f, 0.3f, 0)
    /** Horizontal spacing and size info */
    val horizontalSpacing: Spacing = Spacing(0.15f, 0.2f, 0)
    /** Scale applied to cards for displaying */
    var scale: Float = 1.0f
        set(value) {
            field = value
            // Recalculate vertical and horizontal spacing
            verticalSpacing.calcSpacing()
            horizontalSpacing.calcSpacing()
        }

    /**
     * Spacing calculation for a dimension
     * @param ratio The proportion of the lower card that should be visible
     * @param minimum A minimum visible size to insure tapping is consistent
     * @param size The intrinsic size of the card images
     */
    inner class Spacing(ratio: Float, minimum: Float, size: Int) {
        /** The proportion of the lower card that should be visible */
        var ratio: Float = ratio
            set(value) {
                field = value
                calcSpacing()
            }
        /** A minimum visible size to insure tapping is consistent */
        var minimum: Dp = Dp(minimum)
            set(value) {
                field = value
                calcSpacing()
            }
        /** The intrinsic size of the card images */
        var size: Dp = size.dp
            set(value) {
                field = value
                calcSpacing()
            }
        /** Offset of cards in a group */
        var spacing: Dp = calcSpacing()
            private set

        /**
         * Calculate value to use in Modifier.spaceBy() to insure proper spacing
         */
        fun spaceBy(): Dp {
            return spacing - size * scale
        }

        /**
         * Recalculate the the spacing
         */
        fun calcSpacing(): Dp = (size * scale * ratio).coerceAtLeast(minimum).also { spacing = it }
    }
}
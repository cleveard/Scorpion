package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class LayoutMeasurements {
    val verticalSpacing: Spacing = Spacing(0.15f, 0.3f, 0)
    val horizontalSpacing: Spacing = Spacing(0.15f, 0.2f, 0)
    var scale: Float = 1.0f
        set(value) {
            field = value
            verticalSpacing.calcSpacing()
            horizontalSpacing.calcSpacing()
        }

    inner class Spacing(ratio: Float, minimum: Float, size: Int) {
        var ratio: Float = ratio
            set(value) {
                field = value
                calcSpacing()
            }
        var minimum: Dp = Dp(minimum)
            set(value) {
                field = value
                calcSpacing()
            }
        var size: Dp = size.dp
            set(value) {
                field = value
                calcSpacing()
            }
        var spacing: Dp = calcSpacing()
            private set

        fun spaceBy(): Dp {
            return spacing - size * scale
        }
        fun calcSpacing(): Dp = (size * scale * ratio).coerceAtLeast(minimum).also { spacing = it }
    }
}
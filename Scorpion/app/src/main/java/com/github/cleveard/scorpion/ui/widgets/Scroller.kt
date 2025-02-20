package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class Scroller {
    private var autoScrollJob: Job? = null
    private var scrollSpeed: Float = 0.0f
    private lateinit var scope: CoroutineScope
    private lateinit var density: Density

    abstract val limits: Pair<Dp, Dp>
    abstract var value: Dp

    @Composable
    fun rememberScrollableState(): ScrollableState {
        density = LocalDensity.current
        scope = rememberCoroutineScope()
        return rememberScrollableState {
            update(it)
        }
    }

    fun autoScroll(speed: Float) {
        if (speed == 0.0f)
            autoScrollJob?.cancel()
        else {
            scrollSpeed = speed / 10.0f
            if (autoScrollJob?.isCancelled != false) {
                autoScrollJob = scope.launch {
                    try {
                        while (true) {
                            if (update(scrollSpeed) != scrollSpeed) {
                                cancel()
                                break
                            }
                            delay(25)
                        }
                    } finally {
                        autoScrollJob = null
                    }
                }
            }
        }
    }

    fun update(change: Float = 0.0f): Float {
        // If the limits show no scrolling
        return if (limits.first >= limits.second) {
            value = 0.dp
            0.0f
        } else {
            // Calculate the next scroll offset
            val next = value + change.dp
            // If it is larger than the limit, then we will scroll far
            if (next > limits.second) {
                // The amount of scroll we use is how far the scroll
                // offset is below 0
                (limits.second.value - value.value).also {
                    // The new scrollOffset is limits.second
                    value = limits.second
                }
            } else if (next < limits.first) {
                // We will scroll up to far. The amount of scroll used is the amount
                // required to move the bottom of the content to the bottom of the
                // playable area.
                (limits.first.value - value.value).also {
                    // The new scroll offset puts the bottom of the content to the bottom of the playable area
                    value = limits.first
                }
            } else {
                // Scroll and use all of the scroll delta
                value += change.dp
                change
            }
        }
    }
}

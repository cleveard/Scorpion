package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.cleveard.scorpion.ui.games.Game

/**
 * Composable functions for a card group
 * Card groups can be laid out in columns or rows
 */
class CardGroup(val group: Int, val pass: Pass = Pass.Main) {
    private val _offset: MutableState<DpOffset> = mutableStateOf(DpOffset.Zero)
    /** The size of the group */
    var size: DpSize = DpSize.Zero
    /** The spacing of cards in the group */
    var spacing: DpSize = DpSize.Zero
    /** The drawables of cards in the group */
    val cards: SnapshotStateList<CardDrawable?> = mutableStateListOf()

    /** The offset of the group in the playable area */
    var offset: DpOffset
        get() = _offset.value
        set(value) { _offset.value = value }

    /**
     * Recalculate the card offsets for the group
     * @param cardSize The size of each card
     */
    fun cardsUpdated(cardSize: DpSize) {
        // Set the group size
        size = if (cards.isEmpty())
            cardSize
        else {
            // Set the card offset
            for (card in cards) {
                card?.let { drawable ->
                    // Make sure the size is correct
                    drawable.size = cardSize
                    // The offset is just the spacing time the offset position
                    drawable.offset = DpOffset(spacing.width * drawable.offsetPos, spacing.height * drawable.offsetPos)
                }
            }
            // The size of the group
            spacing * cards.last()!!.offsetPos + cardSize
        }
    }

    /**
     * Card group laid out
     */
    @Composable
    fun Content(
        game: Game,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        gestures: @Composable (Modifier.(CardDrawable) -> Modifier) = {
            clickGestures(it, game)
        }
    ) {
        // Add all of the cards to the row
        for (i in cards.indices) {
            // Add the card image
            GetImage(
                i,
                modifier = modifier,
                cardPadding = cardPadding,
                gestures = gestures
            )
        }
    }

    /**
     * Add an image for a card to the group
     * @param i The index of the drawable
     * @param modifier Used to set the width and height of the composable
     * @param cardPadding The padding around the card
     * @param gestures Callback to add gestures to the image
     */
    @Composable
    private fun GetImage(
        i: Int,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        gestures: @Composable (Modifier.(CardDrawable) -> Modifier)
    ) {
        // Don't need to do anything for null drawables. Their
        // spacing is already reflected in the drawable offsets
        cards.getOrNull(i)?.let { drawable ->
            // Skip drawables that are not visible
            if (drawable.visible) {
                // A box to hold the image
                Box(
                    // Align image in center of box
                    contentAlignment = Alignment.Center,
                    // Set the box offset
                    modifier = modifier.offset {
                        IntOffset((offset.x + drawable.offset.x).roundToPx(), (offset.y + drawable.offset.y).roundToPx())
                    }
                        // Set box size
                        .size(drawable.size)
                        // Add any gestures
                        .gestures(drawable)
                        // Set the card padding
                        .padding(cardPadding)
                ) {
                    if (drawable.pass == pass) {
                        // Get the image
                        AsyncImage(
                            drawable.imagePath,
                            contentDescription = "",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier,
                            colorFilter = drawable.colorFilter,
                        )
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "pass=$pass, offset=(${_offset.value.x.value},${_offset.value.y.value}), size=(${size.width.value},${size.height.value}), spacing=(${spacing.width.value},${spacing.height.value})\ncards=${cards.joinToString("\n")}"
    }

    /** Content passes */
    enum class Pass {
        /** The content in the main card groups */
        Main,
        /** The content in the dragging card groups */
        Drag
    }

    companion object {

        @OptIn(ExperimentalFoundationApi::class)
        fun Modifier.clickGestures(drawable: CardDrawable, game: Game): Modifier {
            return combinedClickable(
                onClick = { game.onClick(drawable.card) },
                onDoubleClick = { game.onDoubleClick(drawable.card) }
            )
        }

        @Composable
        fun Modifier.dragAndDropCard(drawable: CardDrawable, drop: DropCard): Modifier {
            val dragger = remember { CardDragger(drop) }
            return pointerInput(drawable) {
                detectDragGestures(
                    onDragStart = {
                        with(dragger) {
                            start(drawable, it)
                        }
                    },
                    onDragEnd = {
                        dragger.end()
                    },
                    onDragCancel = {
                        dragger.cancel()
                    }
                ) { change, dragAmount ->
                    change.consume()
                    with(dragger) {
                        drag(dragAmount)
                    }
                }
            }
        }
    }
}

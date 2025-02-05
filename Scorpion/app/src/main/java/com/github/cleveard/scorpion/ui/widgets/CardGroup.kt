package com.github.cleveard.scorpion.ui.widgets

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.ui.games.Game

interface CardDropTarget {
    /**
     * A card has been dropped on another.
     *
     * @return true to indicate that the [DragAndDropEvent] was consumed; false indicates it was
     * rejected.
     */
    fun onDrop(sourceCard: Card, targetCard: Card, event: DragAndDropEvent): Boolean

    /** A drag and drop session has just been started and this [DragAndDropTarget] is eligible
     * to receive it. This gives an opportunity to set the state for a [DragAndDropTarget] in
     * preparation for consuming a drag and drop session.
     */
    fun onStarted(sourceCard: Card, event: DragAndDropEvent) = Unit

    /**
     * An item being dropped has entered into the bounds of this [DragAndDropTarget].
     */
    fun onEntered(sourceCard: Card, targetCard: Card, event: DragAndDropEvent) = Unit

    /**
     * An item being dropped has moved within the bounds of this [DragAndDropTarget].
     */
    fun onMoved(sourceCard: Card, targetCard: Card, event: DragAndDropEvent) = Unit

    /**
     * An item being dropped has moved outside the bounds of this [DragAndDropTarget].
     */
    fun onExited(sourceCard: Card, targetCard: Card, event: DragAndDropEvent) = Unit

    /**
     * An event in the current drag and drop session has changed within this [DragAndDropTarget]
     * bounds. Perhaps a modifier key has been pressed or released.
     */
    fun onChanged(sourceCard: Card, targetCard: Card, event: DragAndDropEvent) = Unit

    /**
     * The drag and drop session has been completed. All [DragAndDropTarget] instances in the
     * hierarchy that previously received an [onStarted] event will receive this event. This gives
     * an opportunity to reset the state for a [DragAndDropTarget].
     */
    fun onEnded(sourceCard: Card, event: DragAndDropEvent) = Unit
}

/**
 * Composable functions for a card group
 * Card groups can be laid out in columns or rows
 */
class CardGroup {
    private val _offset: MutableState<DpOffset> = mutableStateOf(DpOffset.Zero)
    /** The size of the group */
    var size: DpSize = DpSize.Zero
    /** The spacing of cards in the group */
    var spacing: DpSize = DpSize.Zero
    /** The drawbles of cards in the group */
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

    @OptIn(ExperimentalFoundationApi::class)
    fun Modifier.clickGestures(card: Card, game: Game): Modifier {
        return combinedClickable(
            onClick = { game.onClick(card) },
            onDoubleClick = { game.onDoubleClick(card) }
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun Modifier.dragSourceCard(card: Card): Modifier {
        return dragAndDropSource {
                detectDragGestures(
                    onDragStart = {
                        startTransfer(
                            DragAndDropTransferData(
                                ClipData.newPlainText("Card", "Drag"),
                                card
                            )
                        )
                    }
                ) { _, _ -> }
            }
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun Modifier.dragTargetCard(card: Card, target: CardDropTarget): Modifier {
        return dragAndDropTarget(
            shouldStartDragAndDrop = {
                (it.toAndroidDragEvent().localState as? Card) != null
            }, target = object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    return (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onDrop(it, card, event)
                    }?: false
                }

                override fun onChanged(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onChanged(it, card, event)
                    }
                }

                override fun onEnded(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onEnded(it, event)
                    }
                }

                override fun onEntered(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onEntered(it, card, event)
                    }
                }

                override fun onExited(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onExited(it, card, event)
                    }
                }

                override fun onMoved(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onMoved(it, card, event)
                    }
                }

                override fun onStarted(event: DragAndDropEvent) {
                    (event.toAndroidDragEvent().localState as? Card)?.let {
                        target.onStarted(it, event)
                    }
                }
            })
    }

    /**
     * Card group laid out
     */
    @Composable
    fun Content(
        game: Game,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        gestures: @Composable (Modifier.(Card) -> Modifier) = {
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
        gestures: @Composable (Modifier.(Card) -> Modifier)
    ) {
        // Don't need to do anything for null drawables. Their
        // spacing is already reflected in the drawable offsets
        cards[i]?.let { drawable ->
            // Skip drawables that are not visible
            if (drawable.visible != false) {
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
                        .gestures(drawable.card)
                        // Set the card padding
                        .padding(cardPadding)
                ) {
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

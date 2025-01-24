package com.github.cleveard.scorpion.ui.widgets

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
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
object CardGroup {
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
     * Card group laid out in a column
     */
    @Composable
    fun ColumnContent(
        cards: List<Card?>,
        game: Game,
        size: DpSize,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        gestures: (@Composable Modifier.(Card) -> Modifier) = {
            clickGestures(it, game)
        }
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = verticalArrangement
        ) {
            // Set the height and width of the card
            val imageModifier = Modifier.size(size)
            // Add all of the cards to the columns
            for (card in cards) {
                // Only add the card if it is null or the next card is null or the next card has spread set
                // Spread means that the next card should offset from this card, so part of this card is visible.
                // Null values always take up space, though without drawing anything, so if this card is null
                // or the next card is null we need to add the card. Theoretically, nulls at the end of a group
                // could be skipped.
                if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                    // Add the card image
                    GetImage(
                        card,
                        game,
                        modifier = imageModifier,
                        cardPadding = cardPadding,
                        gestures = gestures
                    )
                }
            }
        }
    }

    /**
     * Card group laid out in a row
     */
    @Composable
    fun RowContent(
        cards: List<Card?>,
        game: Game,
        size: DpSize,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        gestures: (@Composable Modifier.(Card) -> Modifier) = {
            clickGestures(it, game)
        }
    ) {
        // Always layout the cards left-to-right, because the card value is always on the
        // left side of the card. If the cards overlap, we want the card on top to be offset
        // to the right.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement
            ) {
                // Set the height and width of the card
                val imageModifier = Modifier.size(size)
                // Add all of the cards to the row
                for (card in cards) {
                    // Only add the card if it is null or the next card is null or the next card has spread set
                    // Spread means that the next card should offset from this card, so part of this card is visible.
                    // Null values always take up space, though without drawing anything, so if this card is null
                    // or the next card is null we need to add the card. Theoretically, nulls at the end of a group
                    // could be skipped.
                    if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                        // Add the card image
                        GetImage(
                            card,
                            game,
                            modifier = imageModifier,
                            cardPadding = cardPadding,
                            gestures = gestures
                        )
                    }
                }
            }
        }
    }

    /**
     * Add an image for a card to the group
     * @param card The card to add, or null to add a spacer
     * @param game The game interface
     * @param modifier Used to set the width and height of the composable
     * @param cardPadding The padding around the card
     */
    @Composable
    private fun GetImage(
        card: Card?,
        game: Game,
        modifier: Modifier = Modifier,
        cardPadding: PaddingValues = PaddingValues(0.dp),
        gestures: (@Composable Modifier.(Card) -> Modifier)
    ) {
        val gesture = card?.let { modifier.gestures(it) }?: modifier
        Box(
            contentAlignment = Alignment.Center,
            modifier = gesture.padding(cardPadding)
        ) {
            // If card is null, return, the composable modifier will take up space
            if (card != null) {
                // The filter to apply to the image
                var filter: ColorFilter? = null
                // Get the asset path for the image
                val resourcePath = if (card.faceDown)
                    game.cardBackAssetPath      // Card back asset path
                else {
                    // Get the filter for the highlight
                    filter = game.getFilter(card.highlight)
                    // Get the card front asset path
                    game.cardFrontAssetPath(card.value)
                }
                // Get the image
                AsyncImage(
                    resourcePath,
                    contentDescription = "",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier,
                    colorFilter = filter,
                )
            }
        }
    }
}

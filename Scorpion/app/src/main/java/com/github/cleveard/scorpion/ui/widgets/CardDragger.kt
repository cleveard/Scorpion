package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Interface used to drag and drop cards
 */
interface DropCard {
    /** List of card groups */
    val cards: List<CardGroup>

    /**
     * A drag and drop session has just been started.
     */
    fun onStarted(sourceDrawable: CardDrawable, dragDrawables: (List<CardDrawable>) -> List<CardGroup>): List<CardGroup>

    /**
     * An item being dropped has entered into the bounds of this [DragAndDropTarget].
     */
    fun onEntered(sourceDrawable: CardDrawable, targetDrawable: CardDrawable) = Unit

    /**
     * An item being dropped has moved outside the bounds of this [DragAndDropTarget].
     */
    fun onExited(sourceDrawable: CardDrawable, targetDrawable: CardDrawable) = Unit

    /**
     * The drag and drop session has been completed. All [DragAndDropTarget] instances in the
     * hierarchy that previously received an [onStarted] event will receive this event. This gives
     * an opportunity to reset the state for a [DragAndDropTarget].
     */
    fun onEnded(sourceDrawable: CardDrawable, targetDrawable: CardDrawable?) = Unit
}

/**
 * Class to handle details of drag and drop
 * @param drop Interface to game drop handler
 */
class CardDragger(private val drop: DropCard) {
    /** List of groups used to drag cards */
    private var list = listOf<CardGroup>()
    /** First drawable in drag list */
    private var first: CardDrawable? = null
    /** Position of the pointer in the playable area during a drag */
    private var position = DpOffset.Zero
    /** The last card the pointer was over */
    private var over: CardDrawable? = null

    /**
     * Start dragging
     * @param drawable The drawable starting the drag
     * @param offset The offset of the pointer from the top left of the card
     */
    fun Density.start(drawable: CardDrawable, offset: Offset) {
        // Get the list of cards we will be dragging
        list = drop.onStarted(drawable) {drag ->
            first = drag.first()
            // Put the drawables in drag in groups and
            // return the list of groups
            sortedMapOf<Int, CardGroup>().let {
                // Put each drawable in a group
                drag.forEach { drawable ->
                    // Add the drawable to a group
                    it.getOrPut(drawable.card.group) {
                        newGroup(drop.cards[drawable.card.group])
                    }.cards.add(drawable)
                }
                // Return the list of groups
                it.values.toList()
            }
        }
        // Calculate the position of the finger in the playable area
        // The offset is in pixels and needs to be converted to Dp
        position = drawable.offset + drop.cards[drawable.card.group].offset +
            DpOffset(offset.x.toDp(), offset.y.toDp())
        // We start not over anything
        over = null
    }

    /**
     * Drag the drawables
     * @param dragAmount The drag change in pixels
     */
    fun Density.drag(dragAmount: Offset) {
        // Convert drag pixels to Dp
        val dragDp = DpOffset(dragAmount.x.toDp(), dragAmount.y.toDp())
        // Move each group by the offset
        list.forEach { it.offset += dragDp }
        // Move the pointer position by the offset
        position += dragDp
        // Hit test the pointer position
        val card = drop.cards.hitTest(position)
        val temp = over
        if (temp != card) {
            // Finger hit test changed
            if (temp != null) {
                // Exit the previous card
                drop.onExited(first!!, temp)
            }
            if (card != null) {
                // Enter the current card
                drop.onEntered(first!!, card)
            }
            // Remember the hitTest result
            over = card
        }
    }

    /**
     * Drag is finished
     */
    fun end() {
        // Let the game know the drag ended and where the pointer was
        over?.let { drop.onExited(first!!, it) }
        drop.onEnded(first!!, over)
    }

    /**
     * Drag is finished
     */
    fun cancel() {
        over?.let { drop.onExited(first!!, it) }
        // Let the game know the drag ended without a drop
        drop.onEnded(first!!, null)
    }

    /**
     * Create a new group for dragging.
     */
    private fun newGroup(group: CardGroup): CardGroup {
        return CardGroup(-1, CardGroup.Pass.Drag).apply {
            offset = group.offset
            size = group.size
            spacing = group.spacing
        }
    }

    /**
     * Find the card under position
     * @param position The position to check
     * @return The card or null if we are not over a card
     */
    private fun List<CardGroup>.hitTest(position: DpOffset): CardDrawable? {
        // Find a group we are over
        for (i in this.lastIndex downTo 0) {
            val g = this[i]
            val groupDelta = position - g.offset
            if (groupDelta.x >= 0.dp && groupDelta.y >= 0.dp && groupDelta.x < g.size.width && groupDelta.y < g.size.height) {
                // Found a group find the card in the group
                for (j in g.cards.lastIndex downTo 0) {
                    g.cards[j]?.let {d ->
                        if (d.pass == CardGroup.Pass.Main && d.card.faceUp) {
                            val cardDelta = groupDelta - d.offset
                            if (cardDelta.x >= 0.dp && cardDelta.y >= 0.dp && cardDelta.x < d.size.width && cardDelta.y < d.size.height) {
                                // Found a card
                                return d
                            }
                        }
                    }
                }
            }
        }

        return null
    }
}

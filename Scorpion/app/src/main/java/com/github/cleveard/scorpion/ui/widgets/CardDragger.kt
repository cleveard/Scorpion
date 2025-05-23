package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Interface used to drag and drop cards
 */
interface DropCard {
    /** List of card groups */
    val cards: List<CardGroup>

    /** Convert a coordinate in the play area to a coordinate in the game */
    fun toGame(offset: DpOffset): DpOffset = offset

    /** Convert a coordinate in the game to a coordinate in the play area */
    fun toPlayArea(offset: DpOffset): DpOffset = offset

    /**
     * A drag and drop session has just been started.
     */
    fun onStarted(sourceDrawable: CardDrawable, dragDrawables: (List<CardDrawable>) -> List<CardGroup>): List<CardGroup>

    /**
     * Report a drag event at an offset
     */
    fun onDrag(sourceDrawable: CardDrawable, offset: DpOffset) = Unit

    /**
     * An item being dropped has entered into the bounds of this [DragAndDropTarget].
     */
    fun onEntered(sourceDrawable: CardDrawable, targetDrawable: Any): Boolean = true

    /**
     * An item being dropped has moved outside the bounds of this [DragAndDropTarget].
     */
    fun onExited(sourceDrawable: CardDrawable, targetDrawable: Any) = Unit

    /**
     * The drag and drop session has been completed. All [DragAndDropTarget] instances in the
     * hierarchy that previously received an [onStarted] event will receive this event. This gives
     * an opportunity to reset the state for a [DragAndDropTarget].
     */
    fun onEnded(sourceDrawable: CardDrawable, targetDrawable: Any?, velocity: DpOffset) = Unit
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
    private var over: Any? = null

    /**
     * Start dragging
     * @param drawable The drawable starting the drag
     * @param offset The offset of the pointer from the top left of the card
     */
    fun Density.start(drawable: CardDrawable, offset: Offset) {
        // Get the list of cards we will be dragging
        list = drop.onStarted(drawable) { drag ->
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
        position = drop.toPlayArea(drawable.offset + drop.cards[drawable.card.group].offset +
            DpOffset(offset.x.toDp(), offset.y.toDp()))
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
        // Report drag event
        drop.onDrag(first!!, position)
        // Hit test the pointer position
        drop.cards.hitTest(drop.toGame(position))
    }

    /**
     * Drag is finished
     */
    fun Density.end(velocity: Velocity) {
        // Let the game know the drag ended and where the pointer was
        over?.let { drop.onExited(first!!, it) }
        val velocityDp = DpOffset(velocity.x.toDp(), velocity.y.toDp())
        drop.onEnded(first!!, over, velocityDp)
    }

    /**
     * Drag is finished
     */
    fun cancel() {
        over?.let { drop.onExited(first!!, it) }
        // Let the game know the drag ended without a drop
        drop.onEnded(first!!, null, DpOffset.Zero)
    }

    /**
     * Create a new group for dragging.
     */
    private fun newGroup(group: CardGroup): CardGroup {
        return CardGroup(-1, CardGroup.Pass.Drag).apply {
            offset = drop.toPlayArea(group.offset)
            size = group.size
            spacing = group.spacing
        }
    }

    private fun enterAndExit(drawable: Any?): Boolean {
        // Found a card
        val temp = over
        return if (temp != drawable) {
            // Finger hit test changed
            if (temp != null) {
                // Exit the previous card
                drop.onExited(this@CardDragger.first!!, temp)
            }
            // Enter the current card
            drawable?.let {d ->
                drop.onEntered(this@CardDragger.first!!, d).also {
                    // Remember the hitTest result
                    if (!it)
                        over = drawable
                }
            } ?: false.also { over = null }
        } else
            false
    }

    /**
     * Find the card under position
     * @param position The position to check
     * @return The card or null if we are not over a card
     */
    private fun List<CardGroup>.hitTest(position: DpOffset) {
        // Find a group we are over
        for (i in this.lastIndex downTo 0) {
            val g = this[i]
            val groupDelta = position - g.offset
            if (groupDelta.x >= 0.dp && groupDelta.y >= 0.dp && groupDelta.x < g.size.width && groupDelta.y < g.size.height) {
                // Found a group find the card in the group
                for (j in g.cards.lastIndex downTo 0) {
                    g.cards[j]?.let { d ->
                        if (d.pass == CardGroup.Pass.Main && d.card.faceUp) {
                            val cardDelta = groupDelta - d.offset
                            if (cardDelta.x >= 0.dp && cardDelta.y >= 0.dp && cardDelta.x < d.size.width && cardDelta.y < d.size.height) {
                                if (!enterAndExit(d))
                                    return
                            }
                        }
                    }
                }
            }
        }

        // Find a group we are over
        for (i in this.lastIndex downTo 0) {
            val g = this[i]
            val groupDelta = position - g.offset
            if (g.cards.isEmpty() && groupDelta.x >= 0.dp && groupDelta.y >= 0.dp && groupDelta.x < g.size.width && groupDelta.y < g.size.height) {
                if (!enterAndExit(g))
                    return
            }
        }

        enterAndExit(null)
    }
}

package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import com.github.cleveard.scorpion.R
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Actions
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.CardGroup

sealed class Game(
    /** Database and card manager */
    protected val dealer: Dealer,
    /** The state of the game */
    val state: StateEntity,
    /** The qualified class name of the game */
    val name: String,
    /** The number of groups in the game */
    val groupCount: Int,
    /** The resource id of the name of the game */
    val displayNameId: Int
): Actions {
    /** The asset path for image use for backs of cards */
    abstract val cardBackAssetPath: String

    /** Save pass values for cards being dragged */
    private val savePasses: MutableMap<Int, CardGroup.Pass> = mutableMapOf()
    /** The list of groups being dragged */
    private val _dragPass: MutableState<List<CardGroup>?> = mutableStateOf(null)
    private val dragPass: List<CardGroup>?
        get() = _dragPass.value
    /** Flag to indicate the player cheated on the last play */
    var cheated: Boolean = false
    /** The number of times the player has cheated in this game */
    var cheatCount: Int = state.bundle.getInt(CHEAT_COUNT, 0)
        set(value) {
            if (field != value) {
                field = value
                state.bundle.putInt(CHEAT_COUNT, value)
                state.onBundleUpdated()
            }
        }

    /**
     * Get the asset path for the image of the front of a card
     * @param value The card value of the card
     */
    abstract fun cardFrontAssetPath(value: Int): String

    /**
     * Return the list of cards that were dealt
     * @param shuffled The card values in the order that cards are dealt
     * @return The list of cards
     */
    abstract suspend fun deal(shuffled: IntArray): List<Card>

    /**
     * The game content composable
     * @param modifier The modifier for the composable
     */
    @Composable
    abstract fun Content(modifier: Modifier)

    /**
     * Setup the group offsets and sizes
     */
    abstract fun setupGroups()

    /**
     * Game content for the variant dialog
     * @return The content for the dialog
     * A new game is always dealt after accepting these settings
     */
    abstract fun variantContent(): DialogContent?

    /**
     * Game content for the settings dialog
     * @return The content fo the dialog
     * These settings can be applied to the current game
     */
    abstract fun settingsContent(): DialogContent?

    /**
     * Get the filter for a highlight
     * @param highlight The highlight of the card
     * @return The filter. Null means no highlight is shown
     */
    abstract fun getFilter(highlight: Int): ColorFilter?

    /**
     * Check whether the game is over
     * @param generation The current generation
     * The game can do more or less what it wants. It can update cards
     * or show a dialog
     */
    abstract suspend fun checkGameOver(generation: Long)

    /**
     * Check the validity of the current card list and groups
     * @return Error string if something is wrong, or null if all is OK
     */
    abstract fun isValid(): String?

    /**
     * Inform the game that cards have change so it can update
     * the drawable offsets and sizes
     */
    abstract fun cardsUpdated()

    /**
     * The user did something clear the cheat flags
     */
    abstract fun clearCheats()

    /**
     * Start dragging cards
     * @param cardsToDrag The list of card groups to drag
     */
    fun startDrag(cardsToDrag: List<CardGroup>) {
        // Better not be dragging already
        if (_dragPass.value != null)
            throw java.lang.IllegalArgumentException("Dragging already started")
        // All groups had better be for the drag pass
        if (cardsToDrag.any { it.pass != CardGroup.Pass.Drag })
            throw java.lang.IllegalArgumentException("Drag pass groups must use Pass.Drag")
        // Clear the saved passes
        savePasses.clear()
        // Add the current passes for each drawable in cardsToDrag
        cardsToDrag.forEach { g ->
            g.cards.forEach {d ->
                d?.let {
                    // Save the drawable pss
                    savePasses[it.card.value] = it.pass
                }
            }
        }
        // Set the pass for each drawable to Drag
        savePasses.forEach {
            dealer.drawables[it.key].pass = CardGroup.Pass.Drag
        }
        // Start composing the dragging cards
        _dragPass.value = cardsToDrag
    }

    /**
     * End dragging cards
     */
    fun endDrag() {
        // Ignore the call if we aren't dragging
        _dragPass.value?.let {
            // Restore the passes for all of the drawables
            savePasses.forEach {
                dealer.drawables[it.key].pass = it.value
            }
            // Clear the saved passes
            savePasses.clear()
            // Stop composing the dragging cards
            _dragPass.value = null
        }
    }

    /**
     * Compose the dragging cards
     * Add this at the end of the game content so
     * the dragging cards are on top of everything else
     */
    @Composable
    fun DragContent(cardPadding: Dp) {
        // Just compose the content for each group
        dragPass?.forEach {group ->
            group.Content(
                dealer.game,
                cardPadding = PaddingValues(cardPadding),
                gestures = { this }
            )
        }
    }

    /**
     * Add a card to the card changed list
     * @param generation The new generation
     * @param group The new group
     * @param position The new position
     * @param highlight The new highlight
     * @param faceDown The new faceDown flag
     * @param spread The new spread flag
     */
    protected fun Card.changed(
        generation: Long = this.generation,
        group: Int = this.group,
        position: Int = this.position,
        highlight: Int = this.highlight,
        faceDown: Boolean = this.faceDown,
        spread: Boolean = this.spread
    ) {
        // Add the card remember how many cards have changed
        dealer.cardChanged(copy(
            generation = generation,
            group = group,
            position = position,
            highlight = highlight,
            faceDown = faceDown,
            spread = spread
        ))
    }

    protected fun showGameWon() {
        if (cheatCount == 0)
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)   // No show the dialog
        else
            dealer.showNewGameOrDismissAlert(R.plurals.game_won, R.string.congratulations, cheatCount, cheatCount) // Yes the dialog with cheat count
    }

    companion object {
        const val CARDS_PER_SUIT: Int = 13
        @Suppress("MemberVisibilityCanBePrivate")
        const val SUIT_COUNT: Int = 4
        const val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT
        /** Key for the cheat count */
        const val CHEAT_COUNT: String = "cheat_count"
    }
}

/**
 * Class used to track cheating with a value
 * This class is used keep the cheating state with a value.
 * Games can use it when they want to return a value and
 * indicate the the player cheated to make the value.
 */
sealed class Cheating<T>(val value: T) {
    /** Class when the player cheated */
    class Cheated<T>(value: T): Cheating<T>(value) {
        override fun <R> transform(xform: (T) -> R): Cheating<R> = Cheated(xform(value))
        context(Game)
        override fun setCheated(): T = value.also {
            cheated = true
        }
    }
    /** Class when the player didn't cheat */
    class DidNotCheat<T>(value: T): Cheating<T>(value) {
        override fun <R> transform(xform: (T) -> R): Cheating<R> = DidNotCheat(xform(value))
        context(Game)
        override fun setCheated(): T = value
    }

    /**
     * Transform a cheat class
     * Transform the value of the cheating object keeping the type
     * @param xform Lambda to transform the cheating value
     */
    abstract fun <R> transform(xform: (T) -> R): Cheating<R>

    /**
     * Set cheated flag and return the value
     */
    context(Game)
    abstract fun setCheated(): T
}

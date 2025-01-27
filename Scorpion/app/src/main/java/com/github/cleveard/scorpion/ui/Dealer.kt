package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.games.Game
import com.github.cleveard.scorpion.ui.widgets.CardDrawable
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import kotlinx.coroutines.CoroutineScope

/**
 * Interface to application
 */
interface Dealer {
    /** Coroutine scope for launching coroutines */
    val scope: CoroutineScope
    /**
     * The current game.
     * Used by the main activity to start composition
     * */
    val game: Game
    /** The list of card groups */
    val cards: List<CardGroup>
    /** The asset path of the back of a card */
    val cardBackAssetPath: String
    /** True to use the personal colors for the theme */
    val useSystemTheme: Boolean
    /** The aspect ratio of the card */
    val cardAspect: Float
    /**
     * Show dialog
     * Set this to a non null value to show dialog. The content of the dialog
     * is the content of the composable
     */
    var showAlert: (@Composable () -> Unit)?

    /**
     * Get the asset path of the front of a card
     * @param value The value of the card
     * @return The asset path
     */
    fun cardFrontAssetPath(value: Int): String

    /**
     * Deal a new game
     */
    suspend fun deal()

    /**
     * Show the game variants dialog
     */
    suspend fun gameVariants()

    /**
     * Show the settings dialog
     */
    suspend fun settings()

    /**
     * Find a card from the card value
     * @param cardValue The card value of the card
     * @return The card
     */
    fun findCard(cardValue: Int): Card

    /**
     * Run a block of code and update the data base
     * @param action The block of code to run. Generation is the new undo generation
     * @return The return value of action
     */
    suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T

    /**
     * Add a card change to the database
     * @param card The changed card
     * @return The number of cards changed since the last generation
     * The database is not updated until the final withUndo returns.
     */
    fun cardChanged(card: Card): Int

    /**
     * Update the state for a game in the database
     * @param state The state to be updated
     */
    suspend fun onStateChanged(state: StateEntity)

    /**
     * Check whether there are generations that can be undone
     * @return True if there are generations that can be undone
     */
    fun canUndo(): Boolean

    /**
     * Check whether there are generations that can be redone
     * @return True if there are generations that can be redone
     */
    fun canRedo(): Boolean

    /**
     * Undo the current generation
     * @return The cards that need to change for the the undo, or
     *         null if there was nothing to undo
     */
    suspend fun undo(): List<Card>?

    /**
     * Redo the next generation
     * @return The cards that need to change for the the redo, or
     *         null if there was nothing to redo
     */
    suspend fun redo(): List<Card>?

    /**
     * Show a simple dialog and wait for completion
     * @param title A resource id for the title string
     * @param buttons Resource ids for the text of the buttons to complete the dialog
     * @param content The content of the dialog between the title and the completion buttons
     * @return The resource id of the text of the button that completed the dialog.
     *         Zero is returned if the dialog is dismissed without pressing a button.
     */
    suspend fun showDialog(title: Int, vararg buttons: Int, content: @Composable () -> Unit): Int

    /**
     * Show an alert to the user
     * @param text The resource id of the text of the alert
     * @param title The resource id of the title of the alert. Set to 0 for no title.
     * @param plural An optional integer value to show alert text plurals. Set to null for no plurals
     * @param args Optional arguments used to include in the alert text.
     */
    suspend fun showNewGameOrDismissAlert(text: Int, title: Int = 0, plural: Int? = null, vararg args: Any)
}


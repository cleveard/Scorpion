package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpSize
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Actions
import com.github.cleveard.scorpion.ui.DialogContent

sealed class Game(
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
     * @param list The current values of all of the cards
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

    companion object {
        const val CARDS_PER_SUIT: Int = 13
        @Suppress("MemberVisibilityCanBePrivate")
        const val SUIT_COUNT: Int = 4
        const val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT
    }
}

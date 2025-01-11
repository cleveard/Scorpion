package com.github.cleveard.scorpion.ui

import com.github.cleveard.scorpion.db.Card

/**
 * Interface for actions handled by a game
 */
interface Actions {
    /**
     * Called when a card is tapped
     * @param card The tapped card
     */
    fun onClick(card: Card)

    /**
     * Called when a card is double tapped
     * @param card The tapped card
     */
    fun onDoubleClick(card: Card)
}

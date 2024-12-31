package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.widgets.LayoutMeasurements

interface Game: Actions {
    val measurements: LayoutMeasurements
    val cardBackAssetPath: String
    val name: String
    val groupCount: Int

    fun cardFrontAssetPath(value: Int): String

    suspend fun deal(shuffled: IntArray): List<CardEntity>

    @Composable
    fun Content(modifier: Modifier)

    fun variantContent(): DialogContent?

    fun settingsContent(): DialogContent?

    fun getFilter(highlight: Int): ColorFilter?

    suspend fun checkGameOver(list: List<Card>, generation: Long)

    fun isValid(cards: List<Card>, card: Card, lastCard: Card?): String?

    companion object {
        const val CARDS_PER_SUIT: Int = 13
        @Suppress("MemberVisibilityCanBePrivate")
        const val SUIT_COUNT: Int = 4
        const val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT

    }
}

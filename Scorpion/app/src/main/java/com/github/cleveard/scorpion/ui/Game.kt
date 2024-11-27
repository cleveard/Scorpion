package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.widgets.LayoutMeasurements

interface Game: Actions {
    val measurements: LayoutMeasurements
    val cardBackAssetName: String

    fun deal()

    fun restart(cardList: List<CardEntity>)

    @Composable
    fun Content(modifier: Modifier)

    fun getFilter(highlight: Int): ColorFilter?

    companion object {
        const val CARDS_PER_SUIT: Int = 13
        @Suppress("MemberVisibilityCanBePrivate")
        const val SUIT_COUNT: Int = 4
        const val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT

    }
}

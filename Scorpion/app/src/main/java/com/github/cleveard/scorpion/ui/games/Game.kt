package com.github.cleveard.scorpion.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Actions
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.LayoutMeasurements

sealed class Game(
    val state: StateEntity,
    val measurements: LayoutMeasurements,
    val name: String,
    val groupCount: Int,
    val displayNameId: Int
): Actions {
    abstract val cardBackAssetPath: String

    abstract fun cardFrontAssetPath(value: Int): String

    abstract suspend fun deal(shuffled: IntArray): List<Card>

    @Composable
    abstract fun Content(modifier: Modifier)

    abstract fun variantContent(): DialogContent?

    abstract fun settingsContent(): DialogContent?

    abstract fun getFilter(highlight: Int): ColorFilter?

    abstract suspend fun checkGameOver(list: List<Card>, generation: Long)

    abstract fun isValid(cards: List<Card>, card: Card, lastCard: Card?): String?

    companion object {
        const val CARDS_PER_SUIT: Int = 13
        @Suppress("MemberVisibilityCanBePrivate")
        const val SUIT_COUNT: Int = 4
        const val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT
    }
}

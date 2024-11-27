package com.github.cleveard.scorpion

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.Game
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.widgets.ScorpionGame

class MainActivityViewModel: ViewModel(), Dealer {
    val cards: MutableList<CardEntity> = mutableListOf<CardEntity>().apply {
        repeat(Game.CARD_COUNT) {
            add(CardEntity(0, it, 0, 0, mutableIntStateOf(0)))
        }
    }

    val game: Game = ScorpionGame(this).apply {
        deal()
    }

    override val deck: List<CardEntity>
        get() = cards

    override fun shuffle(): List<CardEntity> {
        return mutableListOf<CardEntity>().apply {
            addAll(cards)
            shuffle()
        }
    }

    override fun findCard(cardValue: Int): CardEntity {
        return cards[cardValue]
    }
}

package com.github.cleveard.scorpion

import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.State
import com.github.cleveard.scorpion.ui.Game
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.widgets.ScorpionGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivityViewModel: ViewModel(), Dealer {
    override val scope: CoroutineScope
        get() = viewModelScope
    val cards: MutableList<Card> = mutableListOf<Card>().apply {
        repeat(Game.CARD_COUNT) {
            add(Card(0, it, 0, 0, mutableIntStateOf(0)))
        }
    }

    override val game: Game = ScorpionGame(this)

    override val deck: List<Card>
        get() = cards

    override suspend fun deal() {
        cards.map { it.copy() }.toMutableList().apply {
            shuffle()
            val state = game.deal(this)
            for (c in this)
                cards[c.value] = c
            CardDatabase.db.newGeneration(state, cards, 0L)
        }
    }

    override suspend fun gameVariants() {
        deal()
    }

    override fun findCard(cardValue: Int): Card = cards[cardValue]

    override suspend fun withUndo(name: String, action: suspend () -> Unit) {
        var success = false
        try {
            success = CardDatabase.db.withUndo(name, action)
        } finally {
            if (!success)
                resetGame()
        }
    }

    override fun cardChanged(card: Card) = CardDatabase.db.cardChanged(card)

    override fun stateChanged(stateBlob: ByteArray) = CardDatabase.db.stateChanged(stateBlob)

    override suspend fun undo(): Pair<State, List<Card>>? = CardDatabase.db.undo()?.also { updateGame(it.first, it.second) }

    override suspend fun redo(): Pair<State, List<Card>>? = CardDatabase.db.redo()?.also { updateGame(it.first, it.second) }

    private fun updateGame(state: State, list: List<Card>) {
        game.setCards(list)
        for (card in list)
            cards[card.value] = card
    }

    private suspend fun resetGame() {
        CardDatabase.db.loadGame()?.let {pair ->
            game.setCards(pair.second)
            pair.second.forEach { cards[it.value] = it }
        } ?: deal()
    }

    fun initialize(callback: () -> Unit) {
        viewModelScope.launch {
            resetGame()
            callback()
        }
    }
}

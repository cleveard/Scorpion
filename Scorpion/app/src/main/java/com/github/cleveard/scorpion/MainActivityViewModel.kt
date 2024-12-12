package com.github.cleveard.scorpion

import android.app.Application
import android.text.Highlights
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.db.State
import com.github.cleveard.scorpion.ui.Game
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.widgets.ScorpionGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val LOG_TAG: String = "CardLog"

class MainActivityViewModel(application: Application): AndroidViewModel(application), Dealer {
    private val cardDeck: MutableList<Card> = mutableListOf<Card>().apply {
        repeat(Game.CARD_COUNT) {
            add(Card(0, it, 0, 0, mutableIntStateOf(0)))
        }
    }

    private val cardGroups: MutableList<SnapshotStateList<Card?>> = mutableListOf()

    private val alert: MutableState<(@Composable () -> Unit)?> = mutableStateOf(null)
    override var showAlert: @Composable (() -> Unit)?
        get() = alert.value
        set(value) { alert.value = value }

    override val scope: CoroutineScope
        get() = viewModelScope

    override val game: Game = ScorpionGame(this)

    private var generation: Long = 0L
    private val changedCards: MutableMap<Int, CardEntity> = mutableMapOf()
    private val highlightCards: MutableMap<Int, CardEntity> = mutableMapOf()
    private var changedState: State? = null
    private var undoNesting: Int = 0

    override val cards: List<List<Card?>>
        get() = cardGroups

    override suspend fun deal() {
        IntArray(Game.CARD_COUNT) { it }.apply {
            shuffle()
            val pair = game.deal(this)
            CardDatabase.db.newGeneration(pair.first, pair.second, 0L)
            updateCards(cardDeck, pair.second)
            setCards(cardDeck)
            highlightCards.clear()
        }
    }

    override suspend fun gameVariants() {
        deal()
    }

    override fun findCard(cardValue: Int): Card = cardDeck[cardValue]

    override fun cardChanged(card: CardEntity) {
        val changed = cardDeck[card.value].let {
            it.group != card.group || it.position != card.position ||
                it.flags and Card.HIGHLIGHT_MASK.inv() != card.flags and Card.HIGHLIGHT_MASK.inv()
        }
        if (changed)
            changedCards[card.value] = card;
        else
            changedCards.remove(card.value)
        if ((card.flags and Card.HIGHLIGHT_MASK) != Card.HIGHLIGHT_NONE)
            highlightCards[card.value] = card
        else
            highlightCards.remove(card.value)
    }

    override fun stateChanged(state: State) {
        this.changedState = state
    }

    private suspend fun loadAndCheck(): Boolean {
        val pair = CardDatabase.db.loadGame()
        val error = run {
            if (pair == null)
                return@run "Can't reload the game"
            if (pair.first.generation != generation)
                return@run "Generation incorrect"
            if (pair.second.size != Game.CARD_COUNT)
                return@run "Size incorrect"
            val list = pair.second.toMutableList()
            list.sortBy { it.value }
            for (i in list.indices) {
                if (i != list[i].value)
                    return@run "Card $i value incorrect"
            }
            list.sortWith(compareBy({ it.group }, { it.position }))
            var last: Card? = null
            for (c in list) {
                game.isValid(pair.second, c, last)?.let {
                    return@run it
                }
                last = c
            }
            null
        }

        return if (error != null) {
            Log.d(LOG_TAG, "Generation: $generation - ${pair?.first?.generation}")
            for (i in 0 until cardDeck.size.coerceAtLeast(pair?.second?.size ?: 0)) {
                val c = this.cardDeck.getOrNull(i)
                val d = pair?.second?.getOrNull(i)
                val same = (d != null && c != null &&
                    (d.generation != c.generation || d.value != c.value || d.group != c.group ||
                        d.spread != c.spread || d.faceDown != c.faceDown)) ||
                    d !== c
                if (!same){
                    Log.d(LOG_TAG, "Card: $d != $c")
                } else
                    Log.d(LOG_TAG, "Card: $d")
            }
            showNewGameOrDismissAlert(R.string.database_inconsistency)
            false
        } else
            true
    }

    private fun clearHighlights() {
        for (c in highlightCards.values) {
            highlightCards[c.value] = c.copy(flags = c.flags and Card.HIGHLIGHT_MASK.inv())
        }
    }

    override suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T {
        var success = false
        if (undoNesting == 0)
            clearHighlights()
        ++undoNesting
        try {
            return action(generation + 1).also {
                success = true
            }
        } finally {
            if (undoNesting > 1) {
                --undoNesting
                success = true
            } else {
                undoNesting = 0
                try {
                    if (success) {
                        success = false
                        if (changedCards.isNotEmpty() || changedState != null) {
                            val list = cardDeck.toMutableList()
                            val newCards = mutableMapOf<Int, CardEntity>()
                            if (changedCards.isNotEmpty()) {
                                while (changedCards.isNotEmpty()) {
                                    updateCards(list, changedCards.values)
                                    newCards.putAll(changedCards)
                                    changedCards.clear()
                                    game.checkGameOver(list, generation + 1)
                                }
                            }
                            CardDatabase.db.newGeneration(changedState?: State(generation + 1, game::class.qualifiedName!!),
                                newCards.values, generation + 1)
                            ++generation
                            for (c in newCards.keys)
                                cardDeck[c] = list[c]
                            setCards(cardDeck)
                            loadAndCheck()
                        }

                        for (c in highlightCards.values) {
                            if (!changedCards.containsKey(c.value))
                                cardDeck[c.value] = cardDeck[c.value].from(c)
                        }
                        highlightCards.filter { (it.value.flags and Card.HIGHLIGHT_MASK) != 0 }
                        success = true
                    }
                } finally {
                    changedState = null
                    changedCards.clear()
                    if (!success)
                        highlightCards.clear()
                }
            }
        }
    }

    override suspend fun undo(): Pair<State, List<CardEntity>>? = CardDatabase.db.undo(generation)?.also {
        clearHighlights()
        updateGame(it.first, it.second)
        --generation
    }

    override suspend fun redo(): Pair<State, List<CardEntity>>? = CardDatabase.db.redo(generation + 1)?.also {
        clearHighlights()
        updateGame(it.first, it.second)
        game.checkGameOver(cardDeck, generation)
        ++generation
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun showNewGameOrDismissAlert(text: Int) {
        showAlert = {
            BasicAlertDialog(
                onDismissRequest = {},
                modifier = Modifier.background(AlertDialogDefaults.containerColor),
                properties = DialogProperties()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(text)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showAlert = null }
                        ) { Text( text = stringResource(R.string.dismiss)) }
                        Spacer(Modifier.width(24.dp))
                        TextButton(
                            onClick = {
                                showAlert = null
                                scope.launch {
                                    deal()
                                }
                            }
                        ) { Text( text = stringResource(R.string.new_game)) }
                    }
                }
            }
        }
    }

    private fun updateCards(list: MutableList<Card>, changed: Collection<CardEntity>) {
        for (c in changed) {
            list[c.value] = list[c.value].from(c)
        }
    }

    private fun updateGame(state: State, list: List<CardEntity>) {
        updateCards(cardDeck, list)
        setCards(cardDeck)
        highlightCards.clear()
    }

    private suspend fun resetGame() {
        CardDatabase.db.loadGame()?.let {pair ->
            generation = pair.first.generation
            setCards(pair.second)
            highlightCards.clear()
            pair.second.forEach { cardDeck[it.value] = it }
        } ?: deal()
        changedCards.clear()
        game.checkGameOver(cardDeck, generation)
    }

    private fun removeTails(start: Int, size: Int, end: Int) {
        if (start < end) {
            val list = cardGroups[start]
            repeat(list.size - size) { list.removeAt(list.lastIndex) }
            for (i in start + 1 until end.coerceAtMost(cardGroups.size)) {
                cardGroups[i].clear()
            }
        }
    }

    private fun setCards(cardList: List<Card>) {
        val sorted = cardList.sortedWith(compareBy({ it.group }, { it.position }))
        var lastGroup = 0
        var lastPosition = 0
        for (card in sorted) {
            if (card.group >= 0) {
                removeTails(lastGroup, lastPosition, card.group)
                lastGroup = card.group
                lastPosition = card.position + 1
                while (cardGroups.size <= card.group)
                    cardGroups.add(mutableStateListOf())
                val to = cardGroups[card.group]
                if (card.position >= to.size) {
                    while (to.size < card.position)
                        to.add(null)
                    to.add(card)
                } else {
                    val c = to[card.position]
                    if (c == null || c.generation != card.generation || c.value != card.value ||
                        c.faceDown != card.faceDown || c.spread != card.spread)
                        to[card.position] = card
                }
            }
        }
        removeTails(lastGroup, lastPosition, cardGroups.size)
    }

    fun initialize(callback: () -> Unit) {
        viewModelScope.launch {
            resetGame()
            callback()
        }
    }
}

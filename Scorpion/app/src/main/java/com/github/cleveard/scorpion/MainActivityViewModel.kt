package com.github.cleveard.scorpion

import android.app.Application
import android.os.Bundle
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.db.HighlightEntity
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Game
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.games.Scorpion.ScorpionGame
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

    private lateinit var _game: MutableState<Game>
    override val game: Game
        get() = _game.value

    private var generation: MutableState<Long> = mutableLongStateOf(0L)
    private var minGeneration: MutableState<Long> = mutableLongStateOf(0L)
    private var maxGeneration: MutableState<Long> = mutableLongStateOf(0L)
    private var undoCardFlips: MutableState<Boolean> = mutableStateOf(true)
    private val changedCards: MutableMap<Int, CardEntity> = mutableMapOf()
    private val highlightCards: MutableMap<Int, HighlightEntity> = mutableMapOf()
    private var undoNesting: Int = 0

    override val cards: List<List<Card?>>
        get() = cardGroups

    private lateinit var commonState: StateEntity
    private lateinit var gameState: StateEntity

    override suspend fun deal() {
        IntArray(Game.CARD_COUNT) { it }.apply {
            shuffle()
            val list = game.deal(this)
            CardDatabase.db.newGeneration(game.name, list, 0L)
            updateCards(cardDeck, list)
            setCards(cardDeck)
            highlightCards.clear()
            generation.value = 0L
            minGeneration.value = 0L
            maxGeneration.value = 0L
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
            changedCards[card.value] = card
        else
            changedCards.remove(card.value)
        val highlight = card.flags and Card.HIGHLIGHT_MASK
        highlightCards[card.value] = HighlightEntity(card.value, highlight)
    }

    override fun onStateChanged(state: StateEntity) {
        gameState.onBundleUpdated()
        viewModelScope.launch {
            CardDatabase.db.getStateDao().update(gameState.game, gameState.state)
        }
    }

    private suspend fun loadAndCheck(): Boolean {
        val pair = CardDatabase.db.loadGame(generation.value)
        val error = run {
            if (pair == null)
                return@run "Can't reload the game"
            if (pair.first.size != Game.CARD_COUNT)
                return@run "Size incorrect"
            val list = pair.first
            setHighlight(list, pair.second)
            list.sortBy { it.value }
            for (i in list.indices) {
                if (i != list[i].value)
                    return@run "Card $i value incorrect"
            }
            list.sortWith(compareBy({ it.group }, { it.position }))
            var last: Card? = null
            for (c in list) {
                game.isValid(pair.first, c, last)?.let {
                    return@run it
                }
                last = c
            }
            null
        }

        return if (error != null) {
            Log.d(LOG_TAG, "Generation: ${generation.value}")
            for (i in 0 until cardDeck.size.coerceAtLeast(pair?.first?.size ?: 0)) {
                val c = this.cardDeck.getOrNull(i)
                val d = pair?.first?.getOrNull(i)
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

    private fun clearHighlight(list: MutableList<Card>, clear: List<HighlightEntity>) {
        clear.forEach {
            if (!highlightCards.contains(it.card)) {
                list[it.card] = list[it.card].copy(highlight = Card.HIGHLIGHT_NONE)
            }
        }
    }

    private fun setHighlight(list: MutableList<Card>, set: Collection<HighlightEntity>) {
        set.forEach {
            list[it.card] = list[it.card].copy(highlight = it.highlight)
        }
    }

    override suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T {
        var success = false
        val highlightedCards = if (undoNesting == 0)
            highlightCards.values.toList().also {
                highlightCards.clear()
            }
        else
            null
        ++undoNesting
        try {
            return action(generation.value + 1).also {
                success = true
            }
        } finally {
            highlightedCards?.let {oldHighlights ->
                undoNesting = 0
                try {
                    if (success) {
                        CardDatabase.db.withTransaction {
                            success = false
                            CardDatabase.db.getHighlightDao().delete(oldHighlights.map { it.card })
                            CardDatabase.db.getHighlightDao().insert(highlightCards.values)
                            if (changedCards.isNotEmpty()) {
                                val list = cardDeck.toMutableList()
                                val newCards = mutableMapOf<Int, CardEntity>()
                                if (changedCards.isNotEmpty()) {
                                    while (changedCards.isNotEmpty()) {
                                        updateCards(list, changedCards.values)
                                        newCards.putAll(changedCards)
                                        changedCards.clear()
                                        game.checkGameOver(list, generation.value + 1)
                                    }
                                    CardDatabase.db.newGeneration(
                                        game.name,
                                        newCards.values,
                                        generation.value + 1
                                    )
                                    if (newCards.values.any { cardDeck[it.value].faceDown && (it.flags and Card.FACE_DOWN) == 0  }) {
                                        CardDatabase.db.getCardDao().clearUndo(generation.value)
                                        minGeneration.value = generation.value + 1
                                    }
                                }
                                ++generation.value
                                maxGeneration.value = generation.value
                                for (c in newCards.keys)
                                    cardDeck[c] = list[c]
                                setCards(cardDeck)
                                loadAndCheck()
                            }

                            clearHighlight(cardDeck, oldHighlights)
                            setHighlight(cardDeck, highlightCards.values)
                        }
                        success = true
                    }
                } finally {
                    changedCards.clear()
                    if (!success) {
                        highlightCards.clear()
                        highlightCards.putAll(oldHighlights.map { it.card to it })
                    }
                }
            }?: --undoNesting
        }
    }

    override fun canUndo(): Boolean = generation.value > minGeneration.value

    override fun canRedo(): Boolean = generation.value < maxGeneration.value

    override suspend fun undo(): List<CardEntity>? =
        if (generation.value > minGeneration.value) {
            CardDatabase.db.undo(game.name, generation.value)?.also {
                updateGame(it)
                --generation.value
            }
        } else
            null

    override suspend fun redo(): List<CardEntity>? =
        if (generation.value < maxGeneration.value) {
            CardDatabase.db.redo(game.name, generation.value + 1)?.also {
                updateGame(it)
                game.checkGameOver(cardDeck, generation.value)
                ++generation.value
            }
        } else
            null

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

    private fun updateGame(list: List<CardEntity>) {
        clearHighlight(cardDeck, highlightCards.values.toList().also { highlightCards.clear() })
        updateCards(cardDeck, list)
        setCards(cardDeck)
    }

    private suspend fun resetGame() {
        CardDatabase.db.getStateDao().get(game.name)?.let { state ->
            CardDatabase.db.getCardDao().minGeneration()?.let { dbMinGeneration ->
                CardDatabase.db.getCardDao().maxGeneration()?.let { dbMaxGeneration ->
                    if (state.generation in dbMinGeneration..dbMaxGeneration) {
                        CardDatabase.db.loadGame(state.generation)?.let { pair ->
                            setCards(pair.first)
                            pair.first.forEach { cardDeck[it.value] = it }
                            highlightCards.clear()
                            setHighlight(cardDeck, pair.second)
                            highlightCards.putAll(pair.second.map { it.card to it })
                            generation.value = state.generation
                            minGeneration.value = dbMinGeneration
                            maxGeneration.value = dbMaxGeneration
                        } ?: deal()
                        changedCards.clear()
                        game.checkGameOver(cardDeck, state.generation)
                    }
                }
            }
        }
    }

    private fun removeTails(startGroup: Int, size: Int, endGroup: Int) {
        if (startGroup < endGroup) {
            val list = cardGroups[startGroup]
            repeat(list.size - size) { list.removeAt(list.lastIndex) }
            for (i in startGroup + 1 until endGroup.coerceAtMost(cardGroups.size)) {
                cardGroups[i].clear()
            }
        }
    }

    private fun setCards(cardList: List<Card>) {
        fun SnapshotStateList<Card?>.setCard(position: Int, card: Card?) {
            while (position >= size)
                add(null)

            val c = this[position]
            if (card == null || c == null) {
                if (card != c)
                    this[position] = card
            } else if (card.generation != c.generation || card.value != c.value ||
                card.faceDown != c.faceDown || card.spread != c.spread)
                this[card.position] = card
        }
        while (cardGroups.size < game.groupCount)
            cardGroups.add(mutableStateListOf())
        val sorted = cardList.sortedWith(compareBy({ it.group }, { it.position }))
        var lastGroup = 0
        var lastPosition = 0
        for (card in sorted) {
            while (card.group >= cardGroups.size)
                cardGroups.add(mutableStateListOf())

            if (card.group > lastGroup) {
                removeTails(lastGroup, lastPosition, card.group)
                lastGroup = card.group
                lastPosition = 0
            }
            val to = cardGroups[card.group]
            while (lastPosition < card.position)
                to.setCard(lastPosition++, null)
            to.setCard(lastPosition++, card)
        }
        removeTails(lastGroup, lastPosition, cardGroups.size)
    }

    private suspend fun makeGame(className: String): Game? {
        val cardsValid = ::gameState.isInitialized
        if (!cardsValid)
            gameState = StateEntity(0L, "", Bundle())
        if (className == gameState.game)
            return game

        val oldGame = gameState
        gameState = CardDatabase.db.getStateDao().get(className)?: run {
            StateEntity(0L, className, Bundle()).also {
                CardDatabase.db.getStateDao().insert(it)
            }
        }

        try {
            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(Dealer::class.java, Bundle::class.java)

            try {
                val g = constructor.newInstance(this, gameState.bundle)
                if (g is Game)
                    return g
            } catch (_: Exception) {
            }
        } catch (_: ClassNotFoundException) {
        } catch (_: NoSuchMethodException) {
        } catch (_: SecurityException) {
        }

        gameState = oldGame
        return null
    }

    private suspend fun newGame(name: String): Game ? {
        return makeGame(name)?.also {g ->
            if (g.name != commonState.bundle.getString(GAME_NAME_KEY)) {
                commonState.bundle.putString(GAME_NAME_KEY, g.name)
                commonState.onBundleUpdated()
                CardDatabase.db.getStateDao().update(COMMON_STATE_NAME, commonState.state)
            }
        }
    }

    fun initialize(callback: () -> Unit) {
        viewModelScope.launch {
            CardDatabase.db.withTransaction {
                val stateDao = CardDatabase.db.getStateDao()
                CardGroup.preloadCards(getApplication())
                if (!::commonState.isInitialized) {
                    commonState = stateDao.get(COMMON_STATE_NAME) ?: run {
                        StateEntity(0L, COMMON_STATE_NAME, Bundle())
                    }
                }
                val name = commonState.bundle.getString(GAME_NAME_KEY, DEFAULT_GAME)
                val nextGame = newGame(name) ?: run {
                    newGame(DEFAULT_GAME)!!.also {
                        if (name != DEFAULT_GAME) {
                            CardDatabase.db.getCardDao().deleteAll()
                        }
                    }
                }
                if (::_game.isInitialized)
                    _game.value = nextGame
                else
                    _game = mutableStateOf(nextGame)
            }

            resetGame()
            callback()
        }
    }

    companion object {
        private const val GAME_NAME_KEY = "game_name"
        private val DEFAULT_GAME = ScorpionGame::class.qualifiedName!!
        private val COMMON_STATE_NAME = MainActivityViewModel::class.qualifiedName!!
    }
}

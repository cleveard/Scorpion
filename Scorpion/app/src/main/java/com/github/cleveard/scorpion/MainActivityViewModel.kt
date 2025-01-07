package com.github.cleveard.scorpion

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import coil3.Image
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.db.HighlightEntity
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.games.Game
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.TextRadioButton
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass

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

    private val games: MutableList<Game> = mutableListOf()

    private lateinit var _game: MutableState<Game>
    override val game: Game
        get() = _game.value
    private val _cardBack: MutableState<String> = mutableStateOf(BACK_ASSET_PATH + "red.svg")
    override val cardBackAssetPath: String
        get() = _cardBack.value
    private val _useSystemTheme: MutableState<Boolean> = mutableStateOf(false)
    override val useSystemTheme: Boolean
        get() = _useSystemTheme.value
    override val cardWidth: Int
        get() = MainActivityViewModel.cardWidth
    override val cardHeight: Int
        get() = MainActivityViewModel.cardHeight
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

    override fun cardFrontAssetPath(value: Int): String {
        return FRONT_ASSET_PATH + frontIds[value]
    }

    override suspend fun deal() {
        IntArray(Game.CARD_COUNT) { it }.apply {
            shuffle()
            val list = game.deal(this)
            CardDatabase.db.newGeneration(game.name, list, 0L)
            updateCards(cardDeck, list)
            cardGroups.forEach { it.clear() }
            setCards(cardDeck)
            highlightCards.clear()
            generation.value = 0L
            minGeneration.value = 0L
            maxGeneration.value = 0L
        }
    }

    override suspend fun gameVariants() {
        val gameList = games.map {
            Triple(
                getApplication<Application>().resources.getString(it.displayNameId),
                it,
                it.variantContent()?.apply { reset() }
            )
        }.sortedBy { it.first }
        val selectedGame = mutableStateOf(gameList.firstOrNull { it.second == game }!!)

        val value = showDialog(R.string.game_played, R.string.dismiss, R.string.new_game) {
            Text(
                stringResource(R.string.available_games),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Column(
                modifier = Modifier.border(width = 1.dp, Color.Black)
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
            ) {
                gameList.forEach {

                    TextRadioButton(
                        selectedGame.value.second == it.second,
                        it.second.displayNameId,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onChange = { selectedGame.value = it }
                    )
                }
            }

            selectedGame.value.third?.Content(modifier = Modifier)
        }

        if (value == R.string.new_game) {
            if (game != selectedGame.value.second) {
                commonState.bundle.putString(GAME_NAME_KEY, selectedGame.value.second.name)
                commonState.onBundleUpdated()
                CardDatabase.db.getStateDao().update(commonState.game, commonState.state)
                _game.value = selectedGame.value.second
            }
            selectedGame.value.third?.onAccept()
            deal()
        } else
            selectedGame.value.third?.onDismiss()
    }

    override suspend fun settings() {
        val selectedPath = mutableStateOf(cardBackAssetPath)
        val undoFlips = mutableStateOf(undoCardFlips.value)
        val systemTheme = mutableStateOf(useSystemTheme)
        val gameContent: DialogContent? = game.settingsContent()
        gameContent?.reset()
        val value = showDialog(R.string.settings, R.string.dismiss, R.string.accept) {
            val width = Dp(.4f * 160.0f)
            val height = width * (cardHeight.toFloat() / cardWidth.toFloat())
            Text(
                stringResource(R.string.card_back_image),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(height)
            ) {
                AsyncImage(
                    selectedPath.value,
                    contentDescription = "",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight()
                        .padding(horizontal = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    for (n in backIds) {
                        item {
                            val p = BACK_ASSET_PATH + n
                            val filter = if (p == selectedPath.value)
                                selectedFilter
                            else
                                null
                            AsyncImage(
                                p,
                                contentDescription = "",
                                colorFilter = filter,
                                modifier = Modifier.size(width, height)
                                    .clickable {
                                        selectedPath.value = p
                                    }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp)
            )

            TextSwitch(
                undoFlips.value,
                R.string.allow_undo_for_flips,
                onChange = { undoFlips.value = it }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()

                TextSwitch(
                    systemTheme.value,
                    R.string.use_system_theme,
                    onChange = { systemTheme.value = it }
                )
            }

            gameContent?.Content(modifier = Modifier)
        }

        if (value == R.string.accept) {
            var update = updateState(_cardBack, selectedPath, commonState.bundle) { putString(CARD_BACK_IMAGE, it) }
            update = updateState(undoCardFlips, undoFlips, commonState.bundle) { putBoolean(ALLOW_UNDO_FOR_FLIPS, it) } || update
            update = updateState(_useSystemTheme, systemTheme, commonState.bundle ) { putBoolean(USE_SYSTEM_THEME, it) } || update
            if (update) {
                commonState.onBundleUpdated()
                CardDatabase.db.getStateDao().update(commonState.game, commonState.state)
            }
            gameContent?.onAccept()
        } else
            gameContent?.onDismiss()
    }

    private fun <T> updateState(current: MutableState<T>, changed: MutableState<T>, bundle: Bundle, update: Bundle.(value: T) -> Unit): Boolean {
        return (current.value != changed.value).also {
            if (it) {
                current.value = changed.value
                bundle.update(changed.value)
            }
        }
    }

    override fun findCard(cardValue: Int): Card = cardDeck[cardValue]

    override fun cardChanged(card: CardEntity): Int {
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
        return changedCards.size
    }

    override suspend fun onStateChanged(state: StateEntity) {
        state.onBundleUpdated()
        CardDatabase.db.getStateDao().update(state.game, state.state)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun showDialog(
        title: Int,
        vararg buttons: Int,
        content: @Composable () -> Unit
    ): Int {
        return try {
            suspendCoroutine {resume ->
                showAlert = {
                    BasicAlertDialog(
                        onDismissRequest = { resume.resume(0) },
                        modifier = Modifier.clip(RoundedCornerShape(40.dp))
                            .background(AlertDialogDefaults.containerColor),
                        properties = DialogProperties()
                    ) {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp)
                        ) {
                            if (title != 0) {
                                Text(
                                    stringResource(title),
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            content()

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(fraction = 0.9f),
                                horizontalArrangement = Arrangement.End
                            ) {
                                for (b in buttons) {
                                    Spacer(Modifier.width(24.dp))
                                    TextButton(
                                        onClick = { resume.resume(b) }
                                    ) {
                                        Text(text = stringResource(b))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            showAlert = null
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
            showNewGameOrDismissAlert(R.string.database_inconsistency, R.string.error)
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
                            var cardFlip = false
                            if (changedCards.isNotEmpty()) {
                                val list = ArrayList<Card>().apply { addAll(cardDeck) }
                                val newCards = mutableMapOf<Int, CardEntity>()
                                if (changedCards.isNotEmpty()) {
                                    while (changedCards.isNotEmpty()) {
                                        cardFlip = cardFlip || changedCards.values.any { cardDeck[it.value].faceDown && (it.flags and Card.FACE_DOWN) == 0  }
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
                                    if (!undoCardFlips.value && cardFlip) {
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

    override suspend fun showNewGameOrDismissAlert(text: Int, title:Int, plural: Int?, vararg args: Any) {
        viewModelScope.launch {
            val result = showDialog(title, R.string.dismiss, R.string.new_game) {
                Text(
                    text = if (plural == null)
                        stringResource(text, *args)
                    else
                        pluralStringResource(text, plural, *args)
                )
            }

            if (result == R.string.new_game)
                deal()
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
                            cardGroups.forEach { it.clear() }
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
                    } else
                        null
                }
            }
        }?: deal()
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

    private suspend fun makeGame(clazz: KClass<out Game>): Game? {
        val className = clazz.qualifiedName!!
        val newState = CardDatabase.db.getStateDao().get(className)?: run {
            StateEntity(0L, className, Bundle()).also {
                CardDatabase.db.getStateDao().insert(it)
            }
        }

        try {
            val constructor = clazz.java.getConstructor(Dealer::class.java, StateEntity::class.java)

            try {
                return constructor.newInstance(this, newState)
            } catch (_: Exception) {
            }
        } catch (_: ClassNotFoundException) {
        } catch (_: NoSuchMethodException) {
        } catch (_: SecurityException) {
        }

        return null
    }

    private fun setState(bundle: Bundle) {
        _cardBack.value = bundle.getString(CARD_BACK_IMAGE, BACK_ASSET_PATH + "red.svg")
        undoCardFlips.value = bundle.getBoolean(ALLOW_UNDO_FOR_FLIPS, false)
        _useSystemTheme.value = bundle.getBoolean(USE_SYSTEM_THEME, false)
    }

    fun initialize(callback: () -> Unit) {
        viewModelScope.launch {
            CardDatabase.db.withTransaction {
                Game::class.sealedSubclasses.forEach {clazz ->
                    makeGame(clazz)?.let {
                        games.add(it)
                    }
                }

                if (games.isEmpty())
                    throw IllegalStateException("No game to be played")

                val stateDao = CardDatabase.db.getStateDao()
                preloadCards(getApplication())
                if (!::commonState.isInitialized) {
                    commonState = stateDao.get(COMMON_STATE_NAME) ?: run {
                        StateEntity(0L, COMMON_STATE_NAME, Bundle()).also {
                            CardDatabase.db.getStateDao().insert(it)
                        }
                    }
                }
                setState(commonState.bundle)
                val nextGame = commonState.bundle.getString(GAME_NAME_KEY)?.let {name ->
                    games.firstOrNull { it.name == name }
                }?: games.firstOrNull { it.name.endsWith(DEFAULT_GAME) }
                ?: games[0]
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
        private const val CARD_BACK_IMAGE = "card_back_image"
        private const val ALLOW_UNDO_FOR_FLIPS = "allow_undo_for_card_flips"
        private const val USE_SYSTEM_THEME = "use_system_theme"
        private const val DEFAULT_GAME = ".ScorpionGame"
        private val COMMON_STATE_NAME = MainActivityViewModel::class.qualifiedName!!
        var cardWidth: Int = 234
            private set
        var cardHeight: Int = 333
            private set
        private const val ASSET_PATH = "file:///android_asset/"
        private const val FRONT_ASSET_PATH = ASSET_PATH + "cards/fronts/"
        private const val BACK_ASSET_PATH = ASSET_PATH + "cards/backs/"
        private val selectedFilter = BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply)

        /** Drawable ids of the card fronts */
        private val frontIds: List<String> = listOf(
            "spades_ace.svg",
            "spades_2.svg",
            "spades_3.svg",
            "spades_4.svg",
            "spades_5.svg",
            "spades_6.svg",
            "spades_7.svg",
            "spades_8.svg",
            "spades_9.svg",
            "spades_10.svg",
            "spades_jack.svg",
            "spades_queen.svg",
            "spades_king.svg",
            "hearts_ace.svg",
            "hearts_2.svg",
            "hearts_3.svg",
            "hearts_4.svg",
            "hearts_5.svg",
            "hearts_6.svg",
            "hearts_7.svg",
            "hearts_8.svg",
            "hearts_9.svg",
            "hearts_10.svg",
            "hearts_jack.svg",
            "hearts_queen.svg",
            "hearts_king.svg",
            "clubs_ace.svg",
            "clubs_2.svg",
            "clubs_3.svg",
            "clubs_4.svg",
            "clubs_5.svg",
            "clubs_6.svg",
            "clubs_7.svg",
            "clubs_8.svg",
            "clubs_9.svg",
            "clubs_10.svg",
            "clubs_jack.svg",
            "clubs_queen.svg",
            "clubs_king.svg",
            "diamonds_ace.svg",
            "diamonds_2.svg",
            "diamonds_3.svg",
            "diamonds_4.svg",
            "diamonds_5.svg",
            "diamonds_6.svg",
            "diamonds_7.svg",
            "diamonds_8.svg",
            "diamonds_9.svg",
            "diamonds_10.svg",
            "diamonds_jack.svg",
            "diamonds_queen.svg",
            "diamonds_king.svg",
        )

        private val backIds: List<String> = listOf(
            "abstract.svg",
            "abstract_clouds.svg",
            "abstract_scene.svg",
            "astronaut.svg",
            "blue.svg",
            "blue2.svg",
            "cars.svg",
            "castle.svg",
            "fish.svg",
            "frog.svg",
            "red.svg",
            "red2.svg"
        )

        suspend fun preloadCards(context: Context) {
            for (name in frontIds) {
                suspendCoroutine {
                    val request = ImageRequest.Builder(context)
                        .data(FRONT_ASSET_PATH + name)
                        .target(object: coil3.target.Target {
                            override fun onError(error: Image?) {
                                throw IllegalArgumentException("Missing asset ${FRONT_ASSET_PATH + name}")
                            }

                            override fun onSuccess(result: Image) {
                                it.resume(Unit)
                            }
                        })
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
            for (name in backIds) {
                suspendCoroutine {
                    val request = ImageRequest.Builder(context)
                        .data(BACK_ASSET_PATH + name)
                        .target(object: coil3.target.Target {
                            override fun onError(error: Image?) {
                                throw IllegalArgumentException("Missing asset ${BACK_ASSET_PATH + name}")
                            }

                            override fun onSuccess(result: Image) {
                                cardWidth = result.width
                                cardHeight = result.height
                                it.resume(Unit)
                            }
                        })
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }
}

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

/**
 * The view model for the main activity
 * @param application The application context
 * The view model acts as the dealer for the application. It is also responsible
 * for handling all of the database transactions
 */
class MainActivityViewModel(application: Application): AndroidViewModel(application), Dealer {
    /**
     * A list of the current cards in card value order
     * It is used to look up cards by value
     */
    private val cardDeck: MutableList<Card> = mutableListOf<Card>().apply {
        // Initialized the cards in the list
        repeat(Game.CARD_COUNT) {
            add(Card(0, it, 0, 0, 0))
        }
    }

    /**
     * The cards ordered in their groups
     * Note that the groups are state lists so the display is updated as they are changed
     */
    private val cardGroups: MutableList<SnapshotStateList<Card?>> = mutableListOf()

    /** Mutable state for the current alert dialog */
    private val alert: MutableState<(@Composable () -> Unit)?> = mutableStateOf(null)
    /** @inheritDoc */
    override var showAlert: @Composable (() -> Unit)?
        get() = alert.value
        set(value) { alert.value = value }

    /** @inheritDoc */
    override val scope: CoroutineScope
        get() = viewModelScope

    /**
     * List of all games in the application
     * All of the games are instantiated during initialization. The current game
     * is taken from this list.
     */
    private val games: MutableList<Game> = mutableListOf()

    /** Mutable state for the current game */
    private lateinit var _game: MutableState<Game>
    /** @inheritDoc */
    override val game: Game
        get() = _game.value
    /** Mutable state for the card back image asset path */
    private val _cardBack: MutableState<String> = mutableStateOf(BACK_ASSET_PATH + "red.svg")
    /** @inheritDoc */
    override val cardBackAssetPath: String
        get() = _cardBack.value
    /** Mutable state for the useSystemTheme switch */
    private val _useSystemTheme: MutableState<Boolean> = mutableStateOf(false)
    /** @inheritDoc */
    override val useSystemTheme: Boolean
        get() = _useSystemTheme.value
    /** @inheritDoc */
    override val cardWidth: Int
        get() = MainActivityViewModel.cardWidth
    /** @inheritDoc */
    override val cardHeight: Int
        get() = MainActivityViewModel.cardHeight
    /** Mutable state for the current game generation */
    private var generation: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the smallest valid game generation */
    private var minGeneration: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the largest valid game generation */
    private var maxGeneration: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the switch to allow card flips to be undone */
    private var undoCardFlips: MutableState<Boolean> = mutableStateOf(true)
    /** A map of card values to changed cards */
    private val changedCards: MutableMap<Int, Card> = mutableMapOf()
    /** A map of card values to highlight values for cards */
    private val highlightCards: MutableMap<Int, HighlightEntity> = mutableMapOf()
    /** The withUndo nesting level */
    private var undoNesting: Int = 0

    /** @inheritDoc */
    override val cards: List<List<Card?>>
        get() = cardGroups

    /** The state object maintained for the application */
    private lateinit var commonState: StateEntity

    /** @inheritDoc */
    override fun cardFrontAssetPath(value: Int): String {
        return FRONT_ASSET_PATH + frontIds[value]
    }

    /** @inheritDoc */
    override suspend fun deal() {
        // Create an integer array with all of the card values in it
        IntArray(Game.CARD_COUNT) { it }.apply {
            // Mix up the values
            shuffle()
            // Let the game deal the values into cards
            val list = game.deal(this)
            // Add the cards as the first generation
            CardDatabase.db.newGeneration(game.name, list, 0L)
            // Update the cards in the current card list
            updateCards(cardDeck, list)
            // Clear all of the card groups
            cardGroups.forEach { it.clear() }
            // Put the cards into the card groups
            setCards(cardDeck)
            // Clear any highlights
            highlightCards.clear()
            CardDatabase.db.getHighlightDao().clear()
            // Set the current, minimum and maximum generations
            generation.value = 0L
            minGeneration.value = 0L
            maxGeneration.value = 0L
        }
    }

    /** @inheritDoc */
    override suspend fun gameVariants() {
        // Map the games to triples with the display name, the game,
        // and the games variant dialog content and sort it in display name order.
        // Reset the each game content to the current settings.
        // This list is what drives the game selection
        val gameList = games.map {
            Triple(
                getApplication<Application>().resources.getString(it.displayNameId),
                it,
                it.variantContent()?.apply { reset() }
            )
        }.sortedBy { it.first }
        // Get a mutable state for the currently selected game
        val selectedGame = mutableStateOf(gameList.firstOrNull { it.second == game }!!)

        // Show the variant dialog and get the button used to close it
        val value = showDialog(R.string.game_played, R.string.dismiss, R.string.new_game) {
            // A header for the available games
            Text(
                stringResource(R.string.available_games),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            // Available games is a list of radio buttons with text
            Column(
                modifier = Modifier.border(width = 1.dp, Color.Black)
                    .padding(horizontal = 4.dp)
                    .fillMaxWidth()
            ) {
                // Show all of the available games
                gameList.forEach {

                    TextRadioButton(
                        selectedGame.value.second == it.second,
                        it.second.displayNameId,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onChange = { selectedGame.value = it }
                    )
                }
            }

            // Show the game content here
            selectedGame.value.third?.Content(modifier = Modifier)
        }

        // If the user pressed new game, then we accept the values and deal a new game
        if (value == R.string.new_game) {
            // If the game changes, then update the database and set it
            if (game != selectedGame.value.second) {
                commonState.bundle.putString(GAME_NAME_KEY, selectedGame.value.second.name)
                commonState.onBundleUpdated()
                CardDatabase.db.getStateDao().update(commonState.game, commonState.state)
                _game.value = selectedGame.value.second
            }
            // Let the game content know the current values were accepted
            selectedGame.value.third?.onAccept()
            // Deal a new game
            deal()
        } else
            selectedGame.value.third?.onDismiss()   // Let game know dialog was dismissed
    }

    /** @inheritDoc */
    override suspend fun settings() {
        // Mutable state objects for values changed in the dialog
        // The selected card back image
        val selectedPath = mutableStateOf(cardBackAssetPath)
        // The selected undo card flips value
        val undoFlips = mutableStateOf(undoCardFlips.value)
        // The selected use system theme value
        val systemTheme = mutableStateOf(useSystemTheme)
        // The current game settings content
        val gameContent: DialogContent? = game.settingsContent()
        // Reset the content to the current settings
        gameContent?.reset()

        // Show the settings dialog
        val value = showDialog(R.string.settings, R.string.dismiss, R.string.accept) {
            // Width and height for the card backs
            val width = Dp(.4f * 160.0f)
            val height = width * (cardHeight.toFloat() / cardWidth.toFloat())
            // Header for the card backs
            Text(
                stringResource(R.string.card_back_image),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            // The card backs are in a row. The start image is the current selection
            // The other images are the ones to be selected.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(height)
            ) {
                // The current selected card back image
                AsyncImage(
                    selectedPath.value,
                    contentDescription = "",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight()
                        .padding(horizontal = 4.dp)
                )
                // The card make images to be selected
                LazyRow(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    for (n in backIds) {
                        // The item for a card back image
                        item {
                            val p = BACK_ASSET_PATH + n
                            val filter = if (p == selectedPath.value)
                                selectedFilter      // Distinguish which one is selected
                            else
                                null
                            // The card back image
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

            // Horizontal divider before check boxes
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp)
            )

            // Checkbox to allow card flips to undo
            TextSwitch(
                undoFlips.value,
                R.string.allow_undo_for_flips,
                onChange = { undoFlips.value = it }
            )

            // If the system support personal colors, then let the user
            // decide whether or not to use them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()

                TextSwitch(
                    systemTheme.value,
                    R.string.use_system_theme,
                    onChange = { systemTheme.value = it }
                )
            }

            // The game settings go here
            gameContent?.Content(modifier = Modifier)
        }

        // If the settings are accepted
        if (value == R.string.accept) {
            // Update the bundle for each setting and note whether it changed
            var update = updateState(_cardBack, selectedPath, commonState.bundle) { putString(CARD_BACK_IMAGE, it) }
            update = updateState(undoCardFlips, undoFlips, commonState.bundle) { putBoolean(ALLOW_UNDO_FOR_FLIPS, it) } || update
            update = updateState(_useSystemTheme, systemTheme, commonState.bundle ) { putBoolean(USE_SYSTEM_THEME, it) } || update
            if (update) {
                // The bundle changed, update the database
                commonState.onBundleUpdated()
                CardDatabase.db.getStateDao().update(commonState.game, commonState.state)
            }
            // Let the game know the settings were accepted
            gameContent?.onAccept()
        } else
            gameContent?.onDismiss()    // Let the game know the settings were dismissed
    }

    /**
     * Helper function to update the bundle
     */
    private fun <T> updateState(current: MutableState<T>, changed: MutableState<T>, bundle: Bundle, update: Bundle.(value: T) -> Unit): Boolean {
        // Let the caller know whether something changed
        return (current.value != changed.value).also {
            if (it) {
                // Update view model and database
                current.value = changed.value
                bundle.update(changed.value)
            }
        }
    }

    /** @inheritDoc */
    override fun findCard(cardValue: Int): Card = cardDeck[cardValue]

    /** @inheritDoc */
    override fun cardChanged(card: Card): Int {
        // Did anything other than the highlight change
        val changed = cardDeck[card.value].let {
            it.group != card.group || it.position != card.position ||
                it.flags and Card.HIGHLIGHT_MASK.inv() != card.flags and Card.HIGHLIGHT_MASK.inv()
        }
        if (changed)
            changedCards[card.value] = card     // Yes, Update changedCards map
        else
            changedCards.remove(card.value)     // No, card may have been reverted
        // Make sure highlight are handled
        val highlight = card.flags and Card.HIGHLIGHT_MASK
        if (highlight != Card.HIGHLIGHT_NONE)
            highlightCards[card.value] = HighlightEntity(card.value, highlight)
        return changedCards.size
    }

    /** @inheritDoc */
    override suspend fun onStateChanged(state: StateEntity) {
        state.onBundleUpdated()
        CardDatabase.db.getStateDao().update(state.game, state.state)
    }

    /** @inheritDoc */
    @OptIn(ExperimentalMaterial3Api::class)
    override suspend fun showDialog(
        title: Int,
        vararg buttons: Int,
        content: @Composable () -> Unit
    ): Int {
        return try {
            // Suspend the coroutine until dialog completes
            suspendCoroutine {resume ->
                // Set the dialog content to show it
                showAlert = {
                    // We use the basic alert dialog for our dialogs
                    BasicAlertDialog(
                        onDismissRequest = { resume.resume(0) },
                        modifier = Modifier.clip(RoundedCornerShape(40.dp))
                            .background(AlertDialogDefaults.containerColor),
                        properties = DialogProperties()
                    ) {
                        // The content of the dialog is put into a column
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp)
                        ) {
                            // If we have a title, then show it with a horizontal divider
                            if (title != 0) {
                                Text(
                                    stringResource(title),
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            // Show the rest of the dialog content
                            content()

                            // Put the buttons into a row spaced from the dialog content
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(fraction = 0.9f),
                                horizontalArrangement = Arrangement.End
                            ) {
                                for (b in buttons) {
                                    Spacer(Modifier.width(24.dp))
                                    TextButton(
                                        // When a button is clicked, resume the coroutine
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
            // Stop showing the dialog
            showAlert = null
        }
    }

    /**
     * Validity check for the database
     * This method loads all of the cards for a generation
     * and makes sure the results are valid.
     */
    private suspend fun loadAndCheck(): Boolean {
        // Load the cards and highlights for the generation
        val pair = CardDatabase.db.loadGame(generation.value)
        // Validate the database and return an error string if the is a problem
        val error = run {
            // Make sure we got something
            if (pair == null)
                return@run "Can't reload the game"
            // Make sure the card count is correct
            if (pair.first.size != Game.CARD_COUNT)
                return@run "Size incorrect"

            // Set the highlights on the cards
            val list = pair.first
            setHighlight(list, pair.second)
            // Sort the list by card value and make sure
            // all of the cards are there
            list.sortBy { it.value }
            for (i in list.indices) {
                if (i != list[i].value)
                    return@run "Card $i value incorrect"
            }
            // Sort the list by group and position
            list.sortWith(compareBy({ it.group }, { it.position }))
            var last: Card? = null
            for (c in list) {
                // Call the game validity checker to make sure
                // everything is OK
                game.isValid(pair.first, c, last)?.let {
                    return@run it
                }
                last = c
            }
            // No errors return null
            null
        }

        // If there was an error, dump information to the log
        return if (error != null) {
            // Dump the generation
            Log.d(LOG_TAG, "Generation: ${generation.value}")
            for (i in 0 until cardDeck.size.coerceAtLeast(pair?.first?.size ?: 0)) {
                // Dump each card in the in memory list and in the loaded list
                val c = this.cardDeck.getOrNull(i)
                val d = pair?.first?.getOrNull(i)
                // Compare the cards from each table, ignoring highlight
                val same = (d != null && c != null &&
                    (d.generation != c.generation || d.value != c.value || d.group != c.group ||
                        d.spread != c.spread || d.faceDown != c.faceDown)) ||
                    d !== c
                // Log both cards if they are different
                if (!same){
                    Log.d(LOG_TAG, "Card: $d != $c")
                } else
                    Log.d(LOG_TAG, "Card: $d")  // Or just the loaded card if they are the same
            }
            // Show an alert and to start a new game
            showNewGameOrDismissAlert(R.string.database_inconsistency, R.string.error)
            false       // Return error
        } else
            true        // Return all ok
    }

    /**
     * Clear highlight from the list of cards
     * @param list The list of the cards to clear
     * @param clear The highlight entities to clear
     */
    private fun clearHighlight(list: MutableList<Card>, clear: List<HighlightEntity>) {
        clear.forEach {
            // Check to see if we are changing the highlight instead of removing
            if (!highlightCards.contains(it.card)) {
                // We are removing the highlight. Set the card in the list
                val next = list[it.card].copy(highlight = Card.HIGHLIGHT_NONE)
                list[it.card] = next
                // And in the card groups
                cardGroups[next.group][next.position] = next
            }
        }
    }

    /**
     * Set the highlights in the list of cards
     * @param list The list of cards to set
     * @param set The highlight to set
     */
    private fun setHighlight(list: MutableList<Card>, set: Collection<HighlightEntity>) {
        set.forEach {
            // Set the highlight in the list
            val next = list[it.card].copy(highlight = it.highlight)
            list[it.card] = next
            // And in the card groups
            cardGroups[next.group][next.position] = next
        }
    }

    /** @inheritDoc */
    override suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T {
        // Success is set to true, if the action completes
        var success = false
        // Get the current highlights, if we aren't nesting
        val highlightedCards = if (undoNesting == 0)
            highlightCards.values.toList().also {
                highlightCards.clear()
            }
        else
            null
        ++undoNesting       // Mark the nesting level
        try {
            // Execute the action
            return action(generation.value + 1).also {
                success = true
            }
        } finally {
            // Done, whether successful or not
            // highlightedCards is not null only when undo nesting is 0
            highlightedCards?.let {oldHighlights ->
                undoNesting = 0     // Make sure undo nesting is 0
                try {
                    // If the action succeeded, then update the database
                    if (success) {
                        // Make the full database update a single transaction
                        CardDatabase.db.withTransaction {
                            // Now success will indicate whether the database update worked
                            success = false
                            // Delete old highlights from the database
                            CardDatabase.db.getHighlightDao().delete(oldHighlights.map { it.card })
                            // Add the new highlights
                            CardDatabase.db.getHighlightDao().insert(highlightCards.values)
                            // Check for card flips, to clear undo if we don't allow undo of card flips
                            var cardFlip = false
                            // Where there any changes, other than highlights
                            if (changedCards.isNotEmpty()) {
                                // Yes, we apply the changes to a new list as we go
                                val list = ArrayList<Card>().apply { addAll(cardDeck) }
                                // More cards may change as we do this, and we keep all of the changes here
                                val newCards = mutableMapOf<Int, Card>()

                                // The game can change cards while checking for game over
                                // So we loop and let the game check until it doesn't change anything
                                while (changedCards.isNotEmpty()) {
                                    // Check for a card flip
                                    cardFlip = cardFlip || changedCards.values.any { cardDeck[it.value].faceDown && (it.flags and Card.FACE_DOWN) == 0  }
                                    // Update the cards in list with the changes
                                    updateCards(list, changedCards.values)
                                    // Add the changed cards to the full set
                                    newCards.putAll(changedCards)
                                    // Clear the changed cards
                                    changedCards.clear()
                                    // Let the game check for game over. This may change more cards
                                    game.checkGameOver(list, generation.value + 1)
                                }

                                // Update the new generation in the database
                                CardDatabase.db.newGeneration(
                                    game.name,
                                    newCards.values,
                                    generation.value + 1
                                )
                                // If there was a card flip and we don't allow it to be undone
                                if (!undoCardFlips.value && cardFlip) {
                                    // Clear undo so the flip can't be undone.
                                    CardDatabase.db.getCardDao().clearUndo(generation.value)
                                    minGeneration.value = generation.value + 1
                                }

                                // Bump the generation and max generation
                                ++generation.value
                                maxGeneration.value = generation.value
                                // Set the new cards in the main card list
                                for (c in newCards.keys)
                                    cardDeck[c] = list[c]
                                // And in the card groups
                                setCards(cardDeck)
                                // Validate the database
                                loadAndCheck()
                            }

                            // Make highlights are cleared and set
                            clearHighlight(cardDeck, oldHighlights)
                            setHighlight(cardDeck, highlightCards.values)
                        }
                        // Done - success
                        success = true
                    }
                } finally {
                     // Make sure changeCards is empty
                    changedCards.clear()
                    // If we weren't successful, then put the highlights back
                    if (!success) {
                        highlightCards.clear()
                        highlightCards.putAll(oldHighlights.map { it.card to it })
                    }
                }
            }?: --undoNesting       // Leaving a nested undo
        }
    }

    /** @inheritDoc */
    override fun canUndo(): Boolean = generation.value > minGeneration.value

    /** @inheritDoc */
    override fun canRedo(): Boolean = generation.value < maxGeneration.value

    /** @inheritDoc */
    override suspend fun undo(): List<Card>? =
        // Make sure we can undo
        if (generation.value > minGeneration.value) {
            // Get the cards to change
            CardDatabase.db.undo(game.name, generation.value)?.also {
                // Update the came
                updateGame(it)
                // Bump generation
                --generation.value
            }
        } else
            null

    /** @inheritDoc */
    override suspend fun redo(): List<Card>? =
        // Make sure we can redo
        if (generation.value < maxGeneration.value) {
            // Get the cards to change
            CardDatabase.db.redo(game.name, generation.value + 1)?.also {
                // Update the game
                updateGame(it)
                // Check for game over
                game.checkGameOver(cardDeck, generation.value)
                // Bump the generation
                ++generation.value
            }
        } else
            null

    /** @inheritDoc */
    override suspend fun showNewGameOrDismissAlert(text: Int, title:Int, plural: Int?, vararg args: Any) {
        // Launch a coroutine to show the dialog. We don't want this coroutine to wait for the result
        viewModelScope.launch {
            // Show the alert
            val result = showDialog(title, R.string.dismiss, R.string.new_game) {
                Text(
                    text = if (plural == null)
                        stringResource(text, *args)
                    else
                        pluralStringResource(text, plural, *args)
                )
            }

            // Deal a new game if needed
            if (result == R.string.new_game)
                deal()
        }
    }

    /**
     * Update the cards in the list
     * @param list The list to update
     * @param changed The cards that changed
     */
    private fun updateCards(list: MutableList<Card>, changed: Collection<Card>) {
        // Set the cards in the list
        for (c in changed) {
            list[c.value] = c.copy()
        }
    }

    /**
     * Update the game
     * @param list The list of cards to update
     */
    private fun updateGame(list: List<Card>) {
        // Clear the highlights
        clearHighlight(cardDeck, highlightCards.values.toList().also { highlightCards.clear() })
        // Update the cards in the card list
        updateCards(cardDeck, list)
        // Update the cards in the card groups
        setCards(cardDeck)
    }

    /**
     * Reset the game from the database
     */
    private suspend fun resetGame() {
        // We need the game state, the min and max generations and the cards
        CardDatabase.db.getStateDao().get(game.name)?.let { state ->
            CardDatabase.db.getCardDao().minGeneration()?.let { dbMinGeneration ->
                CardDatabase.db.getCardDao().maxGeneration()?.let { dbMaxGeneration ->
                    if (state.generation in dbMinGeneration..dbMaxGeneration) {
                        CardDatabase.db.loadGame(state.generation)?.let { pair ->
                            // Clear current cards in the groups
                            cardGroups.forEach { it.clear() }
                            // Set the card groups from database
                            setCards(pair.first)
                            // Set the list of cards from the database
                            pair.first.forEach { cardDeck[it.value] = it }
                            // Clear highlight list
                            highlightCards.clear()
                            // Set the highlights from the database
                            setHighlight(cardDeck, pair.second)
                            // Add the highlights to the highlight list
                            highlightCards.putAll(pair.second.map { it.card to it })
                            // Set the current, min and max generations
                            generation.value = state.generation
                            minGeneration.value = dbMinGeneration
                            maxGeneration.value = dbMaxGeneration
                        } ?: deal()         // Something didn't work, deal a new game
                        // Clear all card changes
                        changedCards.clear()
                        // Check for game over
                        game.checkGameOver(cardDeck, state.generation)
                    } else
                        null
                }
            }
        }?: deal()          // Something didn't work deal a new game
    }

    /**
     * Clear groups as we are adding cards to them.
     * @param startGroup The group to start clearing
     * @param size The new size of the startGroup group
     * @param endGroup The group to clear to
     * This is called from setCards when the group changes and after all cards are set.
     * setCards processes cards in group, position order, so when the group changes,
     * we need to delete any cards in the group past where are have set, and we need to
     * clear any groups between where are have set, and where we will start setting.
     */
    private fun removeTails(startGroup: Int, size: Int, endGroup: Int) {
        // Sanity check, should always be true
        if (startGroup < endGroup) {
            val list = cardGroups[startGroup]
            // Clear the cards from startGroup past where we have set
            repeat(list.size - size) { list.removeAt(list.lastIndex) }
            // Clear the groups between start group and the next group we are setting
            for (i in startGroup + 1 until endGroup.coerceAtMost(cardGroups.size)) {
                cardGroups[i].clear()
            }
        }
    }

    /**
     * Set the cards in the card groups
     * @param cardList The full list of all of the cards
     * This method only sets cards that have changed to minimize
     * recomposes
     */
    private fun setCards(cardList: List<Card>) {
        // Put a card or null into a card group
        // Position is passed separately, because card may be null.
        fun SnapshotStateList<Card?>.setCard(position: Int, card: Card?) {
            // Pad end of list with nulls if needed
            while (position >= size)
                add(null)

            // Get value to compare
            val c = this[position]
            // If either c or card is null, then we will replace the value
            // if c != card. If both are not null, then we only compare
            // generation, value, faceDown and spread. The group and position
            // must be the same, because they are at the same position in
            // the same group. We also don't change cards that differ in highlight
            // because that is handled elsewhere
            if (card == null || c == null) {
                if (card != c)
                    this[position] = card
            } else if (card.generation != c.generation || card.value != c.value ||
                card.faceDown != c.faceDown || card.spread != c.spread)
                this[card.position] = card
        }

        // First make sure we have enough groups for the game.
        while (cardGroups.size < game.groupCount)
            cardGroups.add(mutableStateListOf())
        // Sort the cards by group and position
        val sorted = cardList.sortedWith(compareBy({ it.group }, { it.position }))
        var lastGroup = 0           // Track last group set
        var lastPosition = 0        // and the last position set
        // Place each card
        for (card in sorted) {
            // Make sure we have enough groups. Shouldn't be needed
            while (card.group >= cardGroups.size)
                cardGroups.add(mutableStateListOf())

            // If the group changed, then remove cards that
            // are no longer in the group
            if (card.group > lastGroup) {
                removeTails(lastGroup, lastPosition, card.group)
                // Set the last group and position
                lastGroup = card.group
                lastPosition = 0
            }
            val to = cardGroups[card.group]
            // Add nulls if there is a gap in positions
            while (lastPosition < card.position)
                to.setCard(lastPosition++, null)
            // Set the card
            to.setCard(lastPosition++, card)
        }

        // Remove any other cards not needed in groups
        removeTails(lastGroup, lastPosition, cardGroups.size)
    }

    /**
     * Instantiate a game object
     * @param clazz The class of the game object
     * @return The game object or null if it couldn't be instantiated
     */
    private suspend fun makeGame(clazz: KClass<out Game>): Game? {
        // Get the qualified class name
        val className = clazz.qualifiedName!!
        // Get the game state object from the database, add it if it isn't in the database
        val newState = CardDatabase.db.getStateDao().get(className)?: run {
            StateEntity(0L, className, Bundle()).also {
                CardDatabase.db.getStateDao().insert(it)
            }
        }

        // Get the game constructor and try to instantiate it. Ignore
        // errors during construction, and any reflection errors
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

    /**
     * Set the state we keep for the view model from a bundle
     * @param bundle The bundle used to hold the state
     */
    private fun setState(bundle: Bundle) {
        // Get the card back image
        _cardBack.value = bundle.getString(CARD_BACK_IMAGE, BACK_ASSET_PATH + "red.svg")
        // Get switch for undoing card flips?
        undoCardFlips.value = bundle.getBoolean(ALLOW_UNDO_FOR_FLIPS, false)
        // Get switch for using personal colors
        _useSystemTheme.value = bundle.getBoolean(USE_SYSTEM_THEME, false)
    }

    /**
     * Initialize the view model
     * @param callback A callback function called after the view model is initialized
     * This method will return immediately and initialization happens asynchronously.
     */
    fun initialize(callback: () -> Unit) {
        // Launch a coroutine to initialize the viewmodel
        viewModelScope.launch {
            // We may update the database, so start a transaction
            CardDatabase.db.withTransaction {
                // All games are subclasses of Game
                Game::class.sealedSubclasses.forEach {clazz ->
                    // Try to make the game
                    makeGame(clazz)?.let {
                        // It worked add it to the game list
                        games.add(it)
                    }
                }

                // Did we get any games>
                if (games.isEmpty())
                    throw IllegalStateException("No game to be played")     // No throw an exception

                val stateDao = CardDatabase.db.getStateDao()
                // Preload card front and back images
                preloadCards(getApplication())
                // Have we initialized the commonState
                if (!::commonState.isInitialized) {
                    // No, get it from the state table
                    commonState = stateDao.get(COMMON_STATE_NAME) ?: run {
                        // First run - add a common state entity to the state table
                        StateEntity(0L, COMMON_STATE_NAME, Bundle()).also {
                            CardDatabase.db.getStateDao().insert(it)
                        }
                    }
                }

                // Set the state from the bundle
                setState(commonState.bundle)
                // Get the game qualified name from the bundle and find it
                // in the games list or make the default game, or use the first
                // game in the games list.
                val nextGame = commonState.bundle.getString(GAME_NAME_KEY)?.let {name ->
                    games.firstOrNull { it.name == name }
                }?: games.firstOrNull { it.name.endsWith(DEFAULT_GAME) }
                ?: games[0]

                // If the game is initialized, set the game
                // or create a mutable state object if not
                if (::_game.isInitialized)
                    _game.value = nextGame
                else
                    _game = mutableStateOf(nextGame)
            }

            // Reset the game from the database
            resetGame()
            // Call the callback
            callback()
        }
    }

    companion object {
        // The are keys for bundle values in the state bundle for the view model.
        private const val GAME_NAME_KEY = "game_name"                           // The game qualified class name
        private const val CARD_BACK_IMAGE = "card_back_image"                   // The card back image asset path
        private const val ALLOW_UNDO_FOR_FLIPS = "allow_undo_for_card_flips"    // The allow undo for card flips switch
        private const val USE_SYSTEM_THEME = "use_system_theme"                 // The use personal colors switch

        // The default name name - We only use the end of the name
        // to avoid a reference to the actual default game class
        private const val DEFAULT_GAME = ".ScorpionGame"
        // The name used to store the view model state in the database
        private val COMMON_STATE_NAME = MainActivityViewModel::class.qualifiedName!!
        // The built in card width and height. These are set when the cards are preloaded
        var cardWidth: Int = 234
            private set
        var cardHeight: Int = 333
            private set
        // The path the the asset folder
        private const val ASSET_PATH = "file:///android_asset/"
        // The path to the card front assets
        private const val FRONT_ASSET_PATH = ASSET_PATH + "cards/fronts/"
        // The path to the card back assets
        private const val BACK_ASSET_PATH = ASSET_PATH + "cards/backs/"
        // Color filter for selected objects
        private val selectedFilter = BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply)

        /**
         * Asset names of the card fronts
         * The list is order in card value order
         */
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

        /** Asset name for card backs */
        private val backIds: List<String> = listOf(
            "abstract.svg",         // TODO: Doesn't load need to find out why
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

        /**
         * Preload the fronts and backs of cards
         * @param context The context to get the assets from
         */
        private suspend fun preloadCards(context: Context) {
            // Loop over all of the asset names in frontIds
            for (name in frontIds) {
                // Load each card sequentially
                suspendCoroutine {
                    // Request the card image
                    val request = ImageRequest.Builder(context)
                        .data(FRONT_ASSET_PATH + name)
                        .target(object: coil3.target.Target {
                            override fun onError(error: Image?) {
                                // Error - just crash
                                throw IllegalArgumentException("Missing asset ${FRONT_ASSET_PATH + name}")
                            }

                            override fun onSuccess(result: Image) {
                                // Got the card image, resume the coroutine
                                it.resume(Unit)
                            }
                        })
                        .build()
                    // Start the loader
                    context.imageLoader.enqueue(request)
                }
            }

            // Loop over the asset names in backIds
            for (name in backIds) {
                // Load the images sequentially
                suspendCoroutine {
                    // Request the card
                    val request = ImageRequest.Builder(context)
                        .data(BACK_ASSET_PATH + name)
                        .target(object: coil3.target.Target {
                            override fun onError(error: Image?) {
                                // Error - just crash
                                throw IllegalArgumentException("Missing asset ${BACK_ASSET_PATH + name}")
                            }

                            override fun onSuccess(result: Image) {
                                // Set the real width and height and resume the coroutine
                                cardWidth = result.width
                                cardHeight = result.height
                                it.resume(Unit)
                            }
                        })
                        .build()
                    // Start the loader
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }
}

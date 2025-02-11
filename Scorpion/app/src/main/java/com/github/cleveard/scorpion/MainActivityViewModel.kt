package com.github.cleveard.scorpion

import android.app.Application
import android.content.Context
import android.graphics.drawable.VectorDrawable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import com.github.cleveard.scorpion.ui.widgets.CardDrawable
import com.github.cleveard.scorpion.ui.widgets.CardGroup
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
    /** @inheritDoc */
    override val drawables: List<CardDrawable> = Array(Game.CARD_COUNT) {
        CardDrawable(Card(0, it, 0, 0, 0))
    }.toList()

    /**
     * The cards ordered in their groups
     * Note that the groups are state lists so the display is updated as they are changed
     */
    private val cardGroups: MutableList<CardGroup> = mutableListOf()

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
    override val cardAspect: Float
        get() = MainActivityViewModel.cardAspect
    /** @inheritDoc */
    override val traySizeRatio: Size
        get() = MainActivityViewModel.traySizeRatio
    private val _playAreaSize: MutableState<DpSize> = mutableStateOf(DpSize.Zero)
    /** @inheritDoc */
    override var playAreaSize: DpSize
        get() = _playAreaSize.value
        set(value) { _playAreaSize.value = value}

    /** Mutable state for the current game generation */
    private var generation: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the smallest valid game generation */
    private var minGeneration: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the largest valid game generation */
    private var maxGeneration: MutableState<Long> = mutableLongStateOf(0L)
    /** Mutable state for the switch to allow card flips to be undone */
    private var undoCardFlips: MutableState<Boolean> = mutableStateOf(true)
    /** A map of card values to changed cards for a single action block */
    private val changedCards: MutableMap<Int, Card> = mutableMapOf()
    /** A map of card value to changed cards for all action blocks */
    private val newCards: MutableMap<Int, Card> = mutableMapOf()
    /** A map of card values to the previous value for cards in newCards */
    private val previousCards: MutableMap<Int, Card> = mutableMapOf()
    /** A map of card values to highlight values for cards */
    private val highlightCards: MutableMap<Int, HighlightEntity> = mutableMapOf()
    /** Previous map of card value to highlight values for cards */
    private val previousHighlight: MutableList<HighlightEntity> = mutableListOf()
    /** The withUndo nesting level */
    private var undoNesting: Int = 0
    /** Have we initialized the view model */
    private var initialized: Boolean = false

    /** @inheritDoc */
    override val cards: List<CardGroup>
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
            var success = false
            try {
                // Update the cards in the current card list
                updateCards(list)
                // Update group and card positions and sizes
                game.setupGroups()
                game.cardsUpdated()
                CardDatabase.db.withTransaction {
                    // Add the cards as the first generation
                    CardDatabase.db.newGeneration(game.name, list, 0L)
                    // Clear any highlights
                    highlightCards.clear()
                    CardDatabase.db.getHighlightDao().clear()
                    // Set the current, minimum and maximum generations
                    generation.value = 0L
                    minGeneration.value = 0L
                    maxGeneration.value = 0L
                    success = true
                }
            } catch (e: CardDatabase.DatabaseInconsistency) {
                inconsistentDatabase(e.message)
            } finally {
                if (!success)
                    resetGame()
            }
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

                val groupCount = selectedGame.value.second.groupCount
                // First make sure we have exactly the right number of groups
                while (cardGroups.size < groupCount)
                    cardGroups.add(CardGroup())
                while (cardGroups.size > groupCount) {
                    cardGroups.last().cards.clear()
                    cardGroups.removeAt(cardGroups.lastIndex)
                }

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
        var undoFlips = undoCardFlips.value
        // The selected use system theme value
        var systemTheme = useSystemTheme
        // The current game settings content
        val gameContent: DialogContent? = game.settingsContent()
        // Reset the content to the current settings
        gameContent?.reset()

        // Show the settings dialog
        val value = showDialog(R.string.settings, R.string.dismiss, R.string.accept) {
            // Width and height for the card backs
            val width = Dp(.4f * 160.0f)
            val height = width / cardAspect
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
                undoFlips,
                R.string.allow_undo_for_flips,
                onChange = { undoFlips = it }
            )

            // If the system support personal colors, then let the user
            // decide whether or not to use them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider()

                TextSwitch(
                    systemTheme,
                    R.string.use_system_theme,
                    onChange = { systemTheme = it }
                )
            }

            // The game settings go here
            gameContent?.Content(modifier = Modifier)
        }

        // If the settings are accepted
        if (value == R.string.accept) {
            // Update the bundle for each setting and note whether it changed
            var update = updateState(_cardBack, selectedPath.value, commonState.bundle) { putString(CARD_BACK_IMAGE, it) }
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
    private fun <T> updateState(current: MutableState<T>, changed: T, bundle: Bundle, update: Bundle.(value: T) -> Unit): Boolean {
        // Let the caller know whether something changed
        return (current.value != changed).also {
            if (it) {
                // Update view model and database
                current.value = changed
                bundle.update(changed)
            }
        }
    }

    /** @inheritDoc */
    override fun cardChanged(card: Card): Int {
        // Did anything other than the highlight change
        val changed = drawables[card.value].card.let {
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
     * Report an inconsistentDatabase error
     * @param error A string describing the error.
     */
    private fun inconsistentDatabase(@Suppress("UNUSED_PARAMETER") error: String?) {
        // Show an alert and start a new game
        showNewGameOrDismissAlert(R.string.database_inconsistency, R.string.error)
    }

    /**
     * Validity check for the database
     * This method loads all of the cards for a generation
     * and makes sure the results are valid.
     */
    private suspend fun loadAndCheck() {
        // Load the cards and highlights for the generation
        val pair = CardDatabase.db.loadGame(generation.value)
        // Validate the database and return an error string if the is a problem
        val error = run {
            // Make sure we got something
            if (pair == null)
                return@run "Can't reload the game"
            // Make sure the card count is correct
            if (pair.first.size != drawables.size)
                return@run "Size incorrect"

            // Set the highlights on the cards
            val list = pair.first
            pair.second.forEach {
                list[it.card] = list[it.card].copy(highlight = it.highlight)
            }
            for (i in drawables.indices) {
                // Compare the cards from each table, ignoring highlight
                val c = this.drawables[i].card
                val d = pair.first[i]
                val diff = d.generation != c.generation || d.value != c.value || d.group != c.group ||
                        d.spread != c.spread || d.faceDown != c.faceDown
                if (diff)
                    return@run "Card $i value incorrect"
            }
            // No errors return null
            null
        }

        // If there was an error, dump information to the log
        if (error != null) {
            // Dump the generation
            Log.d(LOG_TAG, "Generation: ${generation.value}")
            for (i in 0 until drawables.size.coerceAtLeast(pair?.first?.size ?: 0)) {
                // Dump each card in the in memory list and in the loaded list
                val c = this.drawables.getOrNull(i)?.card
                val d = pair?.first?.getOrNull(i)
                // Compare the cards from each table, ignoring highlight
                val diff = (d != null && c != null &&
                    (d.generation != c.generation || d.value != c.value || d.group != c.group ||
                        d.spread != c.spread || d.faceDown != c.faceDown)) ||
                    d !== c
                // Log both cards if they are different
                if (diff){
                    Log.d(LOG_TAG, "Card: $d != $c")
                } else
                    Log.d(LOG_TAG, "Card: $d")  // Or just the loaded card if they are the same
            }
            // Show an alert and to start a new game
            throw CardDatabase.DatabaseInconsistency(error)
        }
    }

    /** @inheritDoc */
    override suspend fun <T> withUndo(action: (generation: Long) -> T): T {
        // Success is set to true, if the action completes
        var success = false
        val oldState = game.state.state
        // Get the current highlights, if we aren't nesting
        if (undoNesting == 0) {
            previousHighlight.addAll(highlightCards.values)
            highlightCards.clear()
        }
        ++undoNesting       // Mark the nesting level
        return try {
            // Execute the action
            val result = action(generation.value + 1)
            try {
                // Check for card flips, to clear undo if we don't allow undo of card flips
                // Where there any changes, other than highlights
                // The game can change cards while checking for game over
                // So we loop and let the game check until it doesn't change anything
                while (changedCards.isNotEmpty()) {
                    // Keep previous values of cards
                    previousCards.putAll(changedCards.keys.map {
                        it to (previousCards[it] ?: drawables[it].card)
                    })
                    // Update the cards in list with the changes
                    updateCards(changedCards.values)
                    // Keep track of all changes
                    newCards.putAll(changedCards)
                    // Clear the changed cards
                    changedCards.clear()
                    // Let the game check for game over. This may change more cards
                    game.checkGameOver(generation.value + 1)
                }

                // Is this the last nesting level
                if (undoNesting == 1) {
                    // Update highlights in the cards
                    mutableMapOf<Int, Card>().apply {
                        // Build a change list with the card highlight clear and set
                        putAll(previousHighlight.map { it.card to drawables[it.card].card.copy(highlight = Card.HIGHLIGHT_NONE) })
                        putAll(highlightCards.values.map { it.card to drawables[it.card].card.copy(highlight = it.highlight) })
                        // Update the cards
                        updateCards(values)
                    }
                    // Update card positions
                    game.cardsUpdated()

                    // Make the full database update a single transaction
                    CardDatabase.db.withTransaction {
                        // Check for card flips, to clear undo if we don't allow undo of card flips
                        val cardFlip = newCards.values.any { previousCards[it.value]!!.faceDown && (it.flags and Card.FACE_DOWN) == 0 }

                        if (newCards.isNotEmpty()) {
                            ++generation.value
                            maxGeneration.value = generation.value
                            // Update the new generation in the database
                            CardDatabase.db.newGeneration(
                                game.name,
                                newCards.values,
                                generation.value
                            )
                            // If there was a card flip and we don't allow it to be undone
                            if (!undoCardFlips.value && cardFlip) {
                                // Clear undo so the flip can't be undone.
                                CardDatabase.db.getCardDao().clearUndo(generation.value - 1)
                                minGeneration.value = generation.value
                            }
                        }

                        // Delete old highlights from the database
                        CardDatabase.db.getHighlightDao().delete(previousHighlight.map { it.card })
                        // Add the new highlights
                        CardDatabase.db.getHighlightDao().insert(highlightCards.values)

                        // Update game state if it was changed.
                        if (!game.state.state.contentEquals(oldState))
                            CardDatabase.db.getStateDao().update(game.state.game, game.state.state)

                        loadAndCheck()
                        success = true
                    }
                }
            } catch (e: CardDatabase.DatabaseInconsistency) {
                inconsistentDatabase(e.message)
            }
            result
        } finally {
            // Decrement the undo nesting
            if (undoNesting > 1)
                --undoNesting
            else {
                // Clear all of the structures keeping track of changes
                undoNesting = 0
                newCards.clear()
                previousHighlight.clear()
                previousHighlight.clear()
                changedCards.clear()
                // If the transaction failed, reset the game from the database
                if (!success) {
                    // Something went wrong
                    // Reset the game from the database
                    resetGame()
                    // Make sure nothing is in the database for the new generation
                    CardDatabase.db.getCardDao().clearRedo(generation.value + 1)
                }
            }
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
            var success = false
            try {
                CardDatabase.db.withTransaction {
                    // Get the cards to change
                    CardDatabase.db.undo(game.name, generation.value)?.also {
                        // Update the came
                        updateGame(it)
                        // Bump generation
                        --generation.value
                        success = true
                    }
                }
            } catch (e: CardDatabase.DatabaseInconsistency) {
                inconsistentDatabase(e.message)
                null
            } finally {
                if (!success)
                    resetGame()
            }
        } else
            null

    /** @inheritDoc */
    override suspend fun redo(): List<Card>? =
        // Make sure we can redo
        if (generation.value < maxGeneration.value) {
            var success = false
            try {
                // Get the cards to change
                CardDatabase.db.redo(game.name, generation.value + 1)?.also {
                    // Update the game
                    updateGame(it)
                    // Check for game over
                    game.checkGameOver(generation.value)
                    // Bump the generation
                    ++generation.value
                    success = true
                }
            } catch (e: CardDatabase.DatabaseInconsistency) {
                inconsistentDatabase(e.message)
                null
            } finally {
                if (!success)
                    resetGame()
            }
        } else
            null

    /** @inheritDoc */
    override fun showNewGameOrDismissAlert(text: Int, title:Int, plural: Int?, vararg args: Any) {
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
     * Update the cards in the collection
     * @param changed The cards that changed
     * The cards are updated to drawables and cardGroups
     */
    private fun updateCards(changed: Collection<Card>) {
        // Put a card or null into a card group
        // Position is passed separately, because card may be null.
        fun CardGroup.setCard(position: Int, offsetPos: Int, drawable: CardDrawable?, next: CardDrawable?) {
            val visible = drawable == null || next == null || next.card.group != drawable.card.group ||
                next.card.position > drawable.card.position + 1 || next.card.spread
            drawable?.let {
                // Rely on mutable state object to check for changes
                it.visible = visible
                it.offsetPos = offsetPos
                // Get the asset path for the image
                it.imagePath = if (it.card.faceDown) {
                    // We don't filter card backs
                    it.colorFilter = null
                    cardBackAssetPath      // Card back asset path
                } else {
                    // Get the filter for the highlight
                    it.colorFilter = game.getFilter(it.card.highlight)
                    // Get the card front asset path
                    game.cardFrontAssetPath(it.card.value)
                }
            }
            // Add card if we are at the end of the list
            if (position >= cards.size)
                cards.add(drawable)
                // Otherwise set the card if it has changed
            else {
                if (drawable !== cards[position])
                    cards[position] = drawable
            }
        }

        val groupCount = game.groupCount
        // Set the cards in the list
        for (c in changed) {
            if (c.group >= groupCount)
                throw CardDatabase.DatabaseInconsistency("Group larger than expected")
            drawables[c.value].card = c
        }

        // First make sure we have exactly the right number of groups
        while (cardGroups.size < groupCount)
            cardGroups.add(CardGroup())
        while (cardGroups.size > groupCount) {
            cardGroups.last().cards.clear()
            cardGroups.removeAt(cardGroups.lastIndex)
        }

        // Sort the cards by group and position
        val sorted = drawables.sortedWith(compareBy({ it.card.group }, { it.card.position }))
        val iterator = sorted.iterator()
        var drawable: CardDrawable?
        // Get the first drawable
        drawable = iterator.next()
        // Clear any card groups before the first group with a card
        for (i in 0..<drawable.card.group)
            cardGroups[i].cards.clear()
        var to: CardGroup = cardGroups[drawable.card.group]
        // Clear any cards before the first card
        for (i in 0..<drawable.card.position)
            to.setCard(i, i, null, if (i == drawable.card.position - 1) drawable else null)
        // Set the offset position to the position of the first card in the group
        var offsetPos = drawable.card.position

        // Continue until we have finished with all the cards
        while (drawable != null) {
            // Get the next card or null, at the end of the list
            val next = if (iterator.hasNext()) iterator.next() else null
            // Set the current card in the card groups
            to.setCard(drawable.card.position, offsetPos, drawable, next)
            // If the card is visible, bump the offset position
            if (drawable.visible)
                ++offsetPos
            // If we are at the end of the cards or the end of a group
            if (next == null || next.card.group != drawable.card.group) {
                // Remove cards after this card and before the next group
                removeTails(drawable.card.group, drawable.card.position + 1, next?.card?.group ?: cardGroups.size)
                if (next != null) {
                    // Set the next group
                    to = cardGroups[next.card.group]
                    // Clear cards before the first card in the group
                    for (i in 0..<next.card.position)
                        to.setCard(i, i, null, if (i == next.card.position - 1) next else null)
                    // Set the offset position to the first card position
                    offsetPos = next.card.position
                }
            } else {
                // Clear and spaces between cards, while bumping the offset position
                for (i in drawable.card.position + 1..<next.card.position)
                    to.setCard(i, offsetPos++, null, next)
            }
            // Go to the next card
            drawable = next
        }

        // Validity check. Can't use !== because the SnapshotStateList
        // checks for changes using ==
        // Make sure each drawable is in the proper position in the group
        drawables.firstOrNull { cardGroups.getOrNull(it.card.group)?.cards?.getOrNull(it.card.position) !== it }?.let {d ->
            drawables.sortedWith(compareBy({ it.card.group }, { it.card.position })).forEach {
                val other = cardGroups.getOrNull(it.card.group)?.cards?.getOrNull(it.card.position)
                if (other !== it)
                    Log.d("DATABASE", "$it.card}\n!== $other}")
            }
            throw CardDatabase.DatabaseInconsistency("Card deck and groups are out of sync - $d")
        }
        // Make sure each drawable in the groups has the correct group and position
        cardGroups.indices.forEach {group ->
            val cards = cardGroups[group].cards
            cards.indices.forEach {position ->
                cards[position]?.card?.let { card ->
                    if (card.group != group || card.position != position)
                        throw CardDatabase.DatabaseInconsistency("Card deck and groups are out of sync - ($group,$position) = $card")
                }
            }
        }
        // Let the game make its own checks
        game.isValid().also {
            if (it != null)
                throw CardDatabase.DatabaseInconsistency(it)
        }
    }

    /**
     * Update the game
     * @param list The list of cards to update
     */
    private fun updateGame(list: List<Card>) {
        // Clear the highlights
        updateCards(highlightCards.values.map { drawables[it.card].card.copy(highlight = Card.HIGHLIGHT_NONE) })
        // Update the cards in the card list
        updateCards(list)
        // Update card positions
        game.cardsUpdated()
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
                            try {
                                // Clear current cards in the groups
                                cardGroups.forEach { it.cards.clear() }
                                // Set the list of cards from the database
                                updateCards(pair.first)
                                // Clear highlight list
                                highlightCards.clear()
                                // Set the highlights from the database
                                updateCards(pair.second.map { drawables[it.card].card.copy(highlight = it.highlight) })
                                // Update group and card positions
                                game.setupGroups()
                                game.cardsUpdated()
                                // Add the highlights to the highlight list
                                highlightCards.putAll(pair.second.map { it.card to it })
                                // Set the current, min and max generations
                                generation.value = state.generation
                                minGeneration.value = dbMinGeneration
                                maxGeneration.value = dbMaxGeneration
                            } catch (e: CardDatabase.DatabaseInconsistency) {
                                inconsistentDatabase(e.message)
                                null
                            }
                        } ?: deal()         // Something didn't work, deal a new game
                        newCards.clear()
                        previousHighlight.clear()
                        previousHighlight.clear()
                        // Clear all card changes
                        changedCards.clear()
                        // Check for game over
                        game.checkGameOver(state.generation)
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
            val list = cardGroups[startGroup].cards
            // Clear the cards from startGroup past where we have set
            repeat(list.size - size) { list.removeAt(list.lastIndex) }
            // Clear the groups between start group and the next group we are setting
            for (i in startGroup + 1 until endGroup.coerceAtMost(cardGroups.size)) {
                cardGroups[i].cards.clear()
            }
        }
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
            if (!initialized) {
                // We may update the database, so start a transaction
                CardDatabase.db.withTransaction {
                    // All games are subclasses of Game
                    Game::class.sealedSubclasses.forEach { clazz ->
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
                    val nextGame = commonState.bundle.getString(GAME_NAME_KEY)?.let { name ->
                        games.firstOrNull { it.name == name }
                    } ?: games.firstOrNull { it.name.endsWith(DEFAULT_GAME) }
                    ?: games[0]

                    // If the game is initialized, set the game
                    // or create a mutable state object if not
                    if (::_game.isInitialized)
                        _game.value = nextGame
                    else
                        _game = mutableStateOf(nextGame)
                }
                initialized = true
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
        var cardAspect: Float = 234.0f / 333.0f
            private set
        var traySizeRatio: Size = Size(318.0f / 234.0f, 417.0f / 333.0f)
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
                                cardAspect = result.width.toFloat() / result.height.toFloat()
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

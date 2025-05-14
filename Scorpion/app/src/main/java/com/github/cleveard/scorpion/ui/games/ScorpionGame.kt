package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.R
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.CardDrawable
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.widgets.DropCard
import com.github.cleveard.scorpion.ui.widgets.Scroller
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.launch

/**
 * Scorpion solitaire game
 * @param dealer The dealer interface used by the game
 * @param state The state entity for the game
 * Scorpion lays out cards in 7 columns or 7 cards each. In the first 3 or 4
 * columns the top 3 cards are face down. They cannot be played until all of
 * the cards below them are moved, when they can be flipped over. The remaining 3
 * cards are kept separately in a kitty face down, until no more plays
 * are possible. When no more plays are possible the cards in the kitty
 * are flipped and the game is continued until no more plays are possible.
 *
 * Cards are play on the last card of any column. The cord of the same suit
 * that is one smaller than the last card or a column can be moved to that column.
 * All cards below the moved card are moved with it. Kings can be moved to empty
 * columns. Depending on the variant the king can move alone, or with the cards
 * below it.
 *
 * You win with game by ordering the cards in four columns ace through king of the
 * same suit.
 *
 * There are some variants to this game
 *
 *     The number of columns with face down cards can be 3 or 4
 *
 *     Kings can move to empty columns with our without the cards below them
 *
 *     The kitty cards are dealt to the first three columns when flipped, or
 *     the are played where they are
 *
 * There are two cheats in the settings dialog. You can activate the cheats for one
 * move and they will allow you to make plays that are not normally legal. Once a
 * play is made, with ot without cheating, the cheat is cleared and you must set again.
 *
 *     One cheat allows you to flip cards that you normally wouldn't be able to -
 *     cards in the kitty before play is finished, and top face down card in any column
 *
 *     The other cheat allows you to move a single card to the card one above it, even
 *     if it isn't at the bottom of a column. Kings are moved above the corresponding queen.
 */
@Suppress("unused")
class ScorpionGame(
    dealer: Dealer,
    state: StateEntity
): Game(
    dealer,
    // Game state entity
    state,
    // Qualified class name
    ScorpionGame::class.qualifiedName!!,
    // Number of groups for the game
    GROUP_COUNT,
    // The display name for the game
    R.string.scorpion
) {
    /** Padding around card groups */
    private val padding: Dp = Dp(2.0f)
    /** Switch to show highlights for cards on below or above the selected card */
    private val showHighlights: MutableState<Boolean> = mutableStateOf(state.bundle.getBoolean(SHOW_HIGHLIGHTS, true))
    /** Allow cheating by flipping a card illegally */
    private var cheatCardFlip: Boolean = false
    /** Allow cheating by moving a card illegally */
    private var cheatMoveCard: Boolean = false
    /** Flag to move the kitty cards to the first three columns when flipped */
    private var moveKittyWhenFlipped: Boolean = state.bundle.getBoolean(MOVE_KITTY_WHEN_FLIPPED, false)
    /** Number of columns with face down cards in them */
    private var hiddenCardColumnCount: Int = state.bundle.getInt(HIDDEN_CARD_COLUMN_COUNT, 3)
    /** Flag to move the alone to empty columns */
    private var kingMovesAlone: Boolean = state.bundle.getBoolean(KING_MOVES_ALONE, true)
    private var cardSize: DpSize = DpSize.Zero
    private val fullSize: MutableState<DpSize> = mutableStateOf(DpSize.Zero)
    private val scrollOffset: MutableState<Dp> = mutableStateOf(0.dp)
    private val scroller: Scroller = object: Scroller() {
        override val limits: Pair<Dp, Dp>
            get() = Pair(dealer.playAreaSize.height - fullSize.value.height, 0.dp)
        override var value: Dp
            get() = scrollOffset.value
            set(value) {scrollOffset.value = value}
    }

    /** Dialog content for the variant dialog */
    private val variantContent = object: DialogContent {
        /** Current moveKittyWhenFlipped value */
        var moveKitty = false
        /** Current hiddenCardColumnCount value */
        var hiddenCards = 3
        /** Current kindMovesAlone value  */
        var kingMoves = true

        /** @inheritDoc */
        @Composable
        override fun Content(modifier: Modifier) {
            // Add checkbox for moving the kitty
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                !moveKitty,
                R.string.move_kitty_when_flipped,
                onChange = { moveKitty = !it }
            )

            // Add checkbox for number of columns with face down cards
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                hiddenCards == 3,
                R.string.hidden_cards_3_columns,
                onChange = { hiddenCards = if (it) 3 else 4 }
            )

            // Add checkbox for king moves alone
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                kingMoves,
                R.string.king_moves_alone,
                onChange = { kingMoves = it }
            )
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
            // Accepted update the changed values in the bundle and notice if it has changed
            var update = (moveKittyWhenFlipped != moveKitty).also {
                if (it) {
                    moveKittyWhenFlipped = moveKitty
                    state.bundle.putBoolean(MOVE_KITTY_WHEN_FLIPPED, moveKittyWhenFlipped)
                }
            }
            update = (hiddenCardColumnCount != hiddenCards).also {
                if (it) {
                    hiddenCardColumnCount = hiddenCards
                    state.bundle.putInt(HIDDEN_CARD_COLUMN_COUNT, hiddenCardColumnCount)
                }
            } || update
            update = (kingMovesAlone != kingMoves).also {
                if (it) {
                    kingMovesAlone = kingMoves
                    state.bundle.putBoolean(KING_MOVES_ALONE, kingMovesAlone)
                }
            } || update

            // Update the database if needed
            if (update)
                dealer.onStateChanged(state)
        }

        /** @inheritDoc */
        override fun reset() {
            // Reset current values to values from game
            moveKitty = moveKittyWhenFlipped
            hiddenCards = hiddenCardColumnCount
            kingMoves = kingMovesAlone
        }
    }

    /** Content for the settings dialog */
    private val settingsContent = object: DialogContent {
        /** Current value of showHighlights */
        var showHints = false
        /** Current value of cheatCardFlip */
        var cheatFlip = false
        /** Current value of cheatMoveCard */
        var cheatMove = false

        /** @inheritDoc */
        @Composable
        override fun Content(modifier: Modifier) {
            // Add the checkbox for showHighlights
            HorizontalDivider()
            TextSwitch(
                showHints,
                R.string.show_highlights,
                onChange = { showHints = it }
            )

            Spacer(Modifier.height(8.dp))
            // Add cheats header
            Text(
                stringResource(R.string.cheats)
            )
            // Add checkbox for cheatCardFlip
            HorizontalDivider(Modifier.padding(top = 4.dp))
            TextSwitch(
                cheatFlip,
                R.string.cheat_card_flip,
                onChange = { cheatFlip = it }
            )
            // Add checkbox for cheatMoveCards
            HorizontalDivider()
            TextSwitch(
                cheatMove,
                R.string.cheat_move_card,
                onChange = { cheatMove = it }
            )
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
            // The settings were accepted
            // Set the cheat flags
            cheatCardFlip = cheatFlip
            cheatMoveCard = cheatMove

            // Update showHighlights in the bundle
            var update = false
            update = (showHints != showHighlights.value).also {
                if (it) {
                    showHighlights.value = showHints
                    state.bundle.putBoolean(SHOW_HIGHLIGHTS, showHints)
                }
            } || update

            // Update the database if needed
            if (update)
                dealer.onStateChanged(state)
        }

        /** @inheritDoc */
        override fun reset() {
            // Reset the current values to the values from the game
            showHints = showHighlights.value
            cheatFlip = cheatCardFlip
            cheatMove = cheatMoveCard
        }
    }

    /** @inheritDoc */
    override val cardBackAssetPath: String
        get() = dealer.cardBackAssetPath

    /** @inheritDoc */
    override fun cardFrontAssetPath(value: Int): String {
        return dealer.cardFrontAssetPath(value)
    }

    /** @inheritDoc */
    override suspend fun deal(shuffled: IntArray): List<Card> {
        // Make a list to hold the cards
        val list = mutableListOf<Card>()
        // Get an iterator to the shuffled card values
        val iterator = shuffled.iterator()
        // Add cards to the list
        for (group in 0 until COLUMN_COUNT) {
            for (position in 0 until CARDS_PER_COLUMN) {
                // Get the next value
                val card = iterator.next()
                // Add the card with the current group and position
                list.add(
                    Card(0L, card, group, position, Card.calcFlags(
                        // Set the card face up or face down depending on the column and position
                        faceDown = group < hiddenCardColumnCount && position < CARDS_FACE_DOWN, spread = true))
                )
            }
        }

        // Add remaining cards to the kitty, face down and not spread
        var position = 0
        while (iterator.hasNext()) {
            val card = iterator.next()
            list.add(Card(0L, card, KITTY_GROUP, position++, Card.calcFlags(faceDown = true)))
        }

        // Return the list
        return list
    }

    override fun setupGroups() {
        // TODO: Need to set maximum sizes for larger screens
        // Portrait makes two rows, one for the kitty and one for the columns
        // Landscape puts the columns and kitty in one row
        val twoRows = dealer.playAreaSize.height > dealer.playAreaSize.width
        // The number of columns across the playing surface
        val cols = if (twoRows) GROUP_COUNT - 1 else GROUP_COUNT
        // The width of each column
        val colWidth = dealer.playAreaSize.width / cols
        // The size of the card with padding
        cardSize = DpSize(colWidth, (colWidth - padding * 2) / dealer.cardAspect + padding * 2)
        // Calculate the vertical and horizontal spacing for cards
        val spacing = DpSize(
            ((cardSize.width - padding * 2) * EXPOSE_RATIO).coerceAtLeast(MINIMUM_EXPOSE),
            ((cardSize.height - padding * 2) * EXPOSE_RATIO).coerceAtLeast(MINIMUM_EXPOSE)
        )
        // Set the initial full size of the cards
        fullSize.value = DpSize(dealer.playAreaSize.width, cardSize.height)

        // Set the offset of the columns
        val offset = if (twoRows) {
            // The kitty is a row and above the columns
            dealer.cards[KITTY_GROUP].let {
                it.offset = DpOffset(dealer.playAreaSize.width - cardSize.width, 0.dp)
                it.spacing = DpSize(spacing.width, 0.dp)
            }
            // Put columns below the kitty
            DpOffset(0.dp, cardSize.height)
        } else {
            // The kitty is a column to the right of the other columns
            dealer.cards[KITTY_GROUP].let {
                it.offset = DpOffset(cardSize.width * COLUMN_COUNT, 0.dp)
                it.spacing = DpSize(0.dp, spacing.height)
            }
            // Put columns at top of playable area
            DpOffset(0.dp, 0.dp)
        }
        // Set the offset of each column
        for (i in 0..<COLUMN_COUNT) {
            val group = dealer.cards[i]
            group.offset = DpOffset(offset.x + cardSize.width * i, offset.y)
            // Set spacing so it is a column
            group.spacing = DpSize(0.dp, spacing.height)
        }
    }

    /** @inheritDoc */
    @Composable
    override fun Content(modifier: Modifier) {
        // Wrap everything in a box that we can scroll
        Box(
            modifier = Modifier
                // Set the offset so we can scroll it
                .offset { IntOffset(0, scrollOffset.value.roundToPx()) }
                // Set the size of the content
                .size(fullSize.value)
                // The scroll logic
                .scrollable(
                    orientation = Orientation.Vertical,
                    state = scroller.rememberScrollableState())
        ) {
            // Draw the groups in the playable area
            for (i in 0..<GROUP_COUNT) {
                dealer.cards[i].Content(
                    this@ScorpionGame,
                    cardPadding = PaddingValues(padding),
                    gestures = {
                        dragAndDropCard(it).clickableCard(it)
                    }
                )
            }
        }

        DragContent(padding)
    }

    /** @inheritDoc */
    override fun variantContent(): DialogContent {
        return variantContent
    }

    /** @inheritDoc */
    override fun settingsContent(): DialogContent {
        return settingsContent
    }

    /** @inheritDoc */
    override fun getFilter(highlight: Int): ColorFilter? {
        // If the highlight isn't selected, and we aren't showing highlights
        // then return null so the highlight isn't visible
        if (highlight != HIGHLIGHT_SELECTED && !showHighlights.value)
            return null
        // Return the highlight
        return filters[highlight]
    }

    /** @inheritDoc */
    override fun cardsUpdated() {
        // Update all of the cards in each group
        for (group in dealer.cards)
            group.cardsUpdated(cardSize)
        // The kitty row offset moves depending on how many cards are visible
        val kitty = dealer.cards[KITTY_GROUP]
        // Check spacing for row vs column
        if (kitty.spacing.height == 0.dp) {
            kitty.offset = DpOffset(dealer.playAreaSize.width - kitty.size.width, kitty.offset.y)
        }
        // Adjust the scroll offset if the content size shrinks
        fullSize.value = DpSize(fullSize.value.width, dealer.cards.maxOf { it.offset.y + it.size.height })
        scroller.update()
    }

    /** @inheritDoc */
    override fun onClick(card: Card) {
        // We need to run this in a coroutine because withUndo may
        // Update the database
        dealer.scope.launch {
            // Turn on undo
            dealer.withUndo { generation ->
                // is the card face down
                if (card.faceDown) {
                    // Legally face down cards cn flip only if they are in the kitty
                    // and are spread, or the are at the bottom of a column
                    if (card.group == KITTY_GROUP) {
                        // The card is in the kitty
                        if (moveKittyWhenFlipped) {
                            // The kitty cards are moved to the first three columns when flipped
                            // Have w spread the kitty yet?
                            if (dealer.cards[KITTY_GROUP].cards[0]!!.card.spread) {
                                // Yes - We flip and move all three cards in the kitty
                                for (c in dealer.cards[KITTY_GROUP].cards) {
                                    val group = KITTY_COUNT - 1 - c!!.card.position
                                    c.card.changed(generation = generation, group = group, position = dealer.cards[group].cards.size, faceDown = false, spread = true)
                                }
                            } else if (cheatCardFlip) {
                                // We are cheating, so flip and move the top card in the kitty
                                val group = KITTY_COUNT - 1 - card.position
                                card.changed(generation = generation, group = group, position = dealer.cards[group].cards.size, faceDown = false, spread = true)
                                cheated = true          // Remember that we cheated
                            }
                        } else if (dealer.cards[KITTY_GROUP].cards[0]!!.card.spread) {
                            // The kitty has been spread, so we can flip the card without cheating
                            card.changed(generation = generation, faceDown = false, spread = true)
                        } else if (cheatCardFlip) {
                            // We are cheating, so flip the card anyway
                            card.changed(generation = generation, faceDown = false, spread = true)
                            cheated = true          // Remember that we cheated
                        }
                    } else if (card.position == dealer.cards[card.group].cards.lastIndex) {
                        // The card is at the bottom of a column, so we can flip it
                        card.changed(generation = generation, faceDown = false, spread = true)
                    } else if (cheatCardFlip && dealer.cards[card.group].cards[card.position + 1]!!.card.faceUp) {
                        // We are cheating and the card is the bottom face down card in a column
                        card.changed(generation = generation, faceDown = false, spread = true)
                        cheated = true          // Remember that we cheated
                    }
                } else {
                    // The card is face up. If it is one lower than the selected
                    // card, then try to move it below the selected card
                    val highlight = card.highlight
                    val target = when (highlight) {
                        HIGHLIGHT_ONE_LOWER -> findOneHigher(card.value)
                        HIGHLIGHT_ONE_HIGHER -> findOneLower(card.value)
                        else -> null
                    }
                    if (target == null || !checkAndMove(card, target, generation)) {
                        // checkAndMove couldn't move the card, so we highlight the cards
                        // If the highlight is selected, then don't do anything because the
                        // highlights will be removed in withUndo after we return
                        if (highlight != HIGHLIGHT_SELECTED) {
                            // Highlight the selected card
                            highlight(card, HIGHLIGHT_SELECTED)
                            findOneLower(card.value)?.let {
                                // Highlight the card one lower, but skip it if it is already
                                // immediately below the selected card.
                                if (card.group != it.group || card.position != it.position - 1)
                                    highlight(it, HIGHLIGHT_ONE_LOWER)
                            }
                            findOneHigher(card.value)?.let {
                                // Highlight the card one higher, but skip it if it is already
                                // immediately above the selected card.
                                if (card.group != it.group || card.position != it.position + 1)
                                    highlight(it, HIGHLIGHT_ONE_HIGHER)
                            }
                        }
                    }
                }
            }
        }
    }

    /** @inheritDoc */
    override fun onDoubleClick(card: Card) {
        dealer.scope.launch {
            // We need to run this in a coroutine because withUndo may
            // Update the database
            dealer.withUndo {generation ->
                // Try to move the card
                checkAndMove(card, null, generation)
            }
        }
    }

    /**
     * @inheritDoc
     * We don't use cheating to determine whether the game is over
     */
    override suspend fun checkGameOver(generation: Long) {
        // Loop through all of the groups except the kitty
        for (i in 0 until COLUMN_COUNT) {
            // See if we can play on the last card in the column
            dealer.cards[i].cards.lastOrNull()?.card?.let {card ->
                // Card is face down, we can play it
                if (card.faceDown)
                    return          // the game isn't over
                // See if we can play the card one lower
                findOneLower(card.value)?.let {
                    // The one lower card is in a different column and face
                    if (it.group != card.group && it.faceUp)
                        return      // The game isn't over
                }
            }
        }

        // Next see if we can move any kings to an empty column
        // First check for an empty column
        if (dealer.cards.withIndex().any {it.index < COLUMN_COUNT && it.value.cards.isEmpty() }) {
            // There is an empty column not look for a king
            for (i in CARDS_PER_SUIT - 1 until CARD_COUNT step CARDS_PER_SUIT) {
                // Find the king and queen
                val king = dealer.drawables[i].card
                val queen = dealer.drawables[i - 1].card
                // The king needs to be face up
                // If the king is in the kitty or not in position 0 it could be moved
                // If the king is in position 0, it can only be move if the king moves alone
                // and it isn't the only card in the group and the queen isn't immediately below the king
                if (king.faceUp && (king.group == KITTY_GROUP || king.position > 0 ||
                        (kingMovesAlone && dealer.cards[king.group].cards.size > 1 &&
                            (king.group != queen.group || king.position + 1 != queen.position))))
                    return          // We can move the king
            }
        }

        val kitty = dealer.cards[KITTY_GROUP].cards
        // See if the kitty has any face down cards
        if (kitty.firstOrNull()?.card?.spread == true) {
            // We have spread the kitty, so lets look for a face down card
            if (kitty.any { it!!.card.faceDown })
                return              // We can flip a card in the kit
        }

        // At this point we think the game is over. But if there
        // are cards in the kitty that we haven't spread, then
        // we can spread them and continue play.
        if (kitty.isEmpty() || kitty.all { it!!.card.spread }) {
            // Did we win? Every colum must either be empty or
            // all of the cards in a suit in descending order
            val won = dealer.cards.all { group ->
                // The column must be empty, or all of the cards in a suit
                group.cards.isEmpty() || group.cards.let {
                    // Calculate the ace value for the suit of the
                    // first card in the group.
                    val ace = (group.cards.first()!!.card.value / CARDS_PER_SUIT) * CARDS_PER_SUIT
                    // Now the range of cards in the suit
                    val suit = ace..<ace + CARDS_PER_SUIT
                    // Each card in the column must be in the suit and decreasing in value
                    group.cards.all { drawable -> drawable!!.card.value in suit && drawable.card.position == CARDS_PER_SUIT - 1 - (drawable.card.value - ace) }
                }
            }

            // Did we win
            if (won) {
                // We won !!. Did we cheat
                showGameWon()
            } else {
                // A card is not in the right group or position. We lost.
                // Show the no more moves dialog and return
                dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
            }
        } else {
            // We need to spread the cards in the kitty
            for (drawable in kitty) {
                // If it isn't already spread (from cheating) then change it.
                if (!drawable!!.card.spread)
                    drawable.card.changed(generation = generation, spread = true)
            }
        }
    }

    /** @inheritDoc */
    override fun isValid(): String? {
        // The dealer makes sure the each card in the deck is in the
        // correct place in the groups, so all we need to do is make
        // sure there are no nulls
        if (dealer.cards.any { it.cards.any {drawable -> drawable == null } })
            return "Nulls present in card groups"
        return null
    }

    @Composable
    private fun Modifier.clickableCard(drawable: CardDrawable): Modifier {
        // Allow any card to be clicked
        return with(CardGroup) {
            clickGestures(drawable, this@ScorpionGame)
        }
    }

    fun Card.canPlayOn(target: Card): Boolean {
        return faceUp && target.faceUp &&
            target.group < COLUMN_COUNT &&
            (value % CARDS_PER_SUIT != CARDS_PER_SUIT - 1 &&
            target.value == value + 1 &&
            (cheatMoveCard || (group != target.group && target.position == dealer.cards[target.group].cards.lastIndex))) ||
            (cheatMoveCard && value % CARDS_PER_SUIT != 0 &&
                target.value == value - 1)
    }

    fun Card.canPlayOn(group: Int): Boolean {
        return value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1 && group < COLUMN_COUNT
    }

    @Composable
    private fun Modifier.dragAndDropCard(drawable: CardDrawable): Modifier {
        // Any face up card can be dragged
        if (drawable.card.faceDown)
            return this

        return with(CardGroup) {
            dragAndDropCard(drawable, object : DropCard {
                private var filter: ColorFilter? = null
                private var color: Color = Color(0)
                override val cards: List<CardGroup>
                    get() = dealer.cards

                override fun toGame(offset: DpOffset): DpOffset = DpOffset(offset.x, offset.y - scrollOffset.value)

                override fun toPlayArea(offset: DpOffset): DpOffset = DpOffset(offset.x, offset.y + scrollOffset.value)

                override fun onStarted(sourceDrawable: CardDrawable, dragDrawables: (List<CardDrawable>) -> List<CardGroup>): List<CardGroup> {
                    val group = dealer.cards[sourceDrawable.card.group]
                    val list = if (sourceDrawable.card.group == KITTY_GROUP ||
                        (sourceDrawable.card.value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1 &&
                            group.cards.getOrNull(sourceDrawable.card.position + 1)?.let { it.card.value + 1 } != sourceDrawable.card.value) ||
                        group.cards.getOrNull(sourceDrawable.card.position - 1)?.card?.value?.let { it - 1 } == sourceDrawable.card.value)
                        listOf(sourceDrawable)
                    else
                        ArrayList(group.cards.subList(sourceDrawable.card.position, group.cards.size).filterNotNull())
                    return dragDrawables(list).also {
                        startDrag(it)
                    }
                }

                override fun onDrag(sourceDrawable: CardDrawable, offset: DpOffset) {
                    val autoScroll = (dealer.playAreaSize.height.value * 0.2f).coerceAtLeast(80.0f)
                    when {
                        offset.y.value < autoScroll -> scroller.autoScroll((autoScroll - offset.y.value) * 2.0f)
                        offset.y.value > dealer.playAreaSize.height.value - autoScroll ->
                            scroller.autoScroll((dealer.playAreaSize.height.value - autoScroll - offset.y.value) * 2.0f)
                        else -> scroller.autoScroll(0.0f)
                    }
                }

                override fun onEntered(sourceDrawable: CardDrawable, targetDrawable: Any): Boolean {
                    if (targetDrawable is CardDrawable) {
                        filter = targetDrawable.colorFilter
                        if (sourceDrawable.card.canPlayOn(targetDrawable.card))
                            targetDrawable.colorFilter = dropFilter
                    } else if (targetDrawable is CardGroup) {
                        color = targetDrawable.emptyBackground.value
                        if (sourceDrawable.card.canPlayOn(targetDrawable.group))
                            targetDrawable.emptyBackground.value = dropColor
                    }
                    return false
                }

                override fun onExited(sourceDrawable: CardDrawable, targetDrawable: Any) {
                    if (targetDrawable is CardDrawable)
                        targetDrawable.colorFilter = filter
                    else if (targetDrawable is CardGroup)
                        targetDrawable.emptyBackground.value = color
                    filter = null
                    color = Color(0)
                }

                override fun onEnded(sourceDrawable: CardDrawable, targetDrawable: Any?, velocity: DpOffset) {
                    endDrag()
                    scroller.autoScroll(0.0f)
                    targetDrawable?.let {
                        if (it is CardDrawable) {
                            if (sourceDrawable.card.canPlayOn(it.card)) {
                                dealer.scope.launch {
                                    dealer.withUndo { generation ->
                                        checkAndMove(sourceDrawable.card, it.card, generation)
                                    }
                                }
                            }
                        } else if (it is CardGroup) {
                            if (sourceDrawable.card.canPlayOn(it.group)) {
                                dealer.scope.launch {
                                    dealer.withUndo { generation ->
                                        val moveAlone = sourceDrawable.card.group == KITTY_GROUP ||
                                            (kingMovesAlone && sourceDrawable.card.position < dealer.cards[sourceDrawable.card.group].cards.lastIndex &&
                                                dealer.cards[sourceDrawable.card.group].cards[sourceDrawable.card.position + 1]!!.card.value != sourceDrawable.card.value - 1)
                                        moveCards(sourceDrawable.card, it.group, it.cards.size, generation, moveAlone)
                                    }
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    /** @inheritDoc */
    override fun clearCheats() {
        // Something changed, so clear the cheat flags.
        cheatCardFlip = false
        cheatMoveCard = false
    }

    /**
     * Try to move a card
     * @param card The card to move
     * @param generation The new generation
     */
    private fun checkAndMove(card: Card, target: Card?, generation: Long): Boolean {
        // We can only move face up cards
        if (card.faceUp) {
            if (card.value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1) {
                // The card is a king. Can move it alone, or with the cards below it
                // A card in the kitty always moves alone. Otherwise, the card can only
                // move alone, if it is allowed, and it isn't along in its column, and
                // it isn't matched with its queen
                val moveAlone = card.group == KITTY_GROUP || (kingMovesAlone && card.position < dealer.cards[card.group].cards.lastIndex &&
                    dealer.cards[card.group].cards[card.position + 1]!!.card.value != card.value - 1)
                // If the card isn't at the top of a column, or we can move it alone
                // Then look for an empty column where it can go.
                if (card.position != 0 || moveAlone) {
                    // Look through all the columns
                    for (empty in 0 until COLUMN_COUNT) {
                        // Is this one empty
                        if (dealer.cards[empty].cards.isEmpty()) {
                            // Yes move it and let the caller know we were able to move it
                            moveCards(card, empty, -1, generation, moveAlone)
                            return true
                        }
                    }
                }

                // We don't have legal way to move the kind, so check for cheating
                findOneLower(card.value)?.let {
                    // If we are cheating, and the queen is face up, not in the kitty and not already matched with the king
                    if (cheatMoveCard && it.faceUp && it.group != KITTY_GROUP &&
                        (it.group != card.group || it.position != card.position + 1)) {
                        // We will move just the king above the queen.
                        moveCards(card, it.group, it.position, generation, true)
                        cheated = true          // We cheated
                        return true             // But we did move the card
                    }
                }
            } else {
                val higher = findOneHigher(card.value)
                val lower = findOneLower(card.value)
                // Not a king, so we need to move to the card one higher than this one
                if (higher != null && (!cheatMoveCard || target != lower)) {
                    // We can only legally move the card if the one higher
                    // is not in the same column, is not in the kitty,
                    // is at the end of column and is faceUp
                    if (higher.group != card.group && higher.group != KITTY_GROUP &&
                        higher.position == dealer.cards[higher.group].cards.lastIndex && higher.faceUp) {
                        // Move the cards to the end of column
                        moveCards(card, higher.group, -1, generation, card.group == KITTY_GROUP)
                        return true         // We moved something
                    } else if (cheatMoveCard && higher.faceUp) {
                        // We are cheating and the target card is face up, so we move
                        // just the one card below the target card
                        moveCards(card, higher.group, higher.position + 1, generation, true)
                        cheated = true          // We cheated
                        return true             // And moved something
                    }
                }

                // If we can cheat by moving the card above the lower one
                // then lets do it
                if (cheatMoveCard && lower != null && target == lower && lower.faceUp) {
                    // We are cheating and the target card is face up, so we move
                    // just the one card above the target card
                    moveCards(card, lower.group, lower.position, generation, true)
                    cheated = true          // We cheated
                    return true             // And moved something
                }
            }
        }

        // We couldn't move anything
        return false
    }

    /**
     * Add a highlight to a card
     * @param card The card
     * @param highlight The highlight
     */
    private fun highlight(card: Card, highlight: Int) {
        if (card.faceUp)
            card.changed(highlight = highlight)
    }

    /**
     * Move cards
     * @param card The top card to move
     * @param toGroup The target column
     * @param toPos The target position. -1 means move to the end of the column
     * @param generation The new generation
     * @param single Only move the top card
     */
    private fun moveCards(card: Card, toGroup: Int, toPos: Int, generation: Long, single: Boolean) {
        // From column
        val from = dealer.cards[card.group].cards
        // To column
        val to = dealer.cards[toGroup].cards
        // Position to move to
        var pos = if (toPos >= 0) toPos else to.size
        // If we are cheating, we can move cards in the same column
        if (card.group == toGroup) {
            val moveStart: Int          // Position to start the move
            val moveEnd: Int            // Position to end the move
            val bump: Int               // Amount to move the cards
            if (pos < card.position) {
                // We are moving up in the column
                moveStart = pos             // Start moving cards below the target
                moveEnd = card.position - 1 // Stop moving cards above the card to be moved
                bump = 1                    // Push the cards down the column
            } else if (pos > card.position) {
                --pos                           // We will move the target card up one, so this card will go one lower
                moveStart = card.position + 1   // Start at the card after this card
                moveEnd = pos                   // End at the target card
                bump = -1                       // Move cards up in the column
            } else
                return          // Moving to same position, do nothing
            // Move all the cards up or down one
            for (i in moveStart .. moveEnd)
                from[i]?.card?.let { it.changed(generation = generation, group = toGroup, position = it.position + bump, highlight = Card.HIGHLIGHT_NONE) }
            // Move this card where it belongs
            card.changed(generation = generation, group = toGroup, position = pos, highlight = Card.HIGHLIGHT_NONE)
        } else {
            // Moving between columns is easier
            // Were do we stop the moving card
            val moveEnd = if (single)
                card.position + 1
            else
                from.size
            // How many cards will we move
            val moveCount = moveEnd - card.position
            // Move the cards below the target to make room for the new cards
            for (i in pos until to.size)
                to[i]?.card?.let { it.changed(generation = generation, group = toGroup, position = it.position + moveCount, highlight = Card.HIGHLIGHT_NONE) }
            // Move the cards to where they belong
            for (i in card.position until from.size) {
                // If the cards are before moveEnd, they will move to the new group
                // Otherwise they move up one. This assumes that we are either moving
                // all the cards to the bottom of the column, or just one.
                if (i < moveEnd)
                    from[i]?.card?.changed(generation = generation, group = toGroup, position = pos++, highlight = Card.HIGHLIGHT_NONE)
                else
                    from[i]?.card?.changed(generation = generation, position = i - 1, highlight = Card.HIGHLIGHT_NONE)
            }
        }
    }

    /**
     * Find the card on lower than another
     * @param cardValue The card value
     * @return The card, or null if cardValue is an ace
     */
    private fun findOneLower(cardValue: Int): Card? {
        val value = cardValue % CARDS_PER_SUIT
        return if (value != 0)
            dealer.drawables[cardValue - 1].card
        else
            null
    }

    /**
     * Find the card on higher than another
     * @param cardValue The card value
     * @return The card, or null if cardValue is a king
     */
    private fun findOneHigher(cardValue: Int): Card? {
        val value = cardValue % CARDS_PER_SUIT
        return if (value != CARDS_PER_SUIT - 1)
            dealer.drawables[cardValue + 1].card
        else
            null
    }

    companion object {
        // Constants for laying out the playing surface
        /** The number of columns in the game */
        private const val COLUMN_COUNT: Int = 7
        /** The group number of the kitty */
        private const val KITTY_GROUP: Int = COLUMN_COUNT
        /** The number of groups in the game */
        private const val GROUP_COUNT: Int = KITTY_GROUP + 1
        /** The number of cards per column */
        private const val CARDS_PER_COLUMN: Int = 7
        /** The number of cards face down in a column */
        private const val CARDS_FACE_DOWN: Int = 3
        /** The number of cards dealt to the kitty */
        private const val KITTY_COUNT = CARD_COUNT - COLUMN_COUNT * CARDS_PER_COLUMN

        // Highlight values
        /** Highlight value for selected */
        private const val HIGHLIGHT_SELECTED: Int  = 1
        /** Highlight value for one lower than the selected card */
        private const val HIGHLIGHT_ONE_LOWER: Int  = 2
        /** Highlight value for one height than the selected card */
        private const val HIGHLIGHT_ONE_HIGHER: Int  = 3

        // Keys for values kept in the state bundle
        /** Key for show highlight */
        private const val SHOW_HIGHLIGHTS: String = "show_highlights"
        /** Key for flag to move kitty cards when they are flipped */
        private const val MOVE_KITTY_WHEN_FLIPPED: String = "move_kitty_when_flipped"
        /** Key for the number of columns with face down cards */
        private const val HIDDEN_CARD_COLUMN_COUNT: String = "hidden_card_column_count"
        /** Key for the flag to move kings by themselves. */
        private const val KING_MOVES_ALONE: String = "king_moves_alone"

        private const val EXPOSE_RATIO: Float = 0.15f
        private val MINIMUM_EXPOSE: Dp = 160.dp * 0.3f

        /** The filter used for the highlights */
        private val filters: List<ColorFilter?> = listOf(
            null,
            BlendModeColorFilter(Color(0x60000000), BlendMode.SrcOver),
            BlendModeColorFilter(Color(0x6000FF00), BlendMode.SrcOver),
            BlendModeColorFilter(Color(0x60FF0000), BlendMode.SrcOver)
        )
        private val dropColor = Color(0x600000FF)
        private val dropFilter = BlendModeColorFilter(dropColor, BlendMode.SrcOver)
    }
}
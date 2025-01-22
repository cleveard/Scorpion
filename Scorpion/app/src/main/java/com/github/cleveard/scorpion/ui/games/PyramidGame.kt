package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.R
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.launch

class PyramidGame(private val dealer: Dealer, state: StateEntity): Game(
    state,
    PyramidGame::class.qualifiedName!!,
    GROUP_COUNT,
    R.string.pyramid
) {
    private val cardPadding = 2.dp
    private val stockPadding = 20.dp
    private var stockPassCount: Int = state.bundle.getInt(STOCK_PASS_COUNT, 2)
    private var stockPasses: Int = state.bundle.getInt(STOCK_PASSES, 0)
    private var clearPyramidOnly: Boolean = state.bundle.getBoolean(CLEAR_PYRAMID_ONLY, true)
    private var playPartialCover: Boolean = state.bundle.getBoolean(PLAY_PARTIAL_COVER, true)
    private var showHighlights: MutableState<Boolean> = mutableStateOf(state.bundle.getBoolean(SHOW_HIGHLIGHTS, true))

    /** Dialog content for the variant dialog */
    private val variantContent = object: DialogContent {
        /** Current moveKittyWhenFlipped value */
        val twoPass = mutableIntStateOf(2)
        /** Current hiddenCardColumnCount value */
        val clearPyramidWins = mutableStateOf(true)
        /** Current kindMovesAlone value  */
        val playPartial = mutableStateOf(true)

        /** @inheritDoc */
        @Composable
        override fun Content(modifier: Modifier) {
            // Add checkbox for two passes of the cards
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                twoPass.intValue == 2,
                R.string.pass_cards_twice,
                onChange = { twoPass.intValue = if (it) 2 else 1 }
            )

            // Add checkbox to win by clearing the pyramid
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                clearPyramidWins.value,
                R.string.clear_pyramid_wins,
                onChange = { clearPyramidWins.value = it }
            )

            // Add checkbox to allow playing partial covers
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                playPartial.value,
                R.string.play_partial_covers,
                onChange = { playPartial.value = it }
            )
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
            // Accepted update the changed values in the bundle and notice if it has changed
            var update = (stockPassCount != twoPass.intValue).also {
                if (it) {
                    stockPassCount = twoPass.intValue
                    state.bundle.putInt(STOCK_PASS_COUNT, stockPassCount)
                }
            }
            update = (clearPyramidOnly != clearPyramidWins.value).also {
                if (it) {
                    clearPyramidOnly = clearPyramidWins.value
                    state.bundle.putBoolean(CLEAR_PYRAMID_ONLY, clearPyramidOnly)
                }
            } || update
            update = (playPartialCover != playPartial.value).also {
                if (it) {
                    playPartialCover = playPartial.value
                    state.bundle.putBoolean(PLAY_PARTIAL_COVER, playPartialCover)
                }
            } || update

            // Update the database if needed
            if (update)
                dealer.onStateChanged(state)
        }

        /** @inheritDoc */
        override fun reset() {
            // Reset current values to values from game
            twoPass.intValue = stockPassCount
            clearPyramidWins.value = clearPyramidOnly
            playPartial.value = playPartialCover
        }
    }

    /** Content for the settings dialog */
    private val settingsContent = object: DialogContent {
        /** Current value of showHighlights */
        val showHints = mutableStateOf(false)

        /** @inheritDoc */
        @Composable
        override fun Content(modifier: Modifier) {
            // Add the checkbox for showHighlights
            HorizontalDivider()
            TextSwitch(
                showHints.value,
                R.string.show_highlights,
                onChange = { showHints.value = it }
            )
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
            // Update showHighlights in the bundle
            var update = false
            update = (showHints.value != showHighlights.value).also {
                if (it) {
                    showHighlights.value = showHints.value
                    state.bundle.putBoolean(SHOW_HIGHLIGHTS, showHints.value)
                }
            } || update

            // Update the database if needed
            if (update)
                dealer.onStateChanged(state)
        }

        /** @inheritDoc */
        override fun reset() {
            // Reset the current values to the values from the game
            showHints.value = showHighlights.value
        }
    }

    override val cardBackAssetPath: String
        get() = dealer.cardBackAssetPath

    override fun cardFrontAssetPath(value: Int): String = dealer.cardFrontAssetPath(value)

    override suspend fun deal(shuffled: IntArray): List<Card> {
        stockPasses = 0
        state.bundle.putInt(STOCK_PASSES, stockPasses)
        dealer.onStateChanged(state)
        val list = mutableListOf<Card>()
        var i = 0
        for (group in 0 until ROW_COUNT) {
            for (position in 0 until group + 1)
                list.add(Card(0L, shuffled[i++], group, position, Card.SPREAD))
        }
        val count = i
        while (i < shuffled.lastIndex - 1) {
            list.add(Card(0L, shuffled[i], STOCK_GROUP, i - count, Card.FACE_DOWN or Card.HIGHLIGHT_NONE))
            ++i
        }
        list.add(Card(0L, shuffled[i], STOCK_GROUP, i - count, Card.HIGHLIGHT_NONE))
        ++i
        list.add(Card(0L, shuffled[i], WASTE_GROUP, 0, Card.HIGHLIGHT_NONE))
        return list
    }

    @Composable
    override fun BoxWithConstraintsScope.Content(modifier: Modifier) {
        // Calculate the card aspect ratio
        val aspect = dealer.cardAspect
        // The width of cards + padding for portrait layout
        val widthPortrait = ((maxWidth / ROW_COUNT) - cardPadding * 2)
            .coerceAtMost((maxHeight - cardPadding * 4 - stockPadding) * aspect / ((ROW_COUNT + 3) * 0.5f))
        // The width of cards + padding for landscape layout
        val widthLandscape = (((maxWidth - stockPadding) / (ROW_COUNT + 1)) - cardPadding * 2)
            .coerceAtMost((maxHeight - cardPadding * 2) * aspect / ((ROW_COUNT + 1) * 0.5f))
        // Choose the layout with the largest width
        val portrait = widthPortrait > widthLandscape
        // Calculate the scale required for the cards
        val width = widthLandscape.coerceAtLeast(widthPortrait)

        val size = DpSize(width + cardPadding * 2, width / aspect + cardPadding * 2)
        val pyramidSize = DpSize(size.width * ROW_COUNT, (size.height - cardPadding * 2) * 0.5f * (ROW_COUNT - 1) + size.height)
        val fullSize = if (portrait)
            DpSize(pyramidSize.width, pyramidSize.height + size.height + stockPadding)
        else
            DpSize(pyramidSize.width + size.width + stockPadding, pyramidSize.height)
        val pyramidOffset = DpOffset((maxWidth - fullSize.width) / 2.0f, (maxHeight - fullSize.height) / 2.0f)

        // The pyramid itself goes at the top-start of the box. The pyramid is formed
        // from rows in a column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(pyramidOffset.x, pyramidOffset.y)
                .size(pyramidSize),
            verticalArrangement = Arrangement.spacedBy((-cardPadding - size.height * 0.5f), Alignment.Top)
        ) {
            for (i in 0..<ROW_COUNT) {
                CardGroup.RowContent(
                    dealer.cards[i],
                    dealer.game,
                    size = size,
                    modifier = Modifier.size(size.width * (i + 1), size.height),
                    cardPadding = PaddingValues(cardPadding)
                )
            }
        }

        if (portrait) {
            // The stock and waste are put below the pyramid on the left
            // and the discard is below the pyramid on the right
            val offsetY = pyramidOffset.y + pyramidSize.height + stockPadding
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.offset(x = pyramidOffset.x, y = offsetY)
                    .width(size.width * 2.0f + stockPadding)
            ) {
                CardGroup.RowContent(
                    dealer.cards[STOCK_GROUP],
                    dealer.game,
                    size = size,
                    modifier = Modifier
                        .size(size.width, size.height),
                    cardPadding = PaddingValues(cardPadding)
                )

                CardGroup.RowContent(
                    dealer.cards[WASTE_GROUP],
                    dealer.game,
                    size = size,
                    modifier = Modifier
                        .size(size.width, size.height),
                    cardPadding = PaddingValues(cardPadding)
                )
            }

            CardGroup.RowContent(
                dealer.cards[DISCARD_GROUP],
                dealer.game,
                size = size,
                modifier = Modifier
                    .offset(pyramidOffset.x + fullSize.width - size.width, offsetY)
                    .size(size.width, size.height),
                cardPadding = PaddingValues(cardPadding)
            )
        } else {
            // The stock and waste are put below the pyramid on the top-end
            // and the discard is at the bottom-end
            val endOffsetX = pyramidOffset.x + fullSize.width
            val stockWidth = size.width * 2.0f + stockPadding
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.offset(x = endOffsetX - stockWidth, y = pyramidOffset.y)
                    .width(stockWidth)
            ) {
                CardGroup.RowContent(
                    dealer.cards[STOCK_GROUP],
                    dealer.game,
                    size = size,
                    modifier = Modifier
                        .size(size.width, size.height),
                    cardPadding = PaddingValues(cardPadding)
                )

                CardGroup.RowContent(
                    dealer.cards[WASTE_GROUP],
                    dealer.game,
                    size = size,
                    modifier = Modifier
                        .size(size.width, size.height),
                    cardPadding = PaddingValues(cardPadding)
                )
            }

            CardGroup.RowContent(
                dealer.cards[DISCARD_GROUP],
                dealer.game,
                size = size,
                modifier = Modifier
                    .offset(pyramidOffset.x + pyramidSize.width + stockPadding, pyramidOffset.y + pyramidSize.height - size.height)
                    .size(size.width, size.height),
                    cardPadding = PaddingValues(cardPadding)
            )
        }
    }

    override fun variantContent(): DialogContent {
        return variantContent
    }

    override fun settingsContent(): DialogContent {
        return settingsContent
    }

    override fun getFilter(highlight: Int): ColorFilter? {
        return when (highlight) {
            HIGHLIGHT_SELECTED -> selectFilter
            HIGHLIGHT_MATCH -> if (showHighlights.value) matchFilter else null
            else -> null
        }
    }

    /**
     * Check whether a card in the pyramid is playable
     */
    private fun Card.playable(): Card? {
        // Cards in the bottom row are always playable
        if (group == ROW_COUNT - 1)
            return this
        // The last card in the stock or waste are playable
        if (group == WASTE_GROUP || group == STOCK_GROUP)
            return if (position == dealer.cards[group].lastIndex) this else null
        if (group >= ROW_COUNT - 1)
            return null
        // Get the row below the card's row
        val cover = dealer.cards[group + 1]
        // If there is a card a position or position + 1 then this card is not playable
        return if (cover.getOrNull(position) != null || cover.getOrNull(position + 1) != null)
            null
        else
            this
    }

    /**
     * Process all of the matches for a card
     * @param callback Lambda to process the matching card. If the lambda
     *                 returns true, the process stops and the matching
     *                 card is return. If the lambda never returns true
     *                 null is returned.
     */
    private fun Card.processMatches(callback: (Card) -> Boolean): Card? {
        val matchValue = CARDS_PER_SUIT - 2 - value % CARDS_PER_SUIT
        for (i in matchValue..<CARD_COUNT step CARDS_PER_SUIT) {
            dealer.findCard(i).let {
                if (callback(it))
                    return it
            }
        }
        return null
    }
    /**
     * Find the card that plays with this
     * @return The match card or null if not found. Kings return themselves
     */
    private fun Card.findPlayable(): Card? {
        // Get the value of the card
        val cardValue = value % CARDS_PER_SUIT
        // If it is a king then match itself
        if (cardValue == CARDS_PER_SUIT - 1)
            return this

        val match = processMatches {
            it != this && it.highlight == HIGHLIGHT_SELECTED
        }

        // No matches with this sum to 13
        return match?.let {
            // Check whether either or both cards are playable
            val matchPlayable = it.playable() != null
            if (playable() != null) {
                if (matchPlayable)
                    return it         // Both cards are playable
            } else if (!matchPlayable)
                return null            // Neither card is playable
            if (!playPartialCover)
                return null            // Don't allow partial covers
            else if (group >= ROW_COUNT || it.group >= ROW_COUNT)
                return null            // Partial cover only works if both cards are in the pyramid

            // One of the cards is playable, one isn't; note which is which
            val playCard: Card
            val notPlayCard: Card
            if (matchPlayable) {
                playCard = it
                notPlayCard = this
            } else {
                playCard = this
                notPlayCard = it
            }

            // These two cards can be played only if the playCard
            // is the only card that covers the notPlayCard
            // First is the playCard in the group below the notPlayCard
            val cardsPlayable = notPlayCard.group + 1 == playCard.group &&
                notPlayCard.position.let {pos ->
                    // Either playCard is at position and the card at position + 1 is null
                    (pos == playCard.position && dealer.cards[playCard.group].getOrNull(playCard.position + 1) == null) ||
                        // or playCard is at position + 1 and the card at position is null
                        (pos + 1 == playCard.position && dealer.cards[playCard.group][playCard.position - 1] == null)
                }
            if (cardsPlayable)
                match
            else
                null
        }
    }

    override suspend fun checkGameOver(generation: Long) {
        fun Card.markSum(sum13: IntArray, playableCards: MutableMap<Int, Card>): Boolean {
            playableCards[value] = this
            // The number value of the card
            val value = value % CARDS_PER_SUIT + 1
            return if (value < (CARDS_PER_SUIT + 1) / 2)
            // If it is 6 or less mark it in the appropriate slot
                (sum13[value] or LESS_THAN_7).let { sum13[value] = it; it == SUM_13 }
            else
            // If it is 7 or more put it in the slot that matches the sum
                (sum13[CARDS_PER_SUIT - value] or MORE_THAN_6).let { sum13[CARDS_PER_SUIT - value] = it; it == SUM_13 }
        }
        
        // If clearing the pyramid is a win and it is clear, then let the user know
        // This relies on the dealer to empty groups with no cards in them
        if (dealer.cards[0].isEmpty() && clearPyramidOnly) {
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
            return
        }

        val stock = dealer.cards[STOCK_GROUP]
        val waste = dealer.cards[WASTE_GROUP]

        // If there are cards in the stock, the game isn't over
        if (stock.isNotEmpty())
            return

        // Array of ways to sum cards to 13
        val sum13 = IntArray((CARDS_PER_SUIT + 1) / 2)
        sum13[0] = LESS_THAN_7
        val playableCards = mutableMapOf<Int, Card>()
        // Include top card of stock and waste
        if (stock.lastOrNull()?.markSum(sum13, playableCards) == true ||
            waste.lastOrNull()?.markSum(sum13, playableCards) == true)
            return      // Sum to 13, game isn't over
        // Loop through the cards in pyramid
        (0..<ROW_COUNT).forEach {group ->
            val row = dealer.cards[group]
            // Check whether we have a pair or playable cards that sum to 13
            row.forEach {
                if (it?.playable()?.markSum(sum13, playableCards) == true)
                    return      // Sum to 13 game isn't over
            }
        }

        // Now look for partially covered plays
        if (playPartialCover) {
            playableCards.values.forEach { card ->
                if (card.group > 0) {
                    val group = dealer.cards[card.group]
                    // First check the card covered on the right side
                    if (card.position < group.lastIndex && group[card.position] == null) {
                        // Calculate sum
                        val sum = dealer.cards[card.group - 1][card.position]!!.value + card.value
                        if (sum % CARDS_PER_SUIT == CARDS_PER_SUIT - 1)
                            return              // Sum to 13 - game not over
                    }
                    // Next check the card covered on the left side
                    if (card.position > 0 && group[card.position - 1] == null) {
                        // Calculate sum
                        val sum = dealer.cards[card.group - 1][card.position - 1]!!.value + card.value
                        if (sum % CARDS_PER_SUIT == CARDS_PER_SUIT - 2)
                            return              // Sum to 13 - game not over
                    }
                }
            }
        }

        // Can we go through the cards again
        if (dealer.cards[WASTE_GROUP].size > 1 && dealer.cards[STOCK_GROUP].isEmpty() && stockPasses + 1 < stockPassCount)
            return      // Yes, game not over

        // The game is over, did we win
        if (dealer.cards[DISCARD_GROUP].size == CARD_COUNT) {
            // Yes, let the user know
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
        } else {
            // No, let the user know
            dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
        }
    }

    private suspend fun wasteToStock(generation: Long): Boolean {
        val waste = dealer.cards[WASTE_GROUP]
        val wasteSize = waste.size
        // Should we take the cards from the waste and try some more
        return(wasteSize > 1 && dealer.cards[STOCK_GROUP].isEmpty() && stockPasses + 1 < stockPassCount).also {
            ++stockPasses
            state.bundle.putInt(STOCK_PASSES, stockPasses)
            dealer.onStateChanged(state)
            if (it) {
                (2..<wasteSize).forEach {i ->
                    waste[i]?.let { w -> w.changed(generation = generation, group = STOCK_GROUP, position = wasteSize - 1 - w.position, faceDown = true, spread = false) }
                }
                waste[1]?.let { w -> w.changed(generation = generation, group = STOCK_GROUP, position = wasteSize - 1 - w.position, faceDown = false, spread = false) }
            }
        }
    }

    override fun isValid(): String? {
        return when {
            dealer.cards[STOCK_GROUP].any { it == null } -> "Stock has a null card"
            dealer.cards[WASTE_GROUP].any { it == null } -> "Waste has a null card"
            dealer.cards[DISCARD_GROUP].any { it == null } -> "Discard has a null card"
            else -> null
        }
    }

    private fun playCard(card: Card, group: Int, pos: Int, generation: Long) {
        card.changed(generation = generation, group = group,
            position = pos, highlight = Card.HIGHLIGHT_NONE, faceDown = false, spread = false)
        if (card.group == STOCK_GROUP && dealer.cards[STOCK_GROUP].size > 1) {
            dealer.cards[STOCK_GROUP].let { it[it.lastIndex - 1]?.changed(generation = generation,
                highlight = Card.HIGHLIGHT_NONE, faceDown = false, spread = false) }
        }
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            dealer.withUndo { generation ->
                val match = card.findPlayable()
                // Is this card playable
                if (match != null) {
                    // Keep track of position for played cards
                    var pos = dealer.cards[DISCARD_GROUP].size
                    // Is the card a king
                    if (match == card) {
                        // This is a king, just play it
                        playCard(card, DISCARD_GROUP, pos, generation)
                        return@withUndo
                    } else {
                        // We have a card and a match, so play them
                        playCard(match, DISCARD_GROUP, pos++, generation)
                        playCard(card, DISCARD_GROUP, pos, generation)
                        return@withUndo
                    }
                }

                // We didn't play the card, so select the card unless it was selected
                if (card.group != DISCARD_GROUP && card.highlight != HIGHLIGHT_SELECTED) {
                    // Select the clicked card
                    card.changed(generation = generation, highlight = HIGHLIGHT_SELECTED)
                    card.processMatches {
                        // If the match is faceUp highlight it
                        if (it.faceUp)
                            it.changed(generation = generation, highlight = HIGHLIGHT_MATCH)
                        false
                    }
                }
            }
        }
    }

    override fun onDoubleClick(card: Card) {
        dealer.scope.launch {
            // The only thing double click does is move
            // the top card of the stock to the waste
            dealer.withUndo {generation ->
                if (card.group == STOCK_GROUP) {
                    playCard(card, WASTE_GROUP, dealer.cards[WASTE_GROUP].size, generation = generation)
                } else if (card.group == WASTE_GROUP)
                    wasteToStock(generation)
            }
        }
    }

    /**
     * Add a card to the card changed list
     * @param generation The new generation
     * @param group The new group
     * @param position The new position
     * @param highlight The new highlight
     * @param faceDown The new faceDown flag
     * @param spread The new spread flag
     */
    private fun Card.changed(
        generation: Long = this.generation,
        group: Int = this.group,
        position: Int = this.position,
        highlight: Int = this.highlight,
        faceDown: Boolean = this.faceDown,
        spread: Boolean = this.spread
    ) {
        // Add the card remember how many cards have changed
        dealer.cardChanged(copy(
            generation = generation,
            group = group,
            position = position,
            highlight = highlight,
            faceDown = faceDown,
            spread = spread
        ))
    }

    companion object {
        const val ROW_COUNT = 7
        const val STOCK_GROUP = ROW_COUNT
        const val WASTE_GROUP = STOCK_GROUP + 1
        const val DISCARD_GROUP = WASTE_GROUP + 1
        const val GROUP_COUNT = DISCARD_GROUP + 1

        const val HIGHLIGHT_SELECTED = 1
        const val HIGHLIGHT_MATCH = 2

        const val MORE_THAN_6 = 1
        const val LESS_THAN_7 = 2
        const val SUM_13 = MORE_THAN_6 or LESS_THAN_7

        const val STOCK_PASSES = "stock_passes"
        const val STOCK_PASS_COUNT = "stock_pass_count"
        const val CLEAR_PYRAMID_ONLY = "clear_pyramid_only"
        const val PLAY_PARTIAL_COVER = "play_partial_games"
        const val SHOW_HIGHLIGHTS = "show_highlights"

        val selectFilter = BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply)
        val matchFilter = BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply)

    }
}
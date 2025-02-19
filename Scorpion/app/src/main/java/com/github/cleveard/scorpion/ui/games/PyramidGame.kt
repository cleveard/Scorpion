package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.github.cleveard.scorpion.ui.widgets.CardDrawable
import com.github.cleveard.scorpion.ui.widgets.DropCard
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.launch

class PyramidGame(dealer: Dealer, state: StateEntity): Game(
    dealer,
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
    private var cardSize: DpSize = DpSize.Zero
    private var traySize: DpSize = DpSize.Zero

    /** Dialog content for the variant dialog */
    private val variantContent = object: DialogContent {
        /** Current moveKittyWhenFlipped value */
        var twoPass = 2
        /** Current hiddenCardColumnCount value */
        var clearPyramidWins = true
        /** Current kindMovesAlone value  */
        var playPartial = true

        /** @inheritDoc */
        @Composable
        override fun Content(modifier: Modifier) {
            // Add checkbox for two passes of the cards
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                twoPass == 2,
                R.string.pass_cards_twice,
                onChange = { twoPass = if (it) 2 else 1 }
            )

            // Add checkbox to win by clearing the pyramid
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                clearPyramidWins,
                R.string.clear_pyramid_wins,
                onChange = { clearPyramidWins = it }
            )

            // Add checkbox to allow playing partial covers
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                playPartial,
                R.string.play_partial_covers,
                onChange = { playPartial = it }
            )
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
            // Accepted update the changed values in the bundle and notice if it has changed
            var update = (stockPassCount != twoPass).also {
                if (it) {
                    stockPassCount = twoPass
                    state.bundle.putInt(STOCK_PASS_COUNT, stockPassCount)
                }
            }
            update = (clearPyramidOnly != clearPyramidWins).also {
                if (it) {
                    clearPyramidOnly = clearPyramidWins
                    state.bundle.putBoolean(CLEAR_PYRAMID_ONLY, clearPyramidOnly)
                }
            } || update
            update = (playPartialCover != playPartial).also {
                if (it) {
                    playPartialCover = playPartial
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
            twoPass = stockPassCount
            clearPyramidWins = clearPyramidOnly
            playPartial = playPartialCover
        }
    }

    /** Content for the settings dialog */
    private val settingsContent = object: DialogContent {
        /** Current value of showHighlights */
        var showHints = false

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
        }

        /** @inheritDoc */
        override suspend fun onDismiss() {
        }

        /** @inheritDoc */
        override suspend fun onAccept() {
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
        list.add(Card(0L, shuffled[i], STOCK_GROUP, i - count, Card.SPREAD or Card.HIGHLIGHT_NONE))
        ++i
        list.add(Card(0L, shuffled[i], WASTE_GROUP, 0, Card.HIGHLIGHT_NONE))
        return list
    }

    override fun setupGroups() {
        // Get the card aspect ratio
        val aspect = dealer.cardAspect
        // We solve the formula for the size to get the width that will best fit the
        // play area size. I will put this here to remember how these were calculated.
        // Some notation - pw and ph the play area width and height. cw and ch the card
        // image width and height. tw and th the ratio of the tray width and height with
        // the card width and height. a - the aspect ratio, r the count of rows in the pyramid
        // p the padding around each card image, s - padding between stock, waste and discard piles
        // and the pyramid.
        // The full size of the layout depends on whether we use the landscape or portrait
        // layout.
        // The portrait layout puts the stock, waste and discard piles under the pyramid.
        // So the width is the width of the pyramid, which is (cw + 2p) * r and we solve
        // r * (cw + 2 * p) = pw
        // cw + 2 * p = pw / r
        // cw = pw / r - 2 * p
        // The height in portrait mode is the height of the pyramid + s + th * ch + 2p and we solve
        // ch * (r - 1) / 2 + ch + 2 * p + s + th * ch + 2 * p = ph
        // ch * (r - 1) / 2 + ch + ch * th = ph - s - 4 * p
        // ch * ((r - 1) / 2 + 1 + th) = ph - s - 4 * p
        // ch = (ph - s - 4 * p) / ((r - 1) / 2 + 1 + th)
        // and cw = ch * a
        // The width of cards + padding for portrait layout
        val widthPortrait = ((dealer.playAreaSize.width / ROW_COUNT) - cardPadding * 2)
            .coerceAtMost((dealer.playAreaSize.height - cardPadding * 4 - stockPadding) * aspect /
                ((ROW_COUNT + 1) * 0.5f + dealer.traySizeRatio.height))
        // The landscape layout puts the stock, waste and discard piles to the right of the pyramid.
        // So the width is the width of the pyramid, which is (cw + 2p) * r + s + cw * tw + 2 * p
        // and we solve
        // (cw + 2p) * r + s + cw * tw + 2 * p = pw
        // r * cw + r * 2 * p + s + cw * tw + 2 * p = pw
        // cw * (r + tw) = pw - 2 * p - r * 2 * p - s
        // cw = (pw - (r + 1) * 2 * p - s) / (r + tw)
        // The height in landscape mode is the height of the pyramid and we solve
        // ch * (r - 1) / 2 + ch + 2 * p = ph
        // ch * (r - 1) / 2 + ch = ph - 2 * p
        // ch * ((r - 1) / 2 + 1) = ph - 2 * p
        // ch = (ph - 2 * p) / ((r + 1) / 2)
        // and cw = ch * a
        // The width of cards + padding for landscape layout
        val widthLandscape = ((dealer.playAreaSize.width - stockPadding - cardPadding * 2 * (ROW_COUNT + 1)) / (ROW_COUNT + dealer.traySizeRatio.width))
            .coerceAtMost((dealer.playAreaSize.height - cardPadding * 2) * aspect / ((ROW_COUNT + 1) * 0.5f))
        // Choose the layout with the largest width
        val portrait = widthPortrait > widthLandscape
        // Calculate the scale required for the cards
        val width = widthLandscape.coerceAtLeast(widthPortrait)

        // Set the card size
        cardSize = DpSize(width + cardPadding * 2, width / aspect + cardPadding * 2)
        traySize = DpSize(width * dealer.traySizeRatio.width + cardPadding * 2, width * dealer.traySizeRatio.height / aspect + cardPadding * 2)
        // Calculate the size of the pyramid without the stock, waste and discard groups
        val pyramidSize = DpSize(cardSize.width * ROW_COUNT, (cardSize.height - cardPadding * 2) * 0.5f * (ROW_COUNT - 1) + cardSize.height)
        // Calculate the full size of the cards in the playable area. Portrait layout puts the stock,
        // waste and discard piles under the pyramid. Landscape puts them to the right of the pyramid
        val fullSize = if (portrait)
            DpSize(pyramidSize.width, pyramidSize.height + traySize.height + stockPadding)
        else
            DpSize(pyramidSize.width + traySize.width + stockPadding, pyramidSize.height)
        // The offset of the pyramid to center it in the playable area.
        val pyramidOffset = DpOffset((dealer.playAreaSize.width - fullSize.width) / 2.0f, (dealer.playAreaSize.height - fullSize.height) / 2.0f)

        // Calculate the offsets for each row in the pyramid
        for (i in 0..<ROW_COUNT) {
            val group = dealer.cards[i]
            // Center each row horizontally and space by half a card vertically
            group.offset = pyramidOffset + DpOffset(
                (pyramidSize.width - cardSize.width * (i + 1)) / 2,
                (cardSize.height * 0.5f - cardPadding) * i
            )
            // Set the group spacing to be a row
            group.spacing = DpSize(cardSize.width, 0.dp)
        }

        if (portrait) {
            // The stock and waste are put below the pyramid on the left
            // and the discard is below the pyramid on the right
            val offsetY = pyramidOffset.y + pyramidSize.height + (traySize.height - cardSize.height) / 2 + stockPadding
            val offsetX = pyramidOffset.x + (traySize.width - cardSize.width) / 2
            dealer.cards[STOCK_GROUP].let {
                it.offset = DpOffset(offsetX, offsetY)
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
            dealer.cards[WASTE_GROUP].let {
                it.offset = DpOffset(offsetX + traySize.width + stockPadding, offsetY)
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
            dealer.cards[DISCARD_GROUP].let {
                it.offset = DpOffset(pyramidOffset.x + fullSize.width - (traySize.width + cardSize.width) / 2, offsetY)
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
        } else {
            // The stock and waste are put below the pyramid on the top-end
            // and the discard is at the bottom-end
            val endOffsetX = pyramidOffset.x + fullSize.width - (traySize.width - cardSize.width) / 2
            val stockWidth = cardSize.width + traySize.width + stockPadding
            val offsetY = pyramidOffset.y + (traySize.height - cardSize.height) / 2
            dealer.cards[STOCK_GROUP].let {
                it.offset = DpOffset(x = endOffsetX - stockWidth, y = offsetY)
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
            dealer.cards[WASTE_GROUP].let {
                it.offset = DpOffset(x = endOffsetX - cardSize.width, y = offsetY)
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
            dealer.cards[DISCARD_GROUP].let {
                it.offset = DpOffset(
                    x = endOffsetX - cardSize.width,
                    y = pyramidOffset.y + pyramidSize.height - cardSize.height - (traySize.height - cardSize.height) / 2
                )
                // Spacing is 0 to make the group a stack
                it.spacing = DpSize.Zero
            }
        }
    }

    @Composable
    private fun Tray(group: CardGroup) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset(x = group.offset.x - (traySize.width - cardSize.width) / 2, y = group.offset.y - (traySize.height - cardSize.height) / 2)
                .size(traySize)
                .padding(cardPadding)
        ) {
            Image(
                painterResource(R.drawable.card_tray),
                contentDescription = "",
                contentScale = ContentScale.Fit
            )
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Tray(dealer.cards[STOCK_GROUP])
        Tray(dealer.cards[WASTE_GROUP])
        Tray(dealer.cards[DISCARD_GROUP])

        // Draw each group where it belongs
        for (i in 0..<GROUP_COUNT) {
            dealer.cards[i].Content(
                dealer.game,
                cardPadding = PaddingValues(cardPadding),
                gestures = {
                    dragAndDropCard(it).clickableCard(it)
                }
            )
        }

        DragContent(cardPadding)
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
            return if (position == dealer.cards[group].cards.lastIndex) this else null
        if (group >= ROW_COUNT - 1)
            return null
        // Get the row below the card's row
        val cover = dealer.cards[group + 1]
        // If there is a card a position or position + 1 then this card is not playable
        return if (cover.cards.getOrNull(position)?.card != null || cover.cards.getOrNull(position + 1)?.card != null)
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
            dealer.drawables[i].card.let {
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
        if (cardValue == CARDS_PER_SUIT - 1) {
            return if (playable() != null)
                this
            else
                null
        }

        val match = processMatches {
            it != this && it.highlight == HIGHLIGHT_SELECTED
        }

        // No matches with this sum to 13
        return match?.let {
            if (playable(it))
                it
            else
                null
        }
    }

    private fun Card.playable(card: Card): Boolean {
        // Check whether either or both cards are playable
        val matchPlayable = card.playable() != null
        if (playable() != null) {
            if (matchPlayable)
                return true         // Both cards are playable
        } else if (!matchPlayable)
            return false           // Neither card is playable
        if (!playPartialCover)
            return false           // Don't allow partial covers
        else if (group >= ROW_COUNT || card.group >= ROW_COUNT)
            return false           // Partial cover only works if both cards are in the pyramid

        // One of the cards is playable, one isn't; note which is which
        val playCard: Card
        val notPlayCard: Card
        if (matchPlayable) {
            playCard = card
            notPlayCard = this
        } else {
            playCard = this
            notPlayCard = card
        }

        // These two cards can be played only if the playCard
        // is the only card that covers the notPlayCard
        // First is the playCard in the group below the notPlayCard
        return notPlayCard.group + 1 == playCard.group &&
            notPlayCard.position.let {pos ->
                // Either playCard is at position and the card at position + 1 is null
                (pos == playCard.position && dealer.cards[playCard.group].cards.getOrNull(playCard.position + 1) == null) ||
                    // or playCard is at position + 1 and the card at position is null
                    (pos + 1 == playCard.position && dealer.cards[playCard.group].cards[playCard.position - 1] == null)
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
        if (dealer.cards[0].cards.isEmpty() && clearPyramidOnly) {
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
            return
        }

        val stock = dealer.cards[STOCK_GROUP]
        val waste = dealer.cards[WASTE_GROUP]

        // If there are cards in the stock, the game isn't over
        if (stock.cards.isNotEmpty())
            return

        // Array of ways to sum cards to 13
        val sum13 = IntArray((CARDS_PER_SUIT + 1) / 2)
        sum13[0] = LESS_THAN_7
        val playableCards = mutableMapOf<Int, Card>()
        // Include top card of stock and waste
        if (stock.cards.lastOrNull()?.card?.markSum(sum13, playableCards) == true ||
            waste.cards.lastOrNull()?.card?.markSum(sum13, playableCards) == true)
            return      // Sum to 13, game isn't over
        // Loop through the cards in pyramid
        (0..<ROW_COUNT).forEach {group ->
            val row = dealer.cards[group]
            // Check whether we have a pair or playable cards that sum to 13
            row.cards.forEach {
                if (it?.card?.playable()?.markSum(sum13, playableCards) == true)
                    return      // Sum to 13 game isn't over
            }
        }

        // Now look for partially covered plays
        if (playPartialCover) {
            playableCards.values.forEach { card ->
                if (card.group > 0) {
                    val group = dealer.cards[card.group].cards
                    // First check the card covered on the right side
                    if (card.position < group.lastIndex && group[card.position] == null) {
                        // Calculate sum
                        val sum = dealer.cards[card.group - 1].cards[card.position]!!.card.value + card.value
                        if (sum % CARDS_PER_SUIT == CARDS_PER_SUIT - 1)
                            return              // Sum to 13 - game not over
                    }
                    // Next check the card covered on the left side
                    if (card.position > 0 && group[card.position - 1] == null) {
                        // Calculate sum
                        val sum = dealer.cards[card.group - 1].cards[card.position - 1]!!.card.value + card.value
                        if (sum % CARDS_PER_SUIT == CARDS_PER_SUIT - 2)
                            return              // Sum to 13 - game not over
                    }
                }
            }
        }

        // Can we go through the cards again
        if (dealer.cards[WASTE_GROUP].cards.size > 1 && dealer.cards[STOCK_GROUP].cards.isEmpty() && stockPasses + 1 < stockPassCount)
            return      // Yes, game not over

        // The game is over, did we win
        if (dealer.cards[DISCARD_GROUP].cards.size == CARD_COUNT) {
            // Yes, let the user know
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
        } else {
            // No, let the user know
            dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
        }
    }

    private fun wasteToStock(generation: Long): Boolean {
        val waste = dealer.cards[WASTE_GROUP]
        val wasteSize = waste.cards.size
        // Should we take the cards from the waste and try some more
        return(wasteSize > 1 && dealer.cards[STOCK_GROUP].cards.isEmpty() && stockPasses + 1 < stockPassCount).also {
            ++stockPasses
            state.bundle.putInt(STOCK_PASSES, stockPasses)
            state.onBundleUpdated()
            if (it) {
                (2..<wasteSize).forEach {i ->
                    waste.cards[i]?.card?.let { w -> w.changed(generation = generation, group = STOCK_GROUP, position = wasteSize - 1 - w.position, faceDown = true, spread = false) }
                }
                waste.cards[1]?.card?.let { w -> w.changed(generation = generation, group = STOCK_GROUP, position = wasteSize - 1 - w.position, faceDown = false, spread = true) }
            }
        }
    }

    override fun isValid(): String? {
        return when {
            dealer.cards[STOCK_GROUP].cards.any { it == null } -> "Stock has a null card"
            dealer.cards[WASTE_GROUP].cards.any { it == null } -> "Waste has a null card"
            dealer.cards[DISCARD_GROUP].cards.any { it == null } -> "Discard has a null card"
            else -> null
        }
    }

    private fun playCard(card: Card, group: Int, pos: Int, generation: Long) {
        card.changed(generation = generation, group = group,
            position = pos, highlight = Card.HIGHLIGHT_NONE, faceDown = false, spread = group == WASTE_GROUP)
        if (card.group == STOCK_GROUP && dealer.cards[STOCK_GROUP].cards.size > 1) {
            dealer.cards[STOCK_GROUP].let { it.cards[it.cards.lastIndex - 1]?.card?.changed(generation = generation,
                highlight = Card.HIGHLIGHT_NONE, faceDown = false, spread = true) }
        }
    }

    /**  @inheritDoc*/
    override fun cardsUpdated() {
        for (group in dealer.cards)
            group.cardsUpdated(cardSize)
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            dealer.withUndo { generation ->
                val match = card.findPlayable()
                // Is this card playable
                if (match != null) {
                    // Keep track of position for played cards
                    var pos = dealer.cards[DISCARD_GROUP].cards.size
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
                    dealer.cards[WASTE_GROUP].cards.lastOrNull()?.card?.changed(generation = generation, spread = false)
                    playCard(card, WASTE_GROUP, dealer.cards[WASTE_GROUP].cards.size, generation = generation)
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

    @Composable
    private fun Modifier.clickableCard(drawable: CardDrawable): Modifier {
        // Any card in the pyramid can be clicked, only
        // the top card of the stock and waste groups can be clicked and
        // the discard group cannot be clicked
        when {
            (drawable.card.group == STOCK_GROUP) ||
                (drawable.card.group == WASTE_GROUP) -> {
                if (dealer.cards[drawable.card.group].cards.lastIndex != drawable.card.position)
                    return this
            }
            (drawable.card.group >= ROW_COUNT) -> return this
        }

        return with(CardGroup) {
            clickGestures(drawable, this@PyramidGame)
        }
    }

    private fun dropCardCount(drag: CardDrawable, drop: CardDrawable): Int {
        return if (drag.card.value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1 && drag.card.playable() != null && drop.card.group == DISCARD_GROUP)
            1
        else if ((drag.card.value + drop.card.value) % CARDS_PER_SUIT == CARDS_PER_SUIT - 2 && drag.card.playable(drop.card))
            2
        else
            0
    }

    @Composable
    private fun Modifier.dragAndDropCard(drawable: CardDrawable): Modifier {
        // Any card in the pyramid can be a drag source, only
        // the top card of the stock and waste groups can be a drag source and
        // the discard group cannot be a drag source
        when {
            (drawable.card.group == STOCK_GROUP) ||
                (drawable.card.group == WASTE_GROUP) -> {
                if (dealer.cards[drawable.card.group].cards.lastIndex != drawable.card.position)
                    return this
            }
            (drawable.card.group >= ROW_COUNT) -> return this
        }
        return with(CardGroup) {
            dragAndDropCard(drawable, object : DropCard {
                private var filter: ColorFilter? = null
                override val cards: List<CardGroup>
                    get() = dealer.cards

                override fun onStarted(sourceDrawable: CardDrawable, dragDrawables: (List<CardDrawable>) -> List<CardGroup>): List<CardGroup> {
                    return dragDrawables(listOf(sourceDrawable)).also {
                        startDrag(it)
                    }
                }

                override fun onEntered(sourceDrawable: CardDrawable, targetDrawable: CardDrawable) {
                    filter = targetDrawable.colorFilter
                    if (dropCardCount(sourceDrawable, targetDrawable) > 0)
                        targetDrawable.colorFilter = dropFilter
                }

                override fun onExited(sourceDrawable: CardDrawable, targetDrawable: CardDrawable) {
                    targetDrawable.colorFilter = filter
                    filter = null
                }

                override fun onEnded(sourceDrawable: CardDrawable, targetDrawable: CardDrawable?) {
                    endDrag()
                    targetDrawable?.also { dropOn ->
                        val count = dropCardCount(sourceDrawable, dropOn)
                        if (count > 0) {
                            dealer.scope.launch {
                                dealer.withUndo { generation ->
                                    playCard(sourceDrawable.card, DISCARD_GROUP, dealer.cards[DISCARD_GROUP].cards.size, generation)
                                    if (count > 1)
                                        playCard(targetDrawable.card, DISCARD_GROUP, dealer.cards[DISCARD_GROUP].cards.size + 1, generation)
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    companion object {
        private const val ROW_COUNT = 7
        private const val STOCK_GROUP = ROW_COUNT
        private const val WASTE_GROUP = STOCK_GROUP + 1
        private const val DISCARD_GROUP = WASTE_GROUP + 1
        private const val GROUP_COUNT = DISCARD_GROUP + 1

        private const val HIGHLIGHT_SELECTED = 1
        private const val HIGHLIGHT_MATCH = 2

        private const val MORE_THAN_6 = 1
        private const val LESS_THAN_7 = 2
        private const val SUM_13 = MORE_THAN_6 or LESS_THAN_7

        private const val STOCK_PASSES = "stock_passes"
        private const val STOCK_PASS_COUNT = "stock_pass_count"
        private const val CLEAR_PYRAMID_ONLY = "clear_pyramid_only"
        private const val PLAY_PARTIAL_COVER = "play_partial_games"
        private const val SHOW_HIGHLIGHTS = "show_highlights"

        private val selectFilter = BlendModeColorFilter(Color(0x60000000), BlendMode.SrcOver)
        private val matchFilter = BlendModeColorFilter(Color(0x6000FF00), BlendMode.SrcOver)
        private val dropFilter = BlendModeColorFilter(Color(0x600000FF), BlendMode.SrcOver)
    }
}
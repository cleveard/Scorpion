package com.github.cleveard.scorpion.ui.games

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.launch

class PyramidGame(private val dealer: Dealer, state: StateEntity): Game(
    state,
    PyramidGame::class.qualifiedName!!,
    GROUP_COUNT,
    R.string.pyramid
) {
    private val cardPadding = 2.dp
    private val stockPadding = 20.dp
    private var stockPassCount: Int = 1
    private var stockPasses: Int = 0
    private var clearPyramidOnly: Boolean = false
    private var showHighlights: MutableState<Boolean> = mutableStateOf(true)
    private var selectedCard: Card? = null
    override val cardBackAssetPath: String
        get() = dealer.cardBackAssetPath

    override fun cardFrontAssetPath(value: Int): String = dealer.cardFrontAssetPath(value)

    override suspend fun deal(shuffled: IntArray): List<Card> {
        stockPasses = 0
        val list = mutableListOf<Card>()
        var i = 0
        for (group in 0 until ROW_COUNT) {
            for (position in 0 until group + 1)
                list.add(Card(0L, shuffled[i++], group, position, Card.SPREAD))
        }
        val count = i
        while (i < shuffled.size) {
            list.add(Card(0L, shuffled[i], STOCK_GROUP, i - count, Card.FACE_DOWN or Card.HIGHLIGHT_NONE))
            ++i
        }
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

    override fun variantContent(): DialogContent? {
        return null
    }

    override fun settingsContent(): DialogContent? {
        return null
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
        if (group == WASTE_GROUP)
            return if (position == dealer.cards[group].lastIndex) this else null
        // Get the row below the card's row
        val cover = dealer.cards[group + 1]
        // If there is a card a position or position + 1 then this card is not playable
        return if (cover.getOrNull(position) != null || cover.getOrNull(position + 1) != null)
            null
        else
            this
    }

    override suspend fun checkGameOver(generation: Long) {
        fun Card.markSum(sum13: IntArray) {
            // The number value of the card
            val value = value % CARDS_PER_SUIT + 1
            if (value < (CARDS_PER_SUIT + 1) / 2)
            // If it is 6 or less mark it in the appropriate slot
                sum13[value] = sum13[value] or LESS_THAN_7
            else
            // If it is 7 or more put it in the slot that matches the sum
                sum13[CARDS_PER_SUIT - value] = sum13[CARDS_PER_SUIT - value] or MORE_THAN_6
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
        // Include top card of stock and waste
        stock.lastOrNull()?.markSum(sum13)
        waste.lastOrNull()?.markSum(sum13)
        // Loop through the cards in pyramid
        (0..<ROW_COUNT).forEach {group ->
            dealer.cards[group].forEach { it?.playable()?.markSum(sum13) }
        }

        // If both bits are set in any slot, then some pair sums to 13
        if (sum13.any { (it and SUM_13) == SUM_13 })
            return

        // Should we take the cards from the waste and try some more
        if (++stockPasses < stockPassCount) {
            // Yes, move waste to stock
            val wasteSize = dealer.cards[WASTE_GROUP].size
            if (wasteSize > 0) {
                dealer.cards[WASTE_GROUP].forEach {
                    it?.changed(generation = generation, group = STOCK_GROUP, position = wasteSize - 1 - it.position, faceDown = true, spread = false)
                }
                return
            }
        }

        // The game is over, did we win
        if (dealer.cards[DISCARD_GROUP].size == CARD_COUNT) {
            // Yes, let the user know
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
        } else {
            // No, let the user know
            dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
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
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            dealer.withUndo { generation ->
                if (card.group == STOCK_GROUP) {
                    playCard(card, WASTE_GROUP, dealer.cards[WASTE_GROUP].size, generation)
                    selectedCard = null
                    return@withUndo
                }

                val match = selectedCard?.let {
                    // Is the selected card playable and does it match the clicked card
                    if (it.playable() != null && (it.value + card.value) % CARDS_PER_SUIT == CARDS_PER_SUIT - 2)
                        it
                    else
                        null
                }
                selectedCard = null
                // The value within the suit of the card
                val value = card.value % CARDS_PER_SUIT
                // Is this card playable
                if (card.playable() != null) {
                    // Keep track of position for played cards
                    var pos = dealer.cards[DISCARD_GROUP].size
                    // Is the card a king
                    if (value == CARDS_PER_SUIT - 1) {
                        // This is a king, just play it
                        playCard(card, DISCARD_GROUP, pos, generation)
                        return@withUndo
                    } else if (match != null && match != card) {
                        // We have a card and a match, so play them
                        playCard(match, DISCARD_GROUP, pos++, generation)
                        playCard(card, DISCARD_GROUP, pos, generation)
                        return@withUndo
                    }
                }

                // We didn't play the card, so select the card unless it was selected
                if (card.highlight != HIGHLIGHT_SELECTED) {
                    // Select the clicked card
                    card.changed(generation = generation, highlight = HIGHLIGHT_SELECTED)
                    selectedCard = card
                    // find the match. Kings don't have matches
                    if (value < CARDS_PER_SUIT - 1) {
                        // Look for the match value in all suits
                        for (i in CARDS_PER_SUIT - 2 - value..<CARD_COUNT step CARDS_PER_SUIT) {
                            dealer.findCard(i).let {
                                // If the match is faceUp highlight it
                                if (it.faceUp)
                                    it.changed(generation = generation, highlight = HIGHLIGHT_MATCH)
                            }
                        }
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
                selectedCard = null
                if (card.group == STOCK_GROUP) {
                    playCard(card, WASTE_GROUP, dealer.cards[DISCARD_GROUP].size, generation = generation)
                }
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

        val selectFilter = BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply)
        val matchFilter = BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply)

    }
}
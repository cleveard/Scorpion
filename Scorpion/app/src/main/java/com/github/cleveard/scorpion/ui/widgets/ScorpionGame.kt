package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.State
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.Game
import kotlinx.coroutines.launch

class ScorpionGame(private val dealer: Dealer) : Game {
    private val cardLayout: CardLayout = CardLayout()
    private val padding: Dp = Dp(2.0f)

    private val cards: MutableList<SnapshotStateList<Card>> = mutableListOf<SnapshotStateList<Card>>().apply {
        repeat(COLUMN_COUNT + 1) {
            add(mutableStateListOf())
        }
    }

    override val measurements: LayoutMeasurements = LayoutMeasurements().apply {
        verticalSpacing.minimum = Dp(0.3f * 160.0f)
        verticalSpacing.ratio = 0.15f
        horizontalSpacing.minimum = Dp(0.3f * 160.0f)
        horizontalSpacing.ratio = 0.15f
    }
    private val cardBack: MutableState<String> = mutableStateOf("red.svg")
    override val cardBackAssetName: String
        get() = cardBack.value
    private var highlighted = mutableListOf<Card>()

    override suspend fun deal(shuffled: List<Card>): State {
        for (c in cards)
            c.clear()

        val iterator = shuffled.iterator()
        for (group in 0 until COLUMN_COUNT) {
            for (position in 0 until CARDS_PER_COLUMN) {
                val card = iterator.next()
                card.group = group
                card.position = position
                card.faceDown = group < cardLayout.hiddenCardColumnCount && position < CARDS_FACE_DOWN
                card.spread = true
                card.highlight = Card.HIGHLIGHT_NONE
            }
        }
        var position = 0
        while (iterator.hasNext()) {
            val card = iterator.next()
            card.group = COLUMN_COUNT
            card.position = position++
            card.faceDown = true
            card.spread = false
        }

        setCards(shuffled)

        return State(0L, ScorpionGame::class.qualifiedName!!)
    }

    override fun setCards(cardList: List<Card>) {
        clearHighlights()

        val oldList: MutableList<Card> = mutableListOf()
        val sorted = cardList.sortedWith(compareBy({ it.group }, { it.position }))
        for (card in sorted) {
            val old = dealer.findCard(card.value)
            if (old === card)
                throw IllegalArgumentException("Cannot update existing cards")
            oldList.add(old)

            val to = cards[card.group]
            if (card.position > to.size)
                throw IllegalArgumentException("Position out of range")
            if (card.position < to.size)
                to[card.position] = card
            else
                to.add(card)
            if (card.highlight != Card.HIGHLIGHT_NONE)
                highlighted.add(card)
        }

        oldList.sortWith(compareBy({ it.group }, { -it.position }))
        for (card in oldList) {
            if (card.highlight != Card.HIGHLIGHT_NONE)
                highlighted.remove(card)
            val to = cards[card.group]
            if (card.position < to.size && to[card.position] === card) {
                if (card.position == to.lastIndex)
                    to.removeAt(card.position)
                else
                    throw IllegalArgumentException("Card mispositioned")
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        measurements.verticalSpacing.size = 333.dp
        measurements.horizontalSpacing.size = 234.dp

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
        ) {
            val twoRows = maxHeight > maxWidth
            val cols = if (twoRows) cards.size - 1 else cards.size
            val colWidth = maxWidth / cols
            measurements.scale = (colWidth - padding) / measurements.horizontalSpacing.size

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (twoRows) {
                    CardGroup.RowContent(
                        cards[cards.kitty],
                        this@ScorpionGame,
                        modifier = Modifier
                            .height(measurements.verticalSpacing.size * measurements.scale + padding)
                            .align(Alignment.End)
                            .padding(padding)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Start)
                ) {
                    for (group in cards.indices) {
                        if (group != cards.kitty || !twoRows) {
                            CardGroup.ColumnContent(
                                cards[group],
                                this@ScorpionGame,
                                modifier = Modifier
                                    .width(colWidth)
                                    .align(Alignment.Top)
                                    .padding(padding)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun getFilter(highlight: Int): ColorFilter? {
        return filters[highlight]
    }

    override fun isClickable(card: Card): Boolean {
        return when {
            card.faceDown -> {
                if (card.group == cards.kitty)
                    card.spread
                else
                    card.position == cards[card.group].lastIndex
            }
            else -> true
        }
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            if (isClickable(card)) {
                val flags = card.flags.value and Card.HIGHLIGHT_MASK
                clearHighlights()
                if (card.faceDown) {
                    card.faceDown = false
                } else if (flags != HIGHLIGHT_ONE_LOWER || !withUndo {
                            checkAndMove(card)
                        }) {
                    if (flags != HIGHLIGHT_SELECTED) {
                        highlight(card, HIGHLIGHT_SELECTED)
                        findOneLower(card.value)?.let {
                            highlight(it, HIGHLIGHT_ONE_LOWER)
                        }
                        findOneHigher(card.value)?.let {
                            highlight(it, HIGHLIGHT_ONE_HIGHER)
                        }
                    }
                }
            }
        }
    }

    override fun onDoubleClick(card: Card) {
        dealer.scope.launch {
            withUndo {
                clearHighlights()
                checkAndMove(card)
            }
        }
    }

    private suspend fun <T> withUndo(actions: suspend () -> T): T {
        return dealer.withUndo {
            actions()
        }
    }
    private fun checkAndMove(card: Card): Boolean {
        if (card.value % Game.CARDS_PER_SUIT == Game.CARDS_PER_SUIT - 1) {
            val moveAlone = cardLayout.kingMovesAlone && card.position < cards[card.group].lastIndex &&
                cards[card.group][card.position + 1].value != card.value - 1
            if (card.position != 0 || moveAlone) {
                for (empty in 0 until cards.lastIndex) {
                    if (cards[empty].isEmpty()) {
                        moveCards(card, empty, moveAlone)
                        return true
                    }
                }
            }
        } else {
            findOneHigher(card.value)?.let {
                val to = cards[it.group]
                val c = to[it.position]
                if (it.group != card.group && it.position == to.lastIndex && c.faceUp) {
                    moveCards(card, it.group, card.group == cards.kitty)
                    return true
                }
            }
        }

        return false
    }

    private fun clearHighlights() {
        for (c in highlighted) {
            c.highlight = 0
            dealer.cardChanged(c)
        }
        highlighted.clear()
    }

    private fun calcNoMoves(): Boolean {
        for (group in cards.indices) {
            if (group != cards.kitty) {
                val c = cards[group]
                if (c.isNotEmpty()) {
                    val card = c.last()
                    if (card.faceDown)
                        return false
                    findOneLower(card.value)?.let {
                        if (it.faceUp && it.group != group)
                            return@calcNoMoves false
                    }
                }
            }
        }

        for (card in cards[cards.kitty]) {
            if (card.faceDown && card.spread)
                return false
        }
        return true
    }

    private fun highlight(card: Card, highlight: Int) {
        if (card.faceUp) {
            card.highlight = highlight
            highlighted.add(card)
        }
    }

    private fun moveCards(card: Card, toGroup: Int, single: Boolean) {
        if (card.group == toGroup)
            throw IllegalArgumentException("Can only move between different groups")
        val from = cards[card.group]
        val to = cards[toGroup]
        val range = if (single)
            card.position..card.position
        else
            card.position until from.size
        for (i in range) {
            val c = from[i]
            c.group = toGroup
            c.position = to.size
            to.add(c)
            dealer.cardChanged(c)
        }

        for (i in range.reversed()) {
            from.removeAt(i)
        }

        for (i in range.first until from.size) {
            from[i].position = i
            dealer.cardChanged(from[i])
        }

        if (calcNoMoves())
            spreadKitty()
    }

    private fun findOneLower(cardValue: Int): Card? {
        val value = cardValue % Game.CARDS_PER_SUIT
        return if (value != 0)
            dealer.findCard(cardValue - 1)
        else
            null
    }

    private fun findOneHigher(cardValue: Int): Card? {
        val value = cardValue % Game.CARDS_PER_SUIT
        return if (value != Game.CARDS_PER_SUIT - 1)
            dealer.findCard(cardValue + 1)
        else
            null
    }

    private fun spreadKitty() {
        val kitty = cards[cards.kitty]
        for (card in kitty) {
            card.spread = true
            dealer.cardChanged(card)
        }
    }

    data class CardLayout(
        var hiddenCardColumnCount: Int = 3,
        var kingMovesAlone: Boolean = true,
        var undoCanUndoReveal: Boolean = false
    )

    companion object {
        const val COLUMN_COUNT: Int = 7
        const val CARDS_PER_COLUMN: Int = 7
        const val CARDS_FACE_DOWN: Int = 3

        const val HIGHLIGHT_SELECTED: Int  = 1
        const val HIGHLIGHT_ONE_LOWER: Int  = 2
        const val HIGHLIGHT_ONE_HIGHER: Int  = 3

        val List<List<Card>>.kitty: Int
            get() = lastIndex

        val filters: List<ColorFilter?> = listOf(
            null,
            BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFFFA0A0), BlendMode.Multiply)
        )
    }
}
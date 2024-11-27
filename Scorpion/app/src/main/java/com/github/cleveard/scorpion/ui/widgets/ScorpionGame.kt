package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.Actions
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.Game

class ScorpionGame(private val dealer: Dealer) : Game, Actions {
    private val cardLayout: CardLayout = CardLayout()
    private val padding: Dp = Dp(2.0f)

    private val cards: MutableList<SnapshotStateList<CardEntity>> = mutableListOf<SnapshotStateList<CardEntity>>().apply {
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
    private var highlighted = mutableListOf<CardEntity>()

    init {
        deal()
    }

    override fun deal() {
        val shuffled = dealer.shuffle()

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

        restart(shuffled)
    }

    override fun restart(cardList: List<CardEntity>) {
        val iterator = cardList.sortedWith(compareBy({ it. group }, { it.position })).iterator()
        for (group in 0 until COLUMN_COUNT) {
            val list = cards[group].apply { clear() }
            for (position in 0 until CARDS_PER_COLUMN) {
                val card = iterator.next()
                card.group = group
                card.position = position
                list.add(card)
            }
        }
        var position = 0
        val list = cards[COLUMN_COUNT].apply { clear() }
        while (iterator.hasNext()) {
            val card = iterator.next()
            card.group = COLUMN_COUNT
            card.position = position++
            list.add(card)
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        measurements.verticalSpacing.size = 333.dp
        measurements.horizontalSpacing.size = 234.dp

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val twoRows = maxHeight > maxWidth
            val cols = if (twoRows) cards.size - 1 else cards.size
            val colWidth = maxWidth / cols
            measurements.scale = (colWidth - padding) / measurements.horizontalSpacing.size

            if (twoRows) {
                CardGroup.RowContent(
                    cards[cards.kitty],
                    this@ScorpionGame,
                    modifier = Modifier
                        .height(measurements.verticalSpacing.size * measurements.scale + padding)
                        .align(Alignment.TopEnd)
                        .padding(padding)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .let {
                        if (twoRows)
                            it.offset(y = measurements.verticalSpacing.size * measurements.scale + padding)
                        else
                            it
                    }
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

    override fun getFilter(highlight: Int): ColorFilter? {
        return filters[highlight]
    }

    override fun isClickable(card: CardEntity): Boolean {
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

    override fun onClick(card: CardEntity) {
        if (isClickable(card)) {
            val flags = card.flags.value and CardEntity.HIGHLIGHT_MASK
            clearHighlights()
            if (card.faceDown)
                card.faceDown = false
            else if (flags == HIGHLIGHT_ONE_LOWER) {
                checkAndMove(card)
            } else if (flags != HIGHLIGHT_SELECTED) {
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

    override fun onDoubleClick(card: CardEntity) {
        clearHighlights()
        checkAndMove(card)
    }

    private fun checkAndMove(card: CardEntity) {
        if (card.value % Game.CARDS_PER_SUIT == Game.CARDS_PER_SUIT - 1) {
            for (empty in 0 until cards.lastIndex) {
                if (cards[empty].isEmpty()) {
                    moveCards(card, empty, true)
                    break
                }
            }
        } else
            findOneHigher(card.value)?.let {
                val to = cards[it.group]
                val c = to[it.position]
                if (it.group != card.group && it.position == to.lastIndex && c.faceUp) {
                    moveCards(card, it.group, card.group == cards.kitty)
                }
            }
    }

    private fun clearHighlights() {
        for (c in highlighted)
            c.highlight = 0
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

    private fun highlight(card: CardEntity, highlight: Int) {
        if (card.faceUp) {
            card.highlight = highlight
            highlighted.add(card)
        }
    }

    private fun moveCards(card: CardEntity, toGroup: Int, single: Boolean) {
        if (card.group == toGroup)
            throw IllegalArgumentException("Can only move between different columns")
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
        }

        for (i in range.reversed()) {
            from.removeAt(i)
        }

        for (i in range.first until from.size)
            from[i].position = i

        if (calcNoMoves())
            spreadKitty()
    }

    private fun findOneLower(cardValue: Int): CardEntity? {
        val value = cardValue % Game.CARDS_PER_SUIT
        return if (value != 0)
            dealer.findCard(cardValue - 1)
        else
            null
    }

    private fun findOneHigher(cardValue: Int): CardEntity? {
        val value = cardValue % Game.CARDS_PER_SUIT
        return if (value != Game.CARDS_PER_SUIT - 1)
            dealer.findCard(cardValue + 1)
        else
            null
    }

    private fun spreadKitty() {
        val kitty = cards[cards.kitty]
        for (card in kitty)
            card.spread = true
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

        val List<List<CardEntity>>.kitty: Int
            get() = lastIndex

        val filters: List<ColorFilter?> = listOf(
            null,
            BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFFFA0A0), BlendMode.Multiply)
        )
    }
}
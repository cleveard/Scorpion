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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.Game

class ScorpionGame : Game, CardGroup.Actions {
    private val cardLayout: CardLayout = CardLayout()
    private val padding: Dp = Dp(2.0f)

    private val cards: MutableList<SnapshotStateList<Int>> = mutableListOf<SnapshotStateList<Int>>().apply {
        repeat(cardLayout.COLUMN_COUNT + 1) {
            add(mutableStateListOf())
        }
    }
    private val places: List<ColumnRow> = List(cardLayout.CARD_COUNT) { ColumnRow() }

    private val measurements: LayoutMeasurements = LayoutMeasurements().apply {
        verticalSpacing.minimum = Dp(0.3f * 160.0f)
        verticalSpacing.ratio = 0.15f
        horizontalSpacing.minimum = Dp(0.3f * 160.0f)
        horizontalSpacing.ratio = 0.15f
    }

    private val noMoves = mutableStateOf( false )
    private var highlighted = mutableListOf<Pair<Int, Int>>()

    init {
        deal()
    }

    override fun deal() {
        val shuffled = IntArray(cardLayout.CARD_COUNT) { it }.let {
            it.shuffle()
            it.toList()
        }

        for (c in cards)
            c.clear()

        var col = 0
        var row = 0
        var list: SnapshotStateList<Int> = cards[0]
        for (card in shuffled) {
            if (row >= cardLayout.CARDS_PER_COLUMN) {
                list = cards[++col]
                row = 0
            }
            places[card].let {
                it.col = col
                it.row = row
            }
            list.add(
                if ((col < cardLayout.hiddenCardColumnCount || col >= cardLayout.COLUMN_COUNT) &&
                    row < cardLayout.CARDS_FACE_DOWN)
                    card or CardGroup.FACE_DOWN
                else
                    card
            )
            ++row
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
            val cols = if (twoRows) cards.lastIndex else cards.size
            val colWidth = maxWidth / cols
            measurements.scale = (colWidth - padding * 2) / measurements.horizontalSpacing.size

            if (twoRows) {
                CardGroup.Content(
                    cards,
                    cards.lastIndex,
                    this@ScorpionGame,
                    modifier = Modifier
                        .height(measurements.verticalSpacing.size * measurements.scale)
                        .align(Alignment.TopEnd)
                        .padding(padding),
                    cardBackAssetName = { "red.svg" },
                    spreadCardsHorizontally = { true },
                    spreadFaceDownCards = { noMoves.value }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .let {
                        if (twoRows)
                            it.offset(y = measurements.verticalSpacing.size * measurements.scale + padding * 2.0f)
                        else
                            it
                    }
            ) {
                for (col in cards.indices) {
                    if (col < cards.lastIndex || !twoRows) {
                        CardGroup.Content(
                            cards,
                            col,
                            this@ScorpionGame,
                            modifier = Modifier
                                .width(colWidth)
                                .align(Alignment.Top)
                                .padding(padding),
                            cardBackAssetName = { "red.svg" },
                            measurements = measurements,
                            spreadCardsHorizontally = { false },
                            spreadFaceDownCards = {
                                if (col == cards.lastIndex)
                                    noMoves.value
                                else
                                    true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun isClickable(list: List<MutableList<Int>>, col: Int, row: Int): Boolean {
        val card = list[col][row]
        return when {
            (card and CardGroup.FACE_DOWN) != 0 -> {
                if (col == list.lastIndex)
                    noMoves.value && row == list[col].lastIndex
                else
                    row == list[col].lastIndex
            }
            else -> true
        }
    }

    override fun onClick(list: List<MutableList<Int>>, col: Int, row: Int) {
        if (isClickable(list, col, row)) {
            val card = list[col][row]
            clearHighlights()
            if ((card and CardGroup.FACE_DOWN) != 0)
                list[col][row] = card and CardGroup.FACE_DOWN.inv()
            else if ((card and CardGroup.ONE_LOWER) != 0) {
                checkAndMove(list, col, row)
            } else if ((card and CardGroup.SELECTED) == 0) {
                highlight(list, col, row, CardGroup.SELECTED)
                findOneLower(card)?.let {
                    highlight(list, it.second, it.third, CardGroup.ONE_LOWER)
                }
                findOneHigher(card)?.let {
                    highlight(list, it.second, it.third, CardGroup.ONE_HIGHER)
                }
            }
        }
    }

    override fun onDoubleClick(list: List<MutableList<Int>>, col: Int, row: Int) {
        checkAndMove(list, col, row)
    }

    private fun checkAndMove(list: List<MutableList<Int>>, col: Int, row: Int) {
        val card = list[col][row] and CardGroup.CARD_MASK
        if (card % cardLayout.CARDS_PER_SUIT == 12) {
            for (empty in 0 until list.lastIndex) {
                if (list[empty].isEmpty()) {
                    if (cardLayout.kingMovesAlone) {
                        places[card].let {
                            it.col = empty
                            it.row = 0
                        }
                        list[empty].add(list[col][row])
                        list[col].removeAt(row)
                    } else
                        moveCards(list, col, row, empty)
                }
            }
        } else
            findOneHigher(card)?.let {
                val to = list[it.second]
                val c = to[it.third]
                if (it.second != col && it.third == to.lastIndex && (c and CardGroup.FACE_DOWN) == 0) {
                    moveCards(list, col, row, it.second)
                }
            }
    }

    private fun findCard(inCard: Int): Triple<Int, Int, Int> {
        return places[inCard and CardGroup.CARD_MASK].let {
            Triple(cards[it.col][it.row], it.col, it.row)
        }
    }

    private fun findOneLower(inCard: Int): Triple<Int, Int, Int>? {
        val card = inCard and CardGroup.CARD_MASK
        val value = card % cardLayout.CARDS_PER_SUIT
        return if (value != 0)
            findCard(card - 1)
        else
            null
    }

    private fun findOneHigher(inCard: Int): Triple<Int, Int, Int>? {
        val card = inCard and CardGroup.CARD_MASK
        val value = card % cardLayout.CARDS_PER_SUIT
        return if (value != cardLayout.CARDS_PER_SUIT - 1)
            findCard(card + 1)
        else
            null
    }

    private fun clearHighlights() {
        for (c in highlighted) {
            val card = cards[c.first][c.second] and
                CardGroup.SELECTED.inv() and
                CardGroup.ONE_LOWER.inv() and
                CardGroup.ONE_HIGHER.inv()
            cards[c.first][c.second] = card
        }
        highlighted.clear()
    }

    private fun calcNoMoves(): Boolean {
        for (col in cards.indices) {
            val c = cards[col]
            if (c.isNotEmpty()) {
                val card = c.last()
                if ((card and CardGroup.FACE_DOWN) != 0) {
                    return col == cards.lastIndex
                }
                findOneLower(card)?.let {
                    if ((it.first and CardGroup.FACE_DOWN) == 0 && it.second != col)
                        return@calcNoMoves false
                }
            }
        }
        return true
    }

    private fun highlight(list: List<MutableList<Int>>, col: Int, row: Int, flag: Int) {
        val c = list[col][row]
        if ((c and CardGroup.FACE_DOWN) == 0) {
            list[col][row] = c or flag
            highlighted.add(Pair(col, row))
        }
    }

    private fun moveCards(list: List<MutableList<Int>>, fromCol: Int, row: Int, toCol: Int) {
        if (fromCol == toCol)
            throw IllegalArgumentException("Can only move between different columns")
        val from = list[fromCol]
        val to = list[toCol]
        for (i in row until from.size) {
            val c = from[i]
            if ((c and CardGroup.FACE_DOWN) == 0) {
                places[c and CardGroup.CARD_MASK].let {
                    it.col = toCol
                    it.row = to.size
                }
                to.add(c and CardGroup.CARD_MASK)
            }
        }

        var i = row
        while (i < from.size) {
            val c = from[i]
            if ((c and CardGroup.FACE_DOWN) == 0)
                from.removeAt(i)
            else {
                places[c and CardGroup.CARD_MASK].row = i++
            }
        }

        noMoves.value = calcNoMoves()
    }

    data class ColumnRow(var col: Int = 0, var row: Int = 0)

    @Suppress("PropertyName")
    companion object {
        class CardLayout(
            val CARDS_PER_SUIT: Int = 13,
            val SUIT_COUNT: Int = 4,
            val CARD_COUNT: Int = CARDS_PER_SUIT * SUIT_COUNT,
            val COLUMN_COUNT: Int = 7,
            val CARDS_PER_COLUMN: Int = 7,
            val CARDS_FACE_DOWN: Int = 3,
            var hiddenCardColumnCount: Int = 3,
            var kingMovesAlone: Boolean = true,
            var undoCanUndoReveal: Boolean = false
        )

    }
}
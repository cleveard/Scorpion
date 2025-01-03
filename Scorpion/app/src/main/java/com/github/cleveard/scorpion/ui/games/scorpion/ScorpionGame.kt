package com.github.cleveard.scorpion.ui.games.scorpion

import android.os.Bundle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.R
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.Game
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.widgets.LayoutMeasurements
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.launch

class ScorpionGame(private val dealer: Dealer, private val bundle: Bundle) : Game {
    private val cardLayout: CardLayout = CardLayout()
    private val padding: Dp = Dp(2.0f)
    private val showHighlights: MutableState<Boolean> = mutableStateOf(bundle.getBoolean(SHOW_HIGHLIGHTS, true))
    private var cheatCardFlip: Boolean = false

    private val settingsContent = object: DialogContent {
        val showHints = mutableStateOf(false)
        var cheatFlip = mutableStateOf(false)
        @Composable
        override fun Content(modifier: Modifier) {
            HorizontalDivider()
            TextSwitch(
                showHints.value,
                R.string.show_highlights,
                onChange = { showHints.value = it }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cheats)
            )
            HorizontalDivider(Modifier.padding(top = 4.dp))
            TextSwitch(
                cheatFlip.value,
                R.string.cheat_card_flip,
                onChange = { cheatFlip.value = it }
            )
        }

        override fun onDismiss() {
        }

        override fun onAccept() {
            cheatCardFlip = cheatFlip.value

            var update = false
            update = (showHints.value != showHighlights.value).also {
                showHighlights.value = showHints.value
                bundle.putBoolean(SHOW_HIGHLIGHTS, showHints.value)
            } || update

            if (update)
                dealer.onStateChanged()
        }

        fun reset() {
            showHints.value = showHighlights.value
            cheatFlip.value = cheatCardFlip
        }
    }

    override val measurements: LayoutMeasurements = LayoutMeasurements().apply {
        verticalSpacing.minimum = Dp(0.3f * 160.0f)
        verticalSpacing.ratio = 0.15f
        horizontalSpacing.minimum = Dp(0.3f * 160.0f)
        horizontalSpacing.ratio = 0.15f
    }
    override val cardBackAssetPath: String
        get() = dealer.cardBackAssetPath
    override val name: String
        get() = ScorpionGame::class.qualifiedName!!
    override val groupCount: Int
        get() = COLUMN_COUNT + 1

    override fun cardFrontAssetPath(value: Int): String {
        return dealer.cardFrontAssetPath(value)
    }

    override suspend fun deal(shuffled: IntArray): List<CardEntity> {
        val list = mutableListOf<CardEntity>()
        val iterator = shuffled.iterator()
        for (group in 0 until COLUMN_COUNT) {
            for (position in 0 until CARDS_PER_COLUMN) {
                val card = iterator.next()
                list.add(CardEntity(0L, card, group, position, Card.calcFlags(
                    faceDown = group < cardLayout.hiddenCardColumnCount && position < CARDS_FACE_DOWN, spread = true)))
            }
        }
        var position = 0
        while (iterator.hasNext()) {
            val card = iterator.next()
            list.add(CardEntity(0L, card, COLUMN_COUNT, position++, Card.calcFlags(faceDown = true)))
        }

        return list
    }

    @Composable
    override fun Content(modifier: Modifier) {
        measurements.verticalSpacing.size = dealer.cardHeight.dp
        measurements.horizontalSpacing.size = dealer.cardWidth.dp

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
        ) {
            val twoRows = maxHeight > maxWidth
            val cols = if (twoRows) dealer.cards.size - 1 else dealer.cards.size
            val colWidth = maxWidth / cols
            measurements.scale = (colWidth - padding) / measurements.horizontalSpacing.size

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (twoRows) {
                    CardGroup.RowContent(
                        dealer.cards[dealer.cards.kitty],
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
                    for (group in dealer.cards.indices) {
                        if (group != dealer.cards.kitty || !twoRows) {
                            CardGroup.ColumnContent(
                                dealer.cards[group],
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

    override fun variantContent(): DialogContent? {
        return null
    }

    override fun settingsContent(): DialogContent? {
        settingsContent.reset()
        return settingsContent
    }

    override fun getFilter(highlight: Int): ColorFilter? {
        if (highlight != HIGHLIGHT_SELECTED && !showHighlights.value)
            return null
        return filters[highlight]
    }

    override fun isClickable(card: Card): Boolean {
        return when {
            card.faceDown -> {
                if (card.group == dealer.cards.kitty)
                    card.spread || cheatCardFlip
                else
                    card.position == dealer.cards[card.group].lastIndex ||
                        (cheatCardFlip && dealer.cards[card.group][card.position + 1]?.faceUp != false)
            }
            else -> true
        }
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            withUndo { generation ->
                if (isClickable(card)) {
                    val highlight = card.highlight
                    if (card.faceDown) {
                        dealer.cardChanged(card.toEntity(generation = generation, faceDown = false, spread = true))
                        cheatCardFlip = false
                    } else if (highlight != HIGHLIGHT_ONE_LOWER || !checkAndMove(card, generation)) {
                        if (highlight != HIGHLIGHT_SELECTED) {
                            highlight(card, HIGHLIGHT_SELECTED)
                            findOneLower(card.value)?.let {
                                if (card.group != it.group || card.position != it.position - 1)
                                    highlight(it, HIGHLIGHT_ONE_LOWER)
                            }
                            findOneHigher(card.value)?.let {
                                if (card.group != it.group || card.position != it.position + 1)
                                    highlight(it, HIGHLIGHT_ONE_HIGHER)
                            }
                        }
                    } else
                        cheatCardFlip = false
                }
            }
        }
    }

    override fun onDoubleClick(card: Card) {
        dealer.scope.launch {
            withUndo {generation ->
                if (checkAndMove(card, generation))
                    cheatCardFlip = false
            }
        }
    }

    override suspend fun checkGameOver(list: List<Card>, generation: Long) {
        var last: Card? = null
        var empty = 0
        var kings = 0
        val kitty = mutableListOf<Card>()
        val sorted = list.sortedWith(compareBy( { it.group }, { -it.position }))
        for (i in sorted.indices) {
            val c = sorted[i]
            if (c.group != COLUMN_COUNT) {
                if (last?.group != c.group) {
                    if (c.faceDown)
                        return
                    if (c.value % Game.CARDS_PER_SUIT > 0) {
                        if (list[c.value - 1].let { it.faceUp && it.group != c.group })
                            return
                    }
                }
                if (c.group != (last?.group?: -1))
                    ++empty
                if ((c.value % Game.CARDS_PER_SUIT) == Game.CARDS_PER_SUIT - 1 && c.faceUp &&
                    (c.position > 0 || (i > 0 &&
                        sorted[i - 1].let { it.group == c.group && it.value + 1 != c.value }))
                ) {
                    ++kings
                }
            } else {
                kitty.add(c)
                if (c.faceDown) {
                    if (c.spread)
                        return
                } else if (c.value % Game.CARDS_PER_SUIT == Game.CARDS_PER_SUIT - 1)
                    ++kings
            }
            last = c
        }

        if (kings > 0 && empty < COLUMN_COUNT)
            return

        if (kitty.isEmpty() || kitty[0].spread) {
            for (s in 0 until Game.CARD_COUNT step Game.CARDS_PER_SUIT) {
                val g = list[s].group
                for (c in 0 until Game.CARDS_PER_SUIT) {
                    if (list[s + c].let { it.group != g || it.position != Game.CARDS_PER_SUIT - 1 - c }) {
                        dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
                        return
                    }
                }
            }
            dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
            return
        }

        for (card in kitty) {
            if (!card.spread)
                dealer.cardChanged(card.toEntity(generation = generation, spread = true))
        }
    }

    override fun isValid(cards: List<Card>, card: Card, lastCard: Card?): String? {
        if (card.group < 0 || card.group >= COLUMN_COUNT + 1)
            return "Group invalid"
        return if (card.position !=
            (if (lastCard != null && card.group == lastCard.group) lastCard.position + 1 else 0)
            )
            "Position invalid"
        else
            null
    }

    private suspend fun <T> withUndo(actions: suspend (generation: Long) -> T): T {
        return dealer.withUndo(actions)
    }
    private fun checkAndMove(card: Card, generation: Long): Boolean {
        if (card.value % Game.CARDS_PER_SUIT == Game.CARDS_PER_SUIT - 1) {
            val moveAlone = cardLayout.kingMovesAlone && card.position < dealer.cards[card.group].lastIndex &&
                dealer.cards[card.group][card.position + 1]!!.value != card.value - 1
            if (card.position != 0 || moveAlone) {
                for (empty in 0 until dealer.cards.lastIndex) {
                    if (dealer.cards[empty].isEmpty()) {
                        moveCards(card, empty, generation, moveAlone)
                        return true
                    }
                }
            }
        } else {
            findOneHigher(card.value)?.let {
                val to = dealer.cards[it.group]
                val c = to[it.position]
                if (it.group != card.group && it.position == to.lastIndex && c!!.faceUp) {
                    moveCards(card, it.group, generation, card.group == dealer.cards.kitty)
                    return true
                }
            }
        }

        return false
    }

    private fun highlight(card: Card, highlight: Int) {
        if (card.faceUp)
            dealer.cardChanged(card.toEntity(highlight = highlight))
    }

    private fun moveCards(card: Card, toGroup: Int, generation: Long, single: Boolean) {
        if (card.group == toGroup)
            throw IllegalArgumentException("Can only move between different groups")
        val from = dealer.cards[card.group]
        val to = dealer.cards[toGroup]
        val moveEnd = if (single)
            card.position + 1
        else
            from.size
        var pos = to.size
        for (i in card.position until from.size) {
            if (i < moveEnd)
                dealer.cardChanged(from[i]!!.toEntity(generation = generation, group = toGroup, position = pos++, highlight = Card.HIGHLIGHT_NONE))
            else
                dealer.cardChanged(from[i]!!.toEntity(generation = generation, position = i - 1, highlight = Card.HIGHLIGHT_NONE))
        }
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

        const val SHOW_HIGHLIGHTS: String = "show_highlights"
        const val CHEAT_CARD_FLIP: String = "cheat_card_flip"

        val <T> List<T>.kitty: Int
            get() = lastIndex

        val filters: List<ColorFilter?> = listOf(
            null,
            BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFFFA0A0), BlendMode.Multiply)
        )
    }
}
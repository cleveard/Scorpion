package com.github.cleveard.scorpion.ui.games

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
import androidx.compose.runtime.mutableIntStateOf
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
import com.github.cleveard.scorpion.db.StateEntity
import com.github.cleveard.scorpion.ui.Actions
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.DialogContent
import com.github.cleveard.scorpion.ui.widgets.CardGroup
import com.github.cleveard.scorpion.ui.widgets.LayoutMeasurements
import com.github.cleveard.scorpion.ui.widgets.TextSwitch
import kotlinx.coroutines.launch

@Suppress("unused")
class ScorpionGame(
    private val dealer: Dealer,
    state: StateEntity
): Game(
    state,
    LayoutMeasurements().apply {
        verticalSpacing.minimum = Dp(0.3f * 160.0f)
        verticalSpacing.ratio = 0.15f
        horizontalSpacing.minimum = Dp(0.3f * 160.0f)
        horizontalSpacing.ratio = 0.15f
    },
    ScorpionGame::class.qualifiedName!!,
    GROUP_COUNT,
    R.string.scorpion
), Actions {
    private val padding: Dp = Dp(2.0f)
    private val showHighlights: MutableState<Boolean> = mutableStateOf(state.bundle.getBoolean(SHOW_HIGHLIGHTS, true))
    private var cheatCount: Int = state.bundle.getInt(CHEAT_COUNT, 0)
    private var cheated: Boolean = false
    private var cardChangeCount: Int = 0
    private var cheatCardFlip: Boolean = false
    private var cheatMoveCard: Boolean = false
    private var moveKittyWhenFlipped: Boolean = state.bundle.getBoolean(MOVE_KITTY_WHEN_FLIPPED, false)
    private var hiddenCardColumnCount: Int = state.bundle.getInt(HIDDEN_CARD_COLUMN_COUNT, 3)
    private var kingMovesAlone: Boolean = state.bundle.getBoolean(KING_MOVES_ALONE, true)

    private val variantContent = object: DialogContent {
        val moveKitty = mutableStateOf(false)
        val hiddenCards = mutableIntStateOf(3)
        val kingMoves = mutableStateOf(true)
        @Composable
        override fun Content(modifier: Modifier) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                !moveKitty.value,
                R.string.move_kitty_when_flipped,
                onChange = { moveKitty.value = !it }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                hiddenCards.intValue == 3,
                R.string.hidden_cards_3_columns,
                onChange = { hiddenCards.intValue = if (it) 3 else 4 }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
            TextSwitch(
                kingMoves.value,
                R.string.king_moves_alone,
                onChange = { kingMoves.value = it }
            )
        }

        override suspend fun onDismiss() {
        }

        override suspend fun onAccept() {
            var update = (moveKittyWhenFlipped != moveKitty.value).also {
                if (it) {
                    moveKittyWhenFlipped = moveKitty.value
                    state.bundle.putBoolean(MOVE_KITTY_WHEN_FLIPPED, moveKittyWhenFlipped)
                }
            }
            update = (hiddenCardColumnCount != hiddenCards.value).also {
                if (it) {
                    hiddenCardColumnCount = hiddenCards.value
                    state.bundle.putInt(HIDDEN_CARD_COLUMN_COUNT, hiddenCardColumnCount)
                }
            } || update
            update = (kingMovesAlone != kingMoves.value).also {
                if (it) {
                    kingMovesAlone = kingMoves.value
                    state.bundle.putBoolean(KING_MOVES_ALONE, kingMovesAlone)
                }
            }

            if (update)
                dealer.onStateChanged(state)
        }

        override fun reset() {
            moveKitty.value = moveKittyWhenFlipped
            hiddenCards.intValue = hiddenCardColumnCount
            kingMoves.value = kingMovesAlone
        }
    }

    private val settingsContent = object: DialogContent {
        val showHints = mutableStateOf(false)
        var cheatFlip = mutableStateOf(false)
        var cheatMove = mutableStateOf(false)
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
            HorizontalDivider()
            TextSwitch(
                cheatMove.value,
                R.string.cheat_move_card,
                onChange = { cheatMove.value = it }
            )
        }

        override suspend fun onDismiss() {
        }

        override suspend fun onAccept() {
            cheatCardFlip = cheatFlip.value
            cheatMoveCard = cheatMove.value

            var update = false
            update = (showHints.value != showHighlights.value).also {
                if (it) {
                    showHighlights.value = showHints.value
                    state.bundle.putBoolean(SHOW_HIGHLIGHTS, showHints.value)
                }
            } || update

            if (update)
                dealer.onStateChanged(state)
        }

        override fun reset() {
            showHints.value = showHighlights.value
            cheatFlip.value = cheatCardFlip
            cheatMove.value = cheatMoveCard
        }
    }

    override val cardBackAssetPath: String
        get() = dealer.cardBackAssetPath

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
                    faceDown = group < hiddenCardColumnCount && position < CARDS_FACE_DOWN, spread = true)))
            }
        }
        var position = 0
        while (iterator.hasNext()) {
            val card = iterator.next()
            list.add(CardEntity(0L, card, KITTY_GROUP, position++, Card.calcFlags(faceDown = true)))
        }

        cheatCount = 0
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
                        dealer.cards[KITTY_GROUP],
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
                        if (group != KITTY_GROUP || !twoRows) {
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
        return variantContent
    }

    override fun settingsContent(): DialogContent {
        return settingsContent
    }

    override fun getFilter(highlight: Int): ColorFilter? {
        if (highlight != HIGHLIGHT_SELECTED && !showHighlights.value)
            return null
        return filters[highlight]
    }

    fun isClickable(card: Card, withCheat: Boolean): Boolean {
        return when {
            card.faceDown -> {
                if (card.group == KITTY_GROUP)
                    card.spread || (withCheat && cheatCardFlip)
                else
                    card.position == dealer.cards[card.group].lastIndex ||
                        (withCheat && cheatCardFlip && dealer.cards[card.group][card.position + 1]?.faceUp != false)
            }
            else -> true
        }
    }

    override fun onClick(card: Card) {
        dealer.scope.launch {
            withUndo { generation ->
                val highlight = card.highlight
                if (card.faceDown) {
                    if (card.group == KITTY_GROUP) {
                        if (moveKittyWhenFlipped) {
                            if (dealer.cards[KITTY_GROUP][0]!!.spread) {
                                for (c in dealer.cards[KITTY_GROUP]) {
                                    val group = KITTY_COUNT - 1 - c!!.position
                                    c.changed(generation = generation, group = group, position = dealer.cards[group].size, faceDown = false, spread = true)
                                }
                            } else {
                                val group = KITTY_COUNT - 1 - card.position
                                card.changed(generation = generation, group = group, position = dealer.cards[group].size, faceDown = false, spread = true)
                                cheated = true
                            }
                        } else if (dealer.cards[KITTY_GROUP][0]!!.spread) {
                            card.changed(generation = generation, faceDown = false, spread = true)
                        } else if (cheatCardFlip) {
                            card.changed(generation = generation, faceDown = false, spread = true)
                            cheated = true
                        }
                    } else if (card.position == dealer.cards[card.group].lastIndex) {
                        card.changed(generation = generation, faceDown = false, spread = true)
                    } else if (cheatCardFlip && dealer.cards[card.group][card.position + 1]!!.faceUp) {
                        card.changed(generation = generation, faceDown = false, spread = true)
                        cheated = true
                    }
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
                }
            }
        }
    }

    override fun onDoubleClick(card: Card) {
        dealer.scope.launch {
            withUndo {generation ->
                checkAndMove(card, generation)
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
            if (c.group != KITTY_GROUP) {
                if (last?.group != c.group) {
                    if (c.faceDown)
                        return
                    if (c.value % CARDS_PER_SUIT > 0) {
                        if (list[c.value - 1].let { it.faceUp && it.group != c.group })
                            return
                    }
                }
                if (c.group != (last?.group?: -1))
                    ++empty
                if ((c.value % CARDS_PER_SUIT) == CARDS_PER_SUIT - 1 && c.faceUp &&
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
                } else if (c.value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1)
                    ++kings
            }
            last = c
        }

        if (kings > 0 && empty < COLUMN_COUNT)
            return

        if (kitty.isEmpty() || kitty[0].spread) {
            for (s in 0 until CARD_COUNT step CARDS_PER_SUIT) {
                val g = list[s].group
                for (c in 0 until CARDS_PER_SUIT) {
                    if (list[s + c].let { it.group != g || it.position != CARDS_PER_SUIT - 1 - c }) {
                        dealer.showNewGameOrDismissAlert(R.string.no_moves, R.string.game_over)
                        return
                    }
                }
            }
            if (cheatCount == 0)
                dealer.showNewGameOrDismissAlert(R.string.game_won, R.string.congratulations)
            else
                dealer.showNewGameOrDismissAlert(R.plurals.game_won, R.string.congratulations, cheatCount, cheatCount)
            return
        }

        for (card in kitty) {
            if (!card.spread)
                card.changed(generation = generation, spread = true)
        }
    }

    override fun isValid(cards: List<Card>, card: Card, lastCard: Card?): String? {
        if (card.group < 0 || card.group >= GROUP_COUNT)
            return "Group invalid"
        return if (card.position !=
            (if (lastCard != null && card.group == lastCard.group) lastCard.position + 1 else 0)
            )
            "Position invalid"
        else
            null
    }

    private fun Card.changed(
        generation: Long = this.generation,
        value: Int = this.value,
        group: Int = this.group,
        position: Int = this.position,
        highlight: Int = this.highlight,
        faceDown: Boolean = this.faceDown,
        spread: Boolean = this.spread
    ) {
        cardChangeCount = dealer.cardChanged(toEntity(
            generation = generation,
            value = value,
            group = group,
            position = position,
            highlight = highlight,
            faceDown = faceDown,
            spread = spread
        ))
    }

    private suspend fun <T> withUndo(actions: suspend (generation: Long) -> T): T {
        cheated = false
        cardChangeCount = 0
        return dealer.withUndo {
            actions(it).also {
                if (cardChangeCount > 0) {
                    if (cheated) {
                        cheatCount += 1
                        state.bundle.putInt(CHEAT_COUNT, cheatCount)
                        dealer.onStateChanged(state)
                    }
                    cheatCardFlip = false
                    cheatMoveCard = false
                }
            }
        }
    }
    private fun checkAndMove(card: Card, generation: Long): Boolean {
        if (card.faceUp) {
            if (card.value % CARDS_PER_SUIT == CARDS_PER_SUIT - 1) {
                val moveAlone = kingMovesAlone && card.position < dealer.cards[card.group].lastIndex &&
                    dealer.cards[card.group][card.position + 1]!!.value != card.value - 1
                if (card.position != 0 || moveAlone) {
                    for (empty in 0 until dealer.cards.lastIndex) {
                        if (dealer.cards[empty].isEmpty()) {
                            moveCards(card, empty, -1, generation, moveAlone)
                            return true
                        }
                    }
                }
                findOneLower(card.value)?.let {
                    if (cheatMoveCard && it.faceUp && (it.group != card.group || it.position != card.position + 1)) {
                        moveCards(card, it.group, it.position, generation, true)
                        cheated = true
                    }
                }
            } else {
                findOneHigher(card.value)?.let {
                    val to = dealer.cards[it.group]
                    val c = to[it.position]
                    if (it.group != card.group && it.group != KITTY_GROUP && it.position == to.lastIndex && c!!.faceUp) {
                        moveCards(card, it.group, -1, generation, card.group == KITTY_GROUP)
                        return true
                    } else if (cheatMoveCard && it.faceUp) {
                        moveCards(card, it.group, it.position + 1, generation, true)
                        cheated = true
                    }
                }
            }
        }

        return false
    }

    private fun highlight(card: Card, highlight: Int) {
        if (card.faceUp)
            card.changed(highlight = highlight)
    }

    private fun moveCards(card: Card, toGroup: Int, toPos: Int, generation: Long, single: Boolean) {
        val from = dealer.cards[card.group]
        val to = dealer.cards[toGroup]
        var pos = if (toPos >= 0) toPos else to.size
        if (card.group == toGroup) {
            val moveStart: Int
            val moveEnd: Int
            val bump: Int
            if (pos < card.position) {
                moveStart = pos
                moveEnd = card.position
                bump = 1
            } else if (pos > card.position) {
                --pos
                moveStart = card.position
                moveEnd = pos
                bump = -1
            } else
                return          // Moving to same position, do nothing
            for (i in moveStart .. moveEnd)
                from[i]?.let { it.changed(generation = generation, group = toGroup, position = it.position + bump, highlight = Card.HIGHLIGHT_NONE) }
            card.changed(generation = generation, group = toGroup, position = pos, highlight = Card.HIGHLIGHT_NONE)
        } else {
            val moveEnd = if (single)
                card.position + 1
            else
                from.size
            val moveCount = moveEnd - card.position
            for (i in pos until to.size)
                to[i]?.let { it.changed(generation = generation, group = toGroup, position = it.position + moveCount, highlight = Card.HIGHLIGHT_NONE) }
            for (i in card.position until from.size) {
                if (i < moveEnd)
                    from[i]?.changed(generation = generation, group = toGroup, position = pos++, highlight = Card.HIGHLIGHT_NONE)
                else
                    from[i]?.changed(generation = generation, position = i - 1, highlight = Card.HIGHLIGHT_NONE)
            }
        }
    }

    private fun findOneLower(cardValue: Int): Card? {
        val value = cardValue % CARDS_PER_SUIT
        return if (value != 0)
            dealer.findCard(cardValue - 1)
        else
            null
    }

    private fun findOneHigher(cardValue: Int): Card? {
        val value = cardValue % CARDS_PER_SUIT
        return if (value != CARDS_PER_SUIT - 1)
            dealer.findCard(cardValue + 1)
        else
            null
    }

    companion object {
        private const val COLUMN_COUNT: Int = 7
        private const val KITTY_GROUP: Int = COLUMN_COUNT
        private const val GROUP_COUNT: Int = KITTY_GROUP + 1
        private const val CARDS_PER_COLUMN: Int = 7
        private const val CARDS_FACE_DOWN: Int = 3
        private const val KITTY_COUNT = Game.CARD_COUNT - COLUMN_COUNT * CARDS_PER_COLUMN

        private const val HIGHLIGHT_SELECTED: Int  = 1
        private const val HIGHLIGHT_ONE_LOWER: Int  = 2
        private const val HIGHLIGHT_ONE_HIGHER: Int  = 3

        private const val SHOW_HIGHLIGHTS: String = "show_highlights"
        private const val CHEAT_COUNT: String = "cheat_count"
        private const val MOVE_KITTY_WHEN_FLIPPED: String = "move_kitty_when_flipped"
        private const val HIDDEN_CARD_COLUMN_COUNT: String = "hidden_card_column_count"
        private const val KING_MOVES_ALONE: String = "king_moves_alone"

        private val filters: List<ColorFilter?> = listOf(
            null,
            BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply),
            BlendModeColorFilter(Color(0xFFFFA0A0), BlendMode.Multiply)
        )
    }
}
package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage

/**
 * TODO: document your custom view class.
 */
object CardGroup {
    /** Mask to extract card number from */
    const val CARD_MASK = 0xff
    const val SELECTED = 0x10000
    const val ONE_HIGHER = 0x20000
    const val ONE_LOWER = 0x40000
    const val FACE_DOWN = 0x80000

    private const val ASSET_PATH = "file:///android_asset/"
    private const val FRONT_ASSET_PATH = ASSET_PATH + "cards/fronts/"
    private const val BACK_ASSET_PATH = ASSET_PATH + "cards/backs/"

    /** Drawable ids of the card fronts */
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

    private var normalFilter: BlendModeColorFilter = BlendModeColorFilter(Color(0x00000000), BlendMode.Dst)
    private var selectionFilter: BlendModeColorFilter = BlendModeColorFilter(Color(0xFFA0A0A0), BlendMode.Multiply)
    private var higherFilter: BlendModeColorFilter = BlendModeColorFilter(Color(0xFFFFA0A0), BlendMode.Multiply)
    private var lowerFilter: BlendModeColorFilter = BlendModeColorFilter(Color(0xFFA0FFA0), BlendMode.Multiply)

    @Composable
    fun Content(
        cards: MutableList<SnapshotStateList<Int>>,
        col: Int,
        actions: Actions?,
        modifier: Modifier = Modifier,
        cardBackAssetName: () -> String = { "red.svg" },
        measurements: LayoutMeasurements = LayoutMeasurements(),
        spreadCardsHorizontally: () -> Boolean = { false },
        spreadFaceDownCards: () -> Boolean = { true },
        spreadCardsRightToLeft: () -> Boolean = { true },
        stackFaceUpCardsOnFaceDownCards: () -> Boolean = { false }
    ) {
        val layoutDir = LocalLayoutDirection.current.let {
            if (spreadCardsHorizontally() && spreadCardsRightToLeft()) {
                if (it == LayoutDirection.Ltr)
                    LayoutDirection.Rtl
                else
                    LayoutDirection.Ltr
            } else
                it
        }

        CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
            if (spreadCardsHorizontally()) {
                Row(
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(
                        measurements.horizontalSpacing.spacing - measurements.horizontalSpacing.size * measurements.scale
                    )
                ) {
                    FillImages(
                        cards,
                        col,
                        actions,
                        cardBackAssetName,
                        spreadFaceDownCards,
                        stackFaceUpCardsOnFaceDownCards
                    )
                }
            } else {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(
                        measurements.verticalSpacing.spacing - measurements.verticalSpacing.size * measurements.scale
                    )
                ) {
                    FillImages(
                        cards,
                        col,
                        actions,
                        cardBackAssetName,
                        spreadFaceDownCards,
                        stackFaceUpCardsOnFaceDownCards
                    )
                }
            }
        }
    }

    @Composable
    fun FillImages(
        cards: List<MutableList<Int>>,
        col: Int,
        actions: Actions?,
        cardBackAssetName: () -> String = { "red.svg" },
        spreadFaceDownCards: () -> Boolean = { true },
        stackFaceUpCardsOnFaceDownCards: () -> Boolean = { false }
    )
    {
        val list = cards[col]
        if (list.isNotEmpty()) {
            val firstFront = frontStart(list)
            var start = if (stackFaceUpCardsOnFaceDownCards() && firstFront < list.size)
                firstFront
            else if (spreadFaceDownCards() || firstFront == 0)
                0
            else {
                GetImage(
                    cards,
                    col,
                    firstFront - 1,
                    actions,
                    cardBackAssetName()
                )
                firstFront
            }

            while (start < list.size) {
                GetImage(
                    cards,
                    col,
                    start,
                    actions,
                    cardBackAssetName()
                )
                ++start
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GetImage(
        list: List<MutableList<Int>>,
        col: Int,
        row: Int,
        actions: Actions?,
        cardBackAssetName: String,
        modifier: Modifier = Modifier
    ) {
        val card = list[col][row]
        var filter = normalFilter
        val resourcePath = if ((card and FACE_DOWN) != 0)
            BACK_ASSET_PATH + cardBackAssetName
        else {
            filter = when {
                (card and SELECTED) != 0 -> selectionFilter
                (card and ONE_HIGHER) != 0 -> higherFilter
                (card and ONE_LOWER) != 0 -> lowerFilter
                else -> normalFilter
            }
            FRONT_ASSET_PATH + frontIds[card and CARD_MASK]
        }
        var combinedModifier = modifier
        if (actions?.isClickable(list, col, row) == true)
            combinedModifier = combinedModifier.combinedClickable(
                onClick = { actions.onClick(list, col, row) },
                onDoubleClick = { actions.onDoubleClick(list, col, row) }
            )
        Box() {
            AsyncImage(
                resourcePath,
                contentDescription = "",
                contentScale = ContentScale.Fit,
                modifier = combinedModifier,
                colorFilter = filter,
            )
        }
    }

    private fun frontStart(list: List<Int>): Int {
        return list.indexOfFirst { (it and FACE_DOWN) == 0 }.let {
            if (it < 0)
                list.size
            else
                it
        }
    }

    interface Actions {
        fun isClickable(list: List<MutableList<Int>>, col: Int, row: Int): Boolean
        fun onClick(list: List<MutableList<Int>>, col: Int, row: Int)
        fun onDoubleClick(list: List<MutableList<Int>>, col: Int, row: Int)
    }
}

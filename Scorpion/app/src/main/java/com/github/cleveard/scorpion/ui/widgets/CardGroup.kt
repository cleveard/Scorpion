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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.ui.Game

/**
 * TODO: document your custom view class.
 */
object CardGroup {
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

    @Composable
    fun ColumnContent(
        cards: SnapshotStateList<CardEntity>,
        game: Game,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(
                game.measurements.verticalSpacing.spaceBy()
            )
        ) {
            for (card in cards) {
                if (card.spread) {
                    GetImage(
                        card,
                        game
                    )
                }
            }
        }
    }

    @Composable
    fun RowContent(
        cards: SnapshotStateList<CardEntity>,
        game: Game,
        modifier: Modifier = Modifier
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(
                    game.measurements.horizontalSpacing.spaceBy()
                )
            ) {
                for (card in cards) {
                    if (card.spread || card.position == cards.lastIndex) {
                        GetImage(
                            card,
                            game
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GetImage(
        card: CardEntity,
        game: Game,
        modifier: Modifier = Modifier
    ) {
        var filter: ColorFilter? = null
        val resourcePath = if (card.faceDown)
            BACK_ASSET_PATH + game.cardBackAssetName
        else {
            filter = game.getFilter(card.highlight)
            FRONT_ASSET_PATH + frontIds[card.value]
        }
        var combinedModifier = modifier
        if (game.isClickable(card))
            combinedModifier = combinedModifier.combinedClickable(
                onClick = { game.onClick(card) },
                onDoubleClick = { game.onDoubleClick(card) }
            )
        Box {
            AsyncImage(
                resourcePath,
                contentDescription = "",
                contentScale = ContentScale.Fit,
                modifier = combinedModifier,
                colorFilter = filter,
            )
        }
    }
}

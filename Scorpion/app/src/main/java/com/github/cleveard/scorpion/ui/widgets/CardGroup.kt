package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.ui.games.Game

/**
 * TODO: document your custom view class.
 */
object CardGroup {
    @Composable
    fun ColumnContent(
        cards: List<Card?>,
        game: Game,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(
                game.measurements.verticalSpacing.spaceBy()
            )
        ) {
            val imageModifier = Modifier.size(game.measurements.horizontalSpacing.size * game.measurements.scale,
                game.measurements.verticalSpacing.size * game.measurements.scale)
            for (card in cards) {
                if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                    GetImage(
                        card,
                        game,
                        modifier = imageModifier
                    )
                }
            }
        }
    }

    @Composable
    fun RowContent(
        cards: List<Card?>,
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
                val imageModifier = Modifier.size(game.measurements.horizontalSpacing.size * game.measurements.scale,
                    game.measurements.verticalSpacing.size * game.measurements.scale)
                for (card in cards) {
                    if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                        GetImage(
                            card,
                            game,
                            modifier = imageModifier
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GetImage(
        card: Card?,
        game: Game,
        modifier: Modifier = Modifier
    ) {
        if (card == null)
            return

        var filter: ColorFilter? = null
        val resourcePath = if (card.faceDown)
            game.cardBackAssetPath
        else {
            filter = game.getFilter(card.highlight)
            game.cardFrontAssetPath(card.value)
        }
        val combinedModifier = Modifier.combinedClickable(
                onClick = { game.onClick(card) },
                onDoubleClick = { game.onDoubleClick(card) }
            )
        AsyncImage(
            resourcePath,
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = combinedModifier,
            colorFilter = filter,
        )
    }
}

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
 * Composable functions for a card group
 * Card groups can be laid out in columns or rows
 */
object CardGroup {
    /**
     * Card group laid out in a column
     */
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
            // Set the height and width of the card
            val imageModifier = Modifier.size(game.measurements.horizontalSpacing.size * game.measurements.scale,
                game.measurements.verticalSpacing.size * game.measurements.scale)
            // Add all of the cards to the columns
            for (card in cards) {
                // Only add the card if it is null or the next card is null or the next card has spread set
                // Spread means that the next card should offset from this card, so part of this card is visible.
                // Null values always take up space, though without drawing anything, so if this card is null
                // or the next card is null we need to add the card. Theoretically, nulls at the end of a group
                // could be skipped.
                if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                    // Add the card image
                    GetImage(
                        card,
                        game,
                        modifier = imageModifier
                    )
                }
            }
        }
    }

    /**
     * Card group laid out in a row
     */
    @Composable
    fun RowContent(
        cards: List<Card?>,
        game: Game,
        modifier: Modifier = Modifier
    ) {
        // Always layout the cards left-to-right, because the card value is always on the
        // left side of the card. If the cards overlap, we want the card on top to be offset
        // to the right.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(
                    game.measurements.horizontalSpacing.spaceBy()
                )
            ) {
                // Set the height and width of the card
                val imageModifier = Modifier.size(game.measurements.horizontalSpacing.size * game.measurements.scale,
                    game.measurements.verticalSpacing.size * game.measurements.scale)
                // Add all of the cards to the row
                for (card in cards) {
                    // Only add the card if it is null or the next card is null or the next card has spread set
                    // Spread means that the next card should offset from this card, so part of this card is visible.
                    // Null values always take up space, though without drawing anything, so if this card is null
                    // or the next card is null we need to add the card. Theoretically, nulls at the end of a group
                    // could be skipped.
                    if (card == null || card.position == cards.lastIndex || cards[card.position + 1]?.spread != false) {
                        // Add the card image
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

    /**
     * Add an image for a card to the group
     * @param card The card to add, or null to add a spacer
     * @param game The game interface
     * @param modifier Used to set the width and height of the composable
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GetImage(
        card: Card?,
        game: Game,
        @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier
    ) {
        // If card is null, return, the composable modifier will take up space
        if (card == null)
            return

        // The filter to apply to the image
        var filter: ColorFilter? = null
        // Get the asset path for the image
        val resourcePath = if (card.faceDown)
            game.cardBackAssetPath      // Card back asset path
        else {
            // Get the filter for the highlight
            filter = game.getFilter(card.highlight)
            // Get the card front asset path
            game.cardFrontAssetPath(card.value)
        }
        // Add a onClick and onDoubleClick handlers
        val combinedModifier = Modifier.combinedClickable(
                onClick = { game.onClick(card) },
                onDoubleClick = { game.onDoubleClick(card) }
            )
        // Get the image
        AsyncImage(
            resourcePath,
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = combinedModifier,
            colorFilter = filter,
        )
    }
}

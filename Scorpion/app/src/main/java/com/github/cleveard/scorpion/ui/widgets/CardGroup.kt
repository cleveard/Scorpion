package com.github.cleveard.scorpion.ui.widgets

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import coil3.Image
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.ui.Game
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * TODO: document your custom view class.
 */
object CardGroup {
    var cardWitdh: Int = 234
        private set
    var cardHeight: Int = 333
        private set
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

    val backIds: List<String> = listOf(
        "abstract.svg",
        "abstract_clouds.svg",
        "abstract_scene.svg",
        "astronaut.svg",
        "blue.svg",
        "blue2.svg",
        "cars.svg",
        "castle.svg",
        "fish.svg",
        "frog.svg",
        "red.svg",
        "red2.svg"
    )
    suspend fun preloadCards(context: Context) {
        for (name in frontIds) {
            suspendCoroutine {
                val request = ImageRequest.Builder(context)
                    .data(FRONT_ASSET_PATH + name)
                    .target(object: coil3.target.Target {
                        override fun onError(error: Image?) {
                            throw IllegalArgumentException("Missing asset ${FRONT_ASSET_PATH + name}")
                        }

                        override fun onSuccess(result: Image) {
                            it.resume(Unit)
                        }
                    })
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
        for (name in backIds) {
            suspendCoroutine {
                val request = ImageRequest.Builder(context)
                    .data(BACK_ASSET_PATH + name)
                    .target(object: coil3.target.Target {
                        override fun onError(error: Image?) {
                            throw IllegalArgumentException("Missing asset ${BACK_ASSET_PATH + name}")
                        }

                        override fun onSuccess(result: Image) {
                            cardWitdh = result.width
                            cardHeight = result.height
                            it.resume(Unit)
                        }
                    })
                    .build()
                context.imageLoader.enqueue(request)
            }
        }
    }

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
                if (card?.spread != false || card == cards.last()) {
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
                    if (card?.spread != false || card.position == cards.lastIndex) {
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
            BACK_ASSET_PATH + game.cardBackAssetName
        else {
            filter = game.getFilter(card.highlight)
            FRONT_ASSET_PATH + frontIds[card.value]
        }
        val combinedModifier = if (game.isClickable(card))
            Modifier.combinedClickable(
                onClick = { game.onClick(card) },
                onDoubleClick = { game.onDoubleClick(card) }
            )
        else
            Modifier
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

package com.github.cleveard.scorpion

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.theme.ScorpionTheme
import kotlinx.coroutines.launch


private val BAR_HEIGHT: Dp = Dp(0.3f * 160.0f)

/**
 * The main activity for the application
 */
class MainActivity : ComponentActivity() {
    /** The activity view model */
    private val viewModel by viewModels<MainActivityViewModel>()

    /** @inheritDoc */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the card database
        CardDatabase.initialize(this.applicationContext)
        // Initialize the view model
        viewModel.initialize {
            // Set the content of the application after the view model initializes
            setContent {
                ScorpionPreview(viewModel)
            }
        }
    }
}

/**
 * The application content
 * @param dealer The dealer for the application. Set to null for Android Studio previews.
 */
@PreviewScreenSizes
@Composable
fun ScorpionPreview(dealer: Dealer? = null) {
    // Determine current orientation
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Use the application theme.
    ScorpionTheme(dynamicColor = dealer?.useSystemTheme?: false) {
        // We use BoxWithConstraints only to get the screen height and width
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xff277714))
        ) {
            // This box is used to show dialogs
            Box {
                dealer?.showAlert?.invoke()
            }

            // The toolbar placement depends on the orientation
            val newArea = if (landscape)
                DpSize(maxWidth - BAR_HEIGHT, maxHeight)
            else
                DpSize(maxWidth, maxHeight - BAR_HEIGHT)

            dealer?.let {
                // Recalculate the group and card positions if the playable area size changes
                if (it.playAreaSize.width != newArea.width || it.playAreaSize.height != newArea.height) {
                    it.playAreaSize = newArea
                    it.game.setupGroups()
                    it.game.cardsUpdated()
                }

                // Let the game fill the rest of the screen area
                Box(
                    Modifier.size(it.playAreaSize)
                ) {
                    it.game.Content(
                        Modifier.align(Alignment.TopStart)
                            .size(it.playAreaSize)
                    )
                }
            }

            if (landscape) {
                // In landscape mode the toolbar is a column on the end side
                Column(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width(BAR_HEIGHT)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Put tools into the toolbar
                    ToolContent(true, dealer)
                }
            } else {
                // In portrait mode the tool bar is a row along the bottom of the screen
                Row(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Put tools into the tool bar
                    ToolContent(false, dealer)
                }
            }
        }
    }
}

/**
 * The tools for the toolbar
 * @param landscape True is the orientation is landscape
 * @param dealer The application dealer
 */
@Composable
fun ToolContent(@Suppress("UNUSED_PARAMETER") landscape: Boolean, dealer: Dealer?) {
    // The deal a new game button
    Button(
        onClick = {
            dealer?.scope?.launch {
                dealer.deal()
            }
        },
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(1.dp, 1.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "",
            Modifier.size(BAR_HEIGHT - 8.dp)
        )
    }
    // The show variants dialog button.
    Button(
        onClick = {
            dealer?.scope?.launch {
                dealer.gameVariants()
            }
        },
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(1.dp, 1.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "",
            Modifier.size(BAR_HEIGHT - 8.dp)
        )
    }
    // The undo button
    Button(
        onClick = {
            dealer?.scope?.launch {
                dealer.undo()
            }
        },
        enabled = dealer?.canUndo() != false,
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(1.dp, 1.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.undo_svgrepo_com),
            contentDescription = "",
            Modifier.size(BAR_HEIGHT - 8.dp)
        )
    }
    // The redo button
    Button(
        onClick = {
            dealer?.scope?.launch {
                dealer.redo()
            }
        },
        enabled = dealer?.canRedo() != false,
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(1.dp, 1.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.redo_svgrepo_com),
            contentDescription = "",
            Modifier.size(BAR_HEIGHT - 8.dp)
        )
    }
    // The settings button
    Button(
        onClick = {
            dealer?.scope?.launch {
                dealer.settings()
            }
        },
        contentPadding = PaddingValues(4.dp),
        modifier = Modifier.defaultMinSize(1.dp, 1.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "",
            Modifier.size(BAR_HEIGHT - 8.dp)
        )
    }
}

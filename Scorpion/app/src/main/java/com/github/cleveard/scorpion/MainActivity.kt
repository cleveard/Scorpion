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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.cleveard.scorpion.db.CardDatabase
import com.github.cleveard.scorpion.ui.Dealer
import com.github.cleveard.scorpion.ui.theme.ScorpionTheme
import kotlinx.coroutines.launch


private val BAR_HEIGHT: Dp = Dp(0.3f * 160.0f)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CardDatabase.initialize(this.applicationContext)
        viewModel.initialize {
            // enableEdgeToEdge()
            setContent {
                ScorpionPreview(viewModel)
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ScorpionPreview(dealer: Dealer? = null) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ScorpionTheme(dynamicColor = dealer?.useSystemTheme?: false) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xff277714))
        ) {
            Box {
                dealer?.showAlert?.invoke()
            }

            if (landscape) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width(BAR_HEIGHT)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolContent(true, dealer)
                }
                dealer?.game?.Content(Modifier.align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(maxWidth - BAR_HEIGHT)
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolContent(false, dealer)
                }
                dealer?.game?.Content(Modifier.align(Alignment.TopStart)
                    .height(maxHeight - BAR_HEIGHT)
                    .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ToolContent(landscape: Boolean, dealer: Dealer?) {
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

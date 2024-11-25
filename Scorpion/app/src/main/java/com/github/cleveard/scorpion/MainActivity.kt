package com.github.cleveard.scorpion

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.Dp
import com.github.cleveard.scorpion.ui.theme.ScorpionTheme


private val BAR_HEIGHT: Dp = Dp(0.3f * 160.0f)

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge()
        setContent {
            ScorpionPreview(viewModel.game)
        }
    }
}

@PreviewScreenSizes
@Composable
fun ScorpionPreview(game: Game? = null) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ScorpionTheme {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xff277714))
        ) {
            if (landscape) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width(BAR_HEIGHT)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolContent(true)
                }
                game?.Content(Modifier.align(Alignment.TopStart)
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
                    ToolContent(false)
                }
                game?.Content(Modifier.align(Alignment.TopStart)
                    .height(maxHeight - BAR_HEIGHT)
                    .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ToolContent(landscape: Boolean) {
}

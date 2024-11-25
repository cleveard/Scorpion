package com.github.cleveard.scorpion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import com.github.cleveard.scorpion.ui.widgets.ScorpionGame

interface Game {
    fun deal()

    @Composable
    fun Content(modifier: Modifier)
}


class MainActivityViewModel: ViewModel() {
    val game: Game = ScorpionGame().apply {
        deal()
    }
}

package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.CardEntity
import com.github.cleveard.scorpion.db.StateEntity
import kotlinx.coroutines.CoroutineScope

interface Dealer {
    val scope: CoroutineScope
    val game: Game
    val cards: List<List<Card?>>
    var showAlert: (@Composable () -> Unit)?
    suspend fun deal()
    suspend fun gameVariants()
    fun findCard(cardValue: Int): Card
    suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T
    fun cardChanged(card: CardEntity)
    fun onStateChanged(state: StateEntity)
    fun canUndo(): Boolean
    fun canRedo(): Boolean
    suspend fun undo(): List<CardEntity>?
    suspend fun redo(): List<CardEntity>?
    fun showNewGameOrDismissAlert(text: Int)
}


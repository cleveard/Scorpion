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
    val cardBackAssetPath: String
    val useSystemTheme: Boolean
    val cardWidth: Int
    val cardHeight: Int
    var showAlert: (@Composable () -> Unit)?
    fun cardFrontAssetPath(value: Int): String
    suspend fun deal()
    suspend fun gameVariants()
    suspend fun settings()
    fun findCard(cardValue: Int): Card
    suspend fun <T> withUndo(action: suspend (generation: Long) -> T): T
    fun cardChanged(card: CardEntity)
    fun onStateChanged()
    fun canUndo(): Boolean
    fun canRedo(): Boolean
    suspend fun undo(): List<CardEntity>?
    suspend fun redo(): List<CardEntity>?
    suspend fun showDialog(title: Int, vararg buttons: Int, content: @Composable () -> Unit): Int
    suspend fun showNewGameOrDismissAlert(text: Int, title: Int = 0)
}


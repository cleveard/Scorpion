package com.github.cleveard.scorpion.ui

import com.github.cleveard.scorpion.db.Card
import com.github.cleveard.scorpion.db.State
import kotlinx.coroutines.CoroutineScope

interface Dealer {
    val scope: CoroutineScope
    val game: Game
    val deck: List<Card>
    suspend fun deal()
    suspend fun gameVariants()
    fun findCard(cardValue: Int): Card
    suspend fun withUndo(name: String, action: suspend () -> Unit)
    fun cardChanged(card: Card)
    fun stateChanged(stateBlob: ByteArray)
    suspend fun undo(): Pair<State, List<Card>>?
    suspend fun redo(): Pair<State, List<Card>>?
}


package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface DialogContent {
    @Composable
    fun Content(modifier: Modifier)

    suspend fun onDismiss()

    suspend fun onAccept()

    fun reset()
}

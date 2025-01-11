package com.github.cleveard.scorpion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Interface to handle content for the settings and variant dialogs
 */
interface DialogContent {
    /**
     * The content in the dialog
     * @param modifier The modifier for the Content
     */
    @Composable
    fun Content(modifier: Modifier)

    /**
     * Called if the dialog is dismissed
     */
    suspend fun onDismiss()

    /**
     * Called if the dialog is accepted
     */
    suspend fun onAccept()

    /**
     * Reset the dialog content to the game's current values
     */
    fun reset()
}

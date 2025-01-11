package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Simple checkbox with text
 * @param value True to check the checkbox
 * @param textId The resource id of the text
 * @param modifier The composable modifier
 * @param onChange Callback when the checkbox is clicked. The only argument is the current check state.
 */
@Composable
fun TextSwitch(value: Boolean, textId: Int, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    // Put the checkbox and text in a row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
    ){
        // Checkbox
        Checkbox(
            value,
            onCheckedChange = onChange
        )
        // Text
        Text(
            stringResource(textId),
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontStyle = MaterialTheme.typography.bodySmall.fontStyle
        )
    }
}
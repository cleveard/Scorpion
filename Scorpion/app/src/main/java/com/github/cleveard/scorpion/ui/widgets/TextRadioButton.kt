package com.github.cleveard.scorpion.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Simple radio button with text
 * @param selected True if this radio button should display as selected
 * @param textId The resource id for the text
 * @param modifier Modifier for composable
 * @param onChange Call back when a radio button is clicked
 */
@Composable
fun TextRadioButton(selected: Boolean, textId: Int, modifier: Modifier = Modifier, onChange: () -> Unit) {
    // Put the radio button and text in a row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
    ){
        // Radio button
        RadioButton(
            selected,
            onClick = { onChange() }
        )
        // Text
        Text(
            stringResource(textId),
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            fontStyle = MaterialTheme.typography.bodySmall.fontStyle,
            modifier = Modifier.clickable { onChange() }
        )
    }
}

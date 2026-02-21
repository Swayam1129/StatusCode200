package com.example.accessu.mode

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModeToggleRow(
    currentMode: String,
    onModeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ModeButton(
            label = "Walking",
            selected = currentMode == "Walking",
            onClick = { onModeSelected("Walking") }
        )

        ModeButton(
            label = "On Bus",
            selected = currentMode == "On Bus",
            onClick = { onModeSelected("On Bus") }
        )
    }
}
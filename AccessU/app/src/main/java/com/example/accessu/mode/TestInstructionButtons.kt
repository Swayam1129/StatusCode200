package com.example.accessu.mode

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TestInstructionButtons(onTestClick: (String) -> Unit) {
    Column {
        Button(onClick = { onTestClick("Turn left in 10 meters") }) {
            Text("Test: Turn left")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onTestClick("You have arrived at Library") }) {
            Text("Test: Arrived")
        }
    }
}
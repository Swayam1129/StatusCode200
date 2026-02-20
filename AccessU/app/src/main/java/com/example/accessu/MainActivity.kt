package com.example.accessu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.accessu.ui.theme.AccessUTheme
import com.example.accessu.core.AudioGuide

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS (AudioGuide handles queue internally)
        AudioGuide.init(this)

        enableEdgeToEdge()

        setContent {
            AccessUTheme {

                // 1️⃣ Compose state for current instruction
                var currentInstruction by remember { mutableStateOf("Welcome. Walking mode ready.") }

                // 2️⃣ Speak automatically whenever the instruction updates
                LaunchedEffect(currentInstruction) {
                    AudioGuide.speak(currentInstruction)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 3️⃣ Display current instruction
                    Greeting(
                        text = currentInstruction,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                // 4️⃣ Example: simulate navigation instructions changing after app starts
                LaunchedEffect(Unit) {
                    // Test: simulate a series of navigation instructions
                    kotlinx.coroutines.delay(3000)
                    currentInstruction = "Walk forward for 50 meters"
                    kotlinx.coroutines.delay(5000)
                    currentInstruction = "Turn left in 10 meters"
                    kotlinx.coroutines.delay(5000)
                    currentInstruction = "You have arrived at your destination"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioGuide.shutdown() // safely stop TTS
    }
}

@Composable
fun Greeting(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AccessUTheme {
        Greeting("Hello Android")
    }
}
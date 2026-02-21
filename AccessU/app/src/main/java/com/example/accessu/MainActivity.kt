package com.example.accessu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.accessu.core.AudioGuide
import com.example.accessu.ui.theme.AccessUTheme
import com.example.accessu.mode.NavigationApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS once
        AudioGuide.init(this)

        setContent {
            AccessUTheme {
                // Call top-level Navigation UI from mode package
                NavigationApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shutdown TTS safely
        AudioGuide.shutdown()
    }
}
package com.example.accessu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.accessu.transit.TransitRepository
import com.example.accessu.ui.theme.AccessUTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val transitRepo = TransitRepository(this)
        val loaded = transitRepo.loadGtfsFromAssets()
        val status = if (loaded) {
            "GTFS loaded: ${transitRepo.getStops().size} stops, ${transitRepo.getRoutes().size} routes"
        } else {
            transitRepo.getLastLoadError()?.let { "GTFS load failed: $it" } ?: "GTFS load failed"
        }

        setContent {
            AccessUTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AccessU",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    AccessUTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "AccessU",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "GTFS loaded: 9 stops, 4 routes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
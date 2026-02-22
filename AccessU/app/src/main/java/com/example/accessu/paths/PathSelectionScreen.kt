package com.example.accessu.paths

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PathSelectionScreen(
    paths: List<PathMetadata>,
    onPathSelected: (PathMetadata) -> Unit,
    onViewPathOnMap: ((pathId: String) -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Indoor Navigation",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Select a path to navigate.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(paths) { path ->
                PathCard(
                    path = path,
                    onClick = { onPathSelected(path) },
                    onViewOnMap = onViewPathOnMap?.let { { it(path.id) } }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun PathCard(
    path: PathMetadata,
    onClick: () -> Unit,
    onViewOnMap: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = path.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${path.origin} → ${path.destination}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = path.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Start Indoor Navigation")
            }
            onViewOnMap?.let { viewMap ->
                TextButton(
                    onClick = viewMap,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("View on UAlberta Map")
                }
            }
        }
    }
}

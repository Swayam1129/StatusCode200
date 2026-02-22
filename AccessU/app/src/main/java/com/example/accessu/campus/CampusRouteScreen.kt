package com.example.accessu.campus

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CampusRouteScreen(
    repository: CampusGraphRepository,
    onStartIndoorNav: (pathId: String) -> Unit,
    onStartOutdoorNav: (originId: String, destId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (resolved, unresolved) = remember { repository.getNodesForPicker() }
    val allForPicker = resolved + unresolved
    var selectedOrigin by remember { mutableStateOf<ResolvedNode?>(null) }
    var selectedDest by remember { mutableStateOf<ResolvedNode?>(null) }
    var route by remember { mutableStateOf<List<ResolvedNode>?>(null) }
    var indoorPathId by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Campus Routes", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Outdoor building-to-building navigation.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("From", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier.height(120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(allForPicker) { r ->
                val selected = selectedOrigin?.node?.id == r.node.id
                val disabled = !r.isResolved
                Text(
                    text = if (disabled) "${r.node.name} (needs coordinates)" else r.node.name,
                    modifier = Modifier
                        .clickable(enabled = r.isResolved) {
                            if (r.isResolved) selectedOrigin = r
                            else snackbarMessage = "Coordinates not available."
                        }
                        .padding(4.dp),
                    style = if (selected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text("To", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(
            modifier = Modifier.height(120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(allForPicker) { r ->
                val selected = selectedDest?.node?.id == r.node.id
                val disabled = !r.isResolved
                Text(
                    text = if (disabled) "${r.node.name} (needs coordinates)" else r.node.name,
                    modifier = Modifier
                        .clickable(enabled = r.isResolved) {
                            if (r.isResolved) selectedDest = r
                            else snackbarMessage = "Coordinates not available."
                        }
                        .padding(4.dp),
                    style = if (selected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Button(
            onClick = {
                val o = selectedOrigin
                val d = selectedDest
                if (o == null || d == null) return@Button
                if (!o.isResolved || !d.isResolved) {
                    snackbarMessage = "Coordinates not available."
                    return@Button
                }
                snackbarMessage = null
                val path = repository.findRoute(o.node.id, d.node.id)
                route = path
                indoorPathId = repository.getIndoorPathId(o.node.id, d.node.id)
                if (path.isEmpty()) snackbarMessage = "Route not found."
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Get Route")
        }

        snackbarMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }

        route?.let { path ->
            if (path.isEmpty()) return@let
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Route (${path.size} stops)", style = MaterialTheme.typography.titleMedium)
                    path.forEachIndexed { i, r ->
                        Text("${i + 1}. ${r.node.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    Button(
                        onClick = { onStartOutdoorNav(path.first().node.id, path.last().node.id) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("Start Outdoor Navigation (GPS)")
                    }
                    indoorPathId?.let { pathId ->
                        Button(onClick = { onStartIndoorNav(pathId) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Start Indoor QR Navigation")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

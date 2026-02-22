package com.example.accessu.indoor

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun IndoorGenericScreen(
    repository: IndoorGenericGraphRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val graph = remember { repository.loadGraph() }
    var startId by remember { mutableStateOf<String?>(null) }
    var destId by remember { mutableStateOf<String?>(null) }
    var routeSteps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var router by remember { mutableStateOf<IndoorGenericRouter?>(null) }
    var lastSpokenStepIndex by remember { mutableIntStateOf(-1) }
    var rerouteFromId by remember { mutableStateOf<String?>(null) }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        tts.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale.US
        }
    }

    if (graph == null) {
        Text("Could not load indoor graph.", modifier = modifier.padding(16.dp))
        return
    }

    val nodeIds = graph.nodes.map { it.id }
    val nodeNames = graph.nodes.associate { it.id to it.name }

    fun speakStep(stepIndex: Int) {
        if (stepIndex < 0 || stepIndex >= routeSteps.size) return
        val (nodeId, say) = routeSteps[stepIndex]
        val r = router ?: return
        val total = routeSteps.size
        val stepNum = stepIndex + 1
        val nextName = r.getNodeName(nodeId)
        val msg = if (say.isEmpty()) {
            "Step $stepNum of $total. Start here. Next: $nextName."
        } else {
            "Step $stepNum of $total. $say Next: $nextName."
        }
        tts.value?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "indoor_generic")
        lastSpokenStepIndex = stepIndex
    }

    fun computeRoute(from: String, to: String) {
        val r = IndoorGenericRouter(graph)
        router = r
        val path = r.findPath(from, to)
        routeSteps = r.getStepsWithSay(path)
        lastSpokenStepIndex = -1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Generic Indoor Navigation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Prototype: no camera or QR. Select start and destination.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        IndoorDropdown(
            label = "Start",
            options = nodeIds,
            displayName = { nodeNames[it] ?: it },
            selectedId = startId,
            onSelect = { startId = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IndoorDropdown(
            label = "Destination",
            options = nodeIds,
            displayName = { nodeNames[it] ?: it },
            selectedId = destId,
            onSelect = { destId = it }
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val s = startId
                val d = destId
                if (s != null && d != null && s != d) computeRoute(s, d)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Compute Route")
        }

        if (routeSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Route (${routeSteps.size} steps)", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                routeSteps.forEachIndexed { i, (nid, say) ->
                    val name = nodeNames[nid] ?: nid
                    val stepText = if (say.isEmpty()) "Start: $name" else "${i + 1}. $say → $name"
                    Text(
                        stepText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val next = lastSpokenStepIndex + 1
                    if (next < routeSteps.size) speakStep(next)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speak Next Step")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Reroute from here:", style = MaterialTheme.typography.labelMedium)
            IndoorDropdown(
                label = "Current node",
                options = routeSteps.map { it.first },
                displayName = { nodeNames[it] ?: it },
                selectedId = rerouteFromId,
                onSelect = { rerouteFromId = it }
            )
            Button(
                onClick = {
                    val from = rerouteFromId ?: routeSteps.firstOrNull()?.first
                    val d = destId
                    if (from != null && d != null && from != d) {
                        computeRoute(from, d)
                        rerouteFromId = null
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Reroute from Here")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

@Composable
private fun IndoorDropdown(
    label: String,
    options: List<String>,
    displayName: (String) -> String,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
    Column(modifier = modifier) {
        Button(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedId?.let { displayName(it) } ?: "Select $label")
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { id ->
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(displayName(id))
                    }
                }
            }
        }
    }
}

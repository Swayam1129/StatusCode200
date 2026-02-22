package com.example.accessu.indoor

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.accessu.ui.theme.AccessUTheme
import java.util.Locale

/**
 * Generic indoor navigation: no camera, no GPS, no map.
 * Uses indoor graph (BFS), step-based TTS only.
 * Repeat instruction + Reroute from here.
 */
class IndoorGenericNavigationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORIGIN_ID = "origin_id"
        const val EXTRA_DEST_ID = "dest_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val originId = intent.getStringExtra(EXTRA_ORIGIN_ID) ?: run { finish(); return }
        val destId = intent.getStringExtra(EXTRA_DEST_ID) ?: run { finish(); return }
        val repo = IndoorGenericGraphRepository(this)
        setContent {
            AccessUTheme {
                IndoorGenericNavigationContent(
                    repository = repo,
                    originId = originId,
                    destId = destId,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
private fun IndoorGenericNavigationContent(
    repository: IndoorGenericGraphRepository,
    originId: String,
    destId: String,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val graph = remember { repository.loadGraph() }
    var routeSteps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var router by remember { mutableStateOf<IndoorGenericRouter?>(null) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var rerouteFromId by remember { mutableStateOf<String?>(null) }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    LaunchedEffect(Unit) {
        tts.value = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale.US
        }
    }

    fun computeRoute(from: String, to: String) {
        if (graph == null) return
        val r = IndoorGenericRouter(graph)
        router = r
        val path = r.findPath(from, to)
        routeSteps = r.getStepsWithSay(path)
        currentStepIndex = 0
        rerouteFromId = null
    }

    LaunchedEffect(originId, destId) {
        if (graph != null && originId != destId) computeRoute(originId, destId)
    }

    fun speakCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= routeSteps.size) return
        val (nodeId, say) = routeSteps[currentStepIndex]
        val r = router ?: return
        val total = routeSteps.size
        val stepNum = currentStepIndex + 1
        val nodeName = r.getNodeName(nodeId)
        val msg = if (say.isEmpty()) {
            "Step $stepNum of $total. Start here. Next: $nodeName."
        } else {
            "Step $stepNum of $total. $say"
        }
        tts.value?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "indoor_generic")
    }

    LaunchedEffect(routeSteps) {
        if (routeSteps.isNotEmpty()) speakCurrentStep()
    }

    if (graph == null) {
        Text("Could not load indoor graph.", modifier = Modifier.padding(16.dp))
        return
    }

    val nodeNames = graph.nodes.associate { it.id to it.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Indoor navigation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No camera or GPS. Step-based directions only.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (routeSteps.isEmpty()) {
            Text("No route found between ${nodeNames[originId] ?: originId} and ${nodeNames[destId] ?: destId}.")
        } else {
            val (nodeId, say) = routeSteps[currentStepIndex]
            val name = nodeNames[nodeId] ?: nodeId
            val stepText = if (say.isEmpty()) "Start: $name" else say
            Text(
                "Step ${currentStepIndex + 1} of ${routeSteps.size}",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                stepText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (currentStepIndex == routeSteps.size - 1) {
                Text("You have arrived at $name.", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { speakCurrentStep() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Repeat instruction")
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (currentStepIndex < routeSteps.size - 1) {
                Button(
                    onClick = {
                        currentStepIndex++
                        speakCurrentStep()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Next step")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text("Reroute from here:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            val pathNodeIds = routeSteps.map { it.first }
            pathNodeIds.forEach { nid ->
                Button(
                    onClick = {
                        rerouteFromId = nid
                        computeRoute(nid, destId)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text("From ${nodeNames[nid] ?: nid}")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("End navigation")
        }
    }
}

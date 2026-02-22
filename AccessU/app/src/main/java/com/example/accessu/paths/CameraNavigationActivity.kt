package com.example.accessu.paths

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.accessu.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import java.util.Locale
import java.util.ArrayDeque

class CameraNavigationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH_ID = "path_id"
        const val EXTRA_PATH_NAME = "path_name"
        private const val TAG = "CameraNav"
        private const val SAME_SCAN_COOLDOWN_MS = 2000L
    }

    private var pathGraph: PathGraph? = null
    private var currentNodeId: String? = null
    private var destinationNodeId: String? = null
    private var lastComputedPath: List<String> = emptyList()
    private var expectedNextNodeId: String? = null
    private var lastSpokenInstruction: String? = null
    private var spokenConfirmation: String? = null
    private var spokenAction: String? = null
    private var spokenNextCheckpoint: String? = null
    private var lastFullSpokenMessage: String? = null
    private var lastPayload: String? = null
    private var lastPayloadAt = 0L

    private var adjacency: Map<String, List<String>> = emptyMap()
    private var barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
    private var tts: TextToSpeech? = null

    private val pathRepo by lazy { PathRepository(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_navigation)

        val pathId = (intent.getStringExtra(EXTRA_PATH_ID) ?: "").trim()
        pathGraph = pathRepo.loadPathGraph(pathId)
        if (pathGraph == null) {
            Toast.makeText(this, "Path graph not found: $pathId", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        destinationNodeId = pathGraph!!.nodes.lastOrNull { it.nextNodeId == null }?.id
        adjacency = buildAdjacencyList(pathGraph!!)

        initBarcodeScanner()
        initTts()
        setupSimulateScanUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildAdjacencyList(graph: PathGraph): Map<String, List<String>> {
        val adj = mutableMapOf<String, MutableList<String>>()
        for (node in graph.nodes) {
            adj.getOrPut(node.id) { mutableListOf() }
            node.nextNodeId?.let { nextId ->
                adj.getOrPut(node.id) { mutableListOf() }.add(nextId)
                adj.getOrPut(nextId) { mutableListOf() }.add(node.id)
            }
        }
        return adj
    }

    private fun findPathBfs(startId: String, destId: String): List<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        val parent = mutableMapOf<String, String>()
        queue.addLast(startId)
        visited.add(startId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == destId) {
                val path = mutableListOf<String>()
                var at = destId
                while (at.isNotEmpty()) {
                    path.add(0, at)
                    at = parent[at] ?: ""
                }
                return path
            }
            for (next in adjacency[curr] ?: emptyList()) {
                if (next !in visited) {
                    visited.add(next)
                    parent[next] = curr
                    queue.addLast(next)
                }
            }
        }
        return emptyList()
    }

    private fun initBarcodeScanner() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                speak("Scan a QR code to start. Point camera at the first QR on your path.")
            }
        }
    }

    private fun speak(msg: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        lastSpokenInstruction = msg
        updateDebugState()
        tts?.speak(msg, queueMode, null, "nav")
    }

    /** Extract action-focused instruction: first 1–2 actionable sentences. Prefer Turn/Continue/Walk/Stop. */
    private fun toActionInstruction(instruction: String): String {
        val sentences = instruction.split(Regex("\\.\\s+")).filter { it.isNotBlank() }
        val actionStarts = listOf("turn", "continue", "walk", "stop", "go", "head", "pass")
        val actionSentences = sentences.filter { s ->
            val lower = s.lowercase()
            actionStarts.any { lower.startsWith(it) || lower.contains(" $it ") }
        }
        val result = if (actionSentences.isNotEmpty()) {
            actionSentences.take(2).joinToString(". ").trim()
        } else {
            sentences.take(2).joinToString(". ").trim()
        }
        return if (result.endsWith(".")) result else "$result."
    }

    /** Detects hazard type for a node. Returns one sentence or null. Case-insensitive. */
    private fun detectHazard(node: Node?): String? {
        if (node == null) return null
        val text = node.instruction.lowercase()
        return when {
            text.contains("door") -> "Door ahead at the next checkpoint. Prepare to open it."
            text.contains("stairs") || text.contains("steps") -> "Stairs ahead at the next checkpoint. Prepare to go down carefully and use the handrail."
            else -> null
        }
    }

    /** Hazard phrasing for CURRENT node (door/stairs). Case-insensitive. One sentence per hazard. */
    private fun addHazardPhrasing(instruction: String): String {
        val lower = instruction.lowercase()
        val hazards = mutableListOf<String>()
        if (lower.contains("door")) hazards.add("Door ahead. Reach forward for the handle.")
        if (lower.contains("stairs") || lower.contains("steps")) hazards.add("Stairs going down ahead. Use the handrail.")
        return if (hazards.isEmpty()) "" else hazards.joinToString(" ")
    }

    /** Build message: A Confirmation, B Current hazard, C Action, D Next hazard pre-warning (if any, no repeat), E Next checkpoint. */
    private fun buildGuidanceMessage(
        currentNode: Node,
        nextNode: Node?,
        isOffRoute: Boolean
    ): String {
        spokenConfirmation = "Checkpoint confirmed: ${currentNode.name}."
        val currentHazard = addHazardPhrasing(currentNode.instruction)
        val actionRaw = toActionInstruction(currentNode.instruction)
        spokenAction = buildString {
            if (currentHazard.isNotEmpty()) append("$currentHazard ")
            append(actionRaw)
        }

        val currentLower = currentNode.instruction.lowercase()
        var nextPreWarning = detectHazard(nextNode)
        if (nextPreWarning != null) {
            val nextLower = nextNode!!.instruction.lowercase()
            if (nextLower.contains("door") && currentLower.contains("door")) nextPreWarning = null
            if ((nextLower.contains("stairs") || nextLower.contains("steps")) &&
                (currentLower.contains("stairs") || currentLower.contains("steps"))) nextPreWarning = null
        }

        spokenNextCheckpoint = if (nextNode == null) {
            "You have reached your destination."
        } else {
            "Next checkpoint: ${nextNode.name}. Scan the next QR when you reach it."
        }

        return buildString {
            if (isOffRoute) append("Off route. Rerouting. Stay at this checkpoint. I will guide you from here. ")
            append(spokenConfirmation!!)
            append(" ")
            append(spokenAction!!)
            if (nextPreWarning != null) append(" $nextPreWarning")
            append(" ")
            append(spokenNextCheckpoint!!)
        }
    }

    private fun updateDebugState() {
        val view = findViewById<TextView>(R.id.debug_state)
        val graph = pathGraph
        if (view != null && graph != null) {
            val destId = destinationNodeId ?: "—"
            val pathStr = if (lastComputedPath.isEmpty()) "—" else lastComputedPath.joinToString("->")
            val nextId = lastComputedPath.getOrNull(1) ?: "—"
            view.text = buildString {
                append("currentNodeId: ${currentNodeId ?: "—"}\n")
                append("destinationNodeId: $destId\n")
                append("computedPath: $pathStr\n")
                append("nextNodeId: $nextId\n")
                append("expectedNextNodeId: ${expectedNextNodeId ?: "—"}\n")
                append("spokenConfirmation: ${spokenConfirmation?.take(40) ?: "—"}\n")
                append("spokenAction: ${spokenAction?.take(40) ?: "—"}\n")
                append("spokenNextCheckpoint: ${spokenNextCheckpoint?.take(40) ?: "—"}")
            }
        }
    }

    private fun setupSimulateScanUi() {
        val graph = pathGraph ?: return
        val nodeIds = graph.nodes.map { it.id }
        val spinner = findViewById<Spinner>(R.id.node_spinner)
        if (spinner != null && nodeIds.isNotEmpty()) {
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nodeIds).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        findViewById<android.widget.Button>(R.id.btn_simulate_scan)?.setOnClickListener {
            val selectedId = (spinner?.selectedItem as? String) ?: nodeIds.firstOrNull() ?: return@setOnClickListener
            handleQrScanned("${graph.pathId}:$selectedId")
        }
        findViewById<android.widget.Button>(R.id.btn_simulate_wrong_path)?.setOnClickListener {
            handleQrScanned("some_other_path:n3")
        }
        findViewById<android.widget.Button>(R.id.btn_repeat_instruction)?.setOnClickListener {
            val msg = lastFullSpokenMessage
            if (!msg.isNullOrBlank()) tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "nav")
        }
        updateDebugState()
    }

    private fun startCamera() {
        val container = findViewById<FrameLayout>(R.id.camera_container)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewView = container.findViewById<androidx.camera.view.PreviewView>(R.id.preview_view)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy -> analyzeImage(imageProxy) }
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        barcodeScanner?.process(image)
            ?.addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    barcodes.firstOrNull()?.rawValue?.let { handleQrScanned(it) }
                }
                imageProxy.close()
            }
            ?.addOnFailureListener { imageProxy.close() }
    }

    private fun handleQrScanned(rawValue: String) {
        val now = System.currentTimeMillis()
        if (rawValue == lastPayload && (now - lastPayloadAt) < SAME_SCAN_COOLDOWN_MS) return
        lastPayload = rawValue
        lastPayloadAt = now

        val (scannedPathId, scannedNodeId) = when {
            rawValue.contains(":") -> {
                val parts = rawValue.split(":", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else return
            }
            rawValue.contains("_") -> {
                val lastUnderscore = rawValue.lastIndexOf('_')
                if (lastUnderscore > 0) rawValue.take(lastUnderscore) to rawValue.drop(lastUnderscore + 1)
                else return
            }
            else -> return
        }

        val graph = pathGraph ?: return
        if (scannedPathId != graph.pathId) {
            speak("Wrong path. You scanned a QR from a different route. Find the QR for ${graph.pathId.replace("_", " to ")}.")
            return
        }

        val node = graph.nodes.find { it.id == scannedNodeId } ?: run {
            speak("Unknown node. Make sure you're on the correct path.")
            return
        }

        val previousCurrentNodeId = currentNodeId
        currentNodeId = scannedNodeId

        val destId = destinationNodeId ?: return

        if (scannedNodeId == destId) {
            spokenConfirmation = "Checkpoint confirmed: ${node.name}."
            spokenAction = toActionInstruction(node.instruction)
            spokenNextCheckpoint = "You have reached your destination."
            val hazard = addHazardPhrasing(node.instruction)
            val msg = buildString {
                append(spokenConfirmation!!)
                append(" ")
                if (hazard.isNotEmpty()) append("$hazard ")
                append(spokenAction!!)
                append(" ")
                append(spokenNextCheckpoint!!)
            }
            lastFullSpokenMessage = msg
            speak(msg)
            lastComputedPath = emptyList()
            expectedNextNodeId = null
        } else {
            val prevExpectedNext = lastComputedPath.getOrNull(1)
            val isOffRoute = previousCurrentNodeId != null &&
                prevExpectedNext != null &&
                scannedNodeId != prevExpectedNext

            val path = findPathBfs(scannedNodeId, destId)
            if (path.isEmpty()) {
                speak("Route error. Cannot find a path to the destination.")
                lastComputedPath = emptyList()
                expectedNextNodeId = null
                spokenConfirmation = null
                spokenAction = null
                spokenNextCheckpoint = null
            } else {
                lastComputedPath = path
                expectedNextNodeId = path.getOrNull(1)
                val nextNode = graph.nodes.find { it.id == path[1] }
                val msg = buildGuidanceMessage(node, nextNode, isOffRoute)
                lastFullSpokenMessage = msg
                speak(msg)
            }
        }
        updateDebugState()
    }

    override fun onDestroy() {
        barcodeScanner?.close()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

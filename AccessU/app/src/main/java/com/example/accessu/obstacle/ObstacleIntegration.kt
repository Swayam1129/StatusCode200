package com.example.accessu.obstacle

/**
 * Integration points for Niharika's app shell.
 *
 * When Niharika adds Walking and On bus modes:
 * - Walking + nav active: use ObstacleCameraScreen(isActive = true)
 * - On bus: use ObstacleCameraScreen(isActive = false) or don't render it
 *
 * The camera runs ONLY when isActive = true.
 * When switching to On bus, set isActive = false to stop camera and obstacle messages.
 */
object ObstacleIntegration {

    /**
     * Current mode from Niharika's perspective.
     * Set to false when user says "I'm on the bus" or taps On bus.
     */
    var isWalkingModeActive: Boolean = true
        set(value) {
            field = value
            onModeChanged?.invoke(value)
        }

    /**
     * Optional: Niharika can register to know when to show/hide obstacle UI.
     */
    var onModeChanged: ((isWalking: Boolean) -> Unit)? = null
}

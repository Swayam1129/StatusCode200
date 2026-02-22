package com.example.accessu.campus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.accessu.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

class OutdoorNavigationActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_ORIGIN_ID = "origin_id"
        const val EXTRA_DEST_ID = "dest_id"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var route: List<ResolvedNode> = emptyList()
    private var tts: TextToSpeech? = null
    private var locationCallback: LocationCallback? = null
    private val repo by lazy { CampusGraphRepository(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startLocationUpdates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_outdoor_navigation)
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        initTts()

        val originId = intent.getStringExtra(EXTRA_ORIGIN_ID) ?: run { finish(); return }
        val destId = intent.getStringExtra(EXTRA_DEST_ID) ?: run { finish(); return }
        route = repo.findRoute(originId, destId)
        if (route.isEmpty()) {
            val unresolved = repo.getUnresolvedNames(originId, destId)
            val msg = if (unresolved.isNotEmpty()) {
                "Coordinates missing for ${unresolved.joinToString()}."
            } else {
                "Route not found."
            }
            speak(msg)
            finish()
            return
        }

        speak("Outdoor map navigation. Walking from ${route.first().node.name} to ${route.last().node.name}.")

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        findViewById<android.widget.Button>(R.id.btn_stop).setOnClickListener { finish() }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
    }

    private fun speak(msg: String) {
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "outdoor")
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
        }
        val points = route.map { LatLng(it.safeLat, it.safeLon) }
        map.addPolyline(PolylineOptions().addAll(points).color(0xFF0066CC.toInt()).width(12f))
        route.forEachIndexed { i, r ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(r.safeLat, r.safeLon))
                    .title(if (i == 0) "Start: ${r.node.name}" else if (i == route.size - 1) "End: ${r.node.name}" else r.node.name)
            )
        }
        val builder = LatLngBounds.builder()
        points.forEach { builder.include(it) }
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2500)
            .setMinUpdateDistanceMeters(2f)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    googleMap?.let { m ->
                        m.animateCamera(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)))
                    }
                    val status = findViewById<TextView>(R.id.status_text)
                    status?.text = "Next: ${route.getOrNull(1)?.node?.name ?: "Arrived"}"
                }
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationCallback?.let { fusedClient.requestLocationUpdates(request, it, Looper.getMainLooper()) }
        }
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

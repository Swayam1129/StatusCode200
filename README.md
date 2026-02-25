## AccessU

AccessU is an Android app that helps blind and low‑vision students navigate university campuses independently. It combines voice interaction, indoor QR routes, outdoor maps, and obstacle detection into a single, hands‑free experience.

## Problem

Traditional campus maps and signage assume you can see. Even with mobility training, visually impaired students often need sighted assistance for:

- Finding the right building entrance
- Navigating complex indoor corridors, doors, and stairs
- Moving safely around crowds and obstacles

AccessU aims to reduce that dependence and increase confidence and independence on campus.

## What AccessU Does

- **Voice‑driven navigation**
  - You speak your destination (e.g. “ETLC to NREF” or “Cameron Library”).
  - The app uses speech recognition to understand you and text‑to‑speech to guide you.

- **Indoor navigation with QR codes**
  - Buildings are modeled as **graphs**; each QR is a node (checkpoint).
  - You scan QRs along the route; AccessU:
    - Locates you in the graph
    - Runs a **shortest‑path (BFS)** to the destination
    - Speaks clear, step‑by‑step directions
  - If you scan a wrong‑path QR, it **reroutes** automatically.

- **Outdoor navigation**
  - Uses **Google Maps** and **GPS** to get you to the right building or bus stop.
  - Turn‑by‑turn style spoken cues (no need to look at the screen).

- **Obstacle awareness**
  - Camera feed is analyzed with **ML Kit Object Detection**.
  - Detects obstacles in a “path zone” in front of the user.
  - Speaks lightweight cues like “Obstacle ahead, move slightly left/right” or “Path is clear.”

- **Fully voice‑first experience**
  - No visual UI required during navigation.
  - Designed for screen‑free use: simple phrases, minimal cognitive load.

## How It Works (Under the Hood)

### Indoor routing

- **Path graphs in JSON**
  - Each indoor route (e.g. `ccis_to_cameron`, `etlc_to_nref`) is a JSON file:
    - Nodes: `id`, `name`, `instruction`, `nextNodeId`
    - `nextNodeId == null` marks the destination.
  - Graphs are stored in `app/src/main/assets/paths/`.

- **Shortest path & rerouting**
  - When a QR is scanned, payload is `pathId:nodeId`.
  - The app loads the corresponding graph via `PathRepository`.
  - A **BFS** over the adjacency list finds the path from current node to destination.
  - If you jump onto a wrong node, BFS recomputes the path from there (reroute).

- **Spoken guidance**
  - For regular checkpoints:  
    - “On right path. {action instruction}”
  - For first and last checkpoints:
    - Just the full instruction (more detailed), no “On right path.”
  - Instructions are authored in the graph JSON, so they can be updated without code changes.

### Outdoor routing

- **Campus graph**
  - Campus building graph is stored in `campus/buildings.json`.
  - Indoor path references (`indoorPaths`) link origin/destination building IDs to indoor path IDs.

- **Google Maps + Location**
  - Uses **Google Maps SDK** and **Play Services Location** to:
    - Get user location
    - Render map and geometry
    - Build outdoor routes between campus nodes

### Obstacle detection

- **Camera pipeline**
  - A Compose screen (`ObstacleCameraScreen`) binds a CameraX pipeline.
  - Frames are passed to `ObstacleDetector`.

- **ML Kit Object Detection**
  - Uses `com.google.mlkit:object-detection` with:
    - `ObjectDetectorOptions.SINGLE_IMAGE_MODE`
    - `enableMultipleObjects()`
  - For each frame:
    - Filter detections by size and position inside a “center path” region.
    - Pick the closest relevant object.
    - Speak:
      - “Obstacle ahead, move slightly left.” or  
      - “Obstacle ahead, move slightly right.” or  
      - “Path is clear.”
  - Includes a global cooldown (~3.5 seconds) to avoid TTS spam.

- **Future depth**
  - TensorFlow Lite dependency is included for a future depth or semantic segmentation model.

## Tech Stack

- **Platform & language**
  - Android (minSdk 26, targetSdk 36)
  - Kotlin, Gradle (Kotlin DSL)

- **UI**
  - Jetpack Compose (Material 3)
  - Custom U of A–themed color system (UofAGreen, UofAGold, etc.)

- **Camera & vision**
  - CameraX (camera2, lifecycle, view)
  - Google ML Kit:
    - Object Detection (obstacle detection)
    - Barcode Scanning (QR codes for indoor navigation)
  - TensorFlow Lite (reserved for future depth/object models)

- **Navigation**
  - Google Maps SDK for Android
  - Google Play Services Location (GPS)
  - Custom BFS routing for:
    - Indoor path graphs (QR checkpoints)
    - Campus building graph

- **Voice & accessibility**
  - Android SpeechRecognizer (voice input)
  - Android TextToSpeech (spoken guidance)
  - Custom phrasing and timing (e.g. door pause: “Automatic door ahead.” then “You may continue.”)

- **Data & infra**
  - Gson for JSON parsing
  - Path and building data in local assets (offline‑friendly)
  - OkHttp (for future metadata / network features)

- **Android patterns**
  - Activity Result API (permissions, camera)
  - Compose navigation screens for flows (voice, indoor, outdoor, obstacle)

## Project Structure (high level)

- `app/src/main/java/com/example/accessu/navigation/`  
  High‑level nav flow, voice interaction, and screen routing.

- `app/src/main/java/com/example/accessu/paths/`  
  Indoor path graphs, camera QR navigation, BFS routing.

- `app/src/main/java/com/example/accessu/campus/`  
  Campus building graph, outdoor navigation, maps.

- `app/src/main/java/com/example/accessu/obstacle/`  
  Obstacle camera pipeline, ML Kit object detection, spoken obstacle cues.

- `app/src/main/assets/paths/`  
  Indoor route graph JSONs and metadata.

- `app/src/main/assets/campus/`  
  Campus buildings graph and indoor path references.

- `app/src/main/assets/qr_test/`  
  QR test pages to generate printable codes for indoor paths.

## Roadmap / Future Work

- **Richer obstacle understanding**
  - Integrate **depth** or **semantic segmentation** (via TensorFlow Lite) for:
    - Distinguishing people vs static obstacles vs walls
    - Better distance estimation and urgency

- **Dynamic route generation**
  - Generate indoor routes from building floor plans rather than fixed graphs.
  - Auto‑detect closures or blocked corridors and reroute.

- **User customization**
  - Adjustable verbosity of instructions
  - Different walking speeds and safety margins
  - Multi‑language support

- **Crowdsourced corrections**
  - Allow users and staff to suggest updates for QR placement and instructions.
  - Sync with a backend so buildings can be added and improved over time.

- **More campuses and buildings**
  - Expand beyond the initial routes (CCIS ↔ ETLC, ETLC ↔ NREF, bus stops, etc.).
  - Create a toolkit for other universities to deploy their own AccessU graphs.

- **Advanced accessibility integration**
  - Explore integration with white canes, haptic feedback, or wearable devices.
  - Better cooperation with TalkBack and other assistive services.

## Getting Started (Development)

- **Requirements**
  - Android Studio (latest stable)
  - Android SDK 26+
  - A device or emulator with camera support (for QR + obstacles recommended on a real device)
  - `local.properties` with `GOOGLE_MAPS_API_KEY`

- **Run**
  - Open the project in Android Studio.
  - Add your Maps API key to `local.properties`:
    - `GOOGLE_MAPS_API_KEY=your_key_here`
  - Build and run the `app` module on a device.

> Note: Indoor routes rely on printed QR codes placed in the environment; the `qr_test` HTML files help you generate them.

## Contributors

- **Sanika Verma** – Navigation flows, indoor/outdoor UX, voice experience  
- **Niharika Rawat** – Obstacle detection, camera pipeline, accessibility behavior  

Team RouteCause – overall concept, design, and testing.

## License

Add your preferred license here (e.g. MIT, Apache 2.0).

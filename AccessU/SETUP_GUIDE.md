# App Skeleton Setup Guide

Follow these steps in order. Do them once as a team (one person can do it and push; others clone).

---

## Step 1: Create the Android project

1. Open **Android Studio**.
2. File → New → New Project.
3. Choose **Empty Activity** (or Empty Views Activity if you prefer).
4. Set:
   - Name: e.g. **UofAAccessibilityNav** (or AccessiNav, NavForAll)
   - Package name: e.g. **com.yourapp.ualberta.nav**
   - Save location: **Desktop/hacked** (or your chosen folder).
   - Language: **Kotlin**.
   - Minimum SDK: **24** (so most phones work).
5. Finish and let Android Studio build.

---

## Step 2: Initialize Git and .gitignore

1. In Android Studio: **VCS → Enable Version Control Integration → Git** (or do it in terminal).

2. In terminal, from your project root (e.g. `Desktop/hacked/UofAAccessibilityNav`):

```bash
cd /path/to/your/android/project
git init
```

3. Android Studio usually creates a `.gitignore` for you. If not, create one in the **project root** (same level as `app/`) with at least:

```
# Built files
*.iml
.gradle
/local.properties
/.idea/
.DS_Store
/build
/captures
*.apk
*.aab

# Keystore (never commit)
*.jks
*.keystore
```

4. First commit:

```bash
git add .
git commit -m "Initial Android skeleton"
```

---

## Step 3: Create remote repo and push

1. Create a **new repository** on GitHub (or GitLab):
   - Name: e.g. **uofa-accessibility-nav**
   - Do **not** add README / .gitignore (you already have them).

2. Add remote and push:

```bash
git remote add origin https://github.com/YOUR_USERNAME/uofa-accessibility-nav.git
git branch -M main
git push -u origin main
```

3. Invite **Sanika** and **Niharika** as collaborators (Settings → Collaborators).

---

## Step 4: Add dependencies (build.gradle)

In **app/build.gradle.kts** (or app/build.gradle), add under `dependencies { ... }`:

```
// Maps & Location
implementation("com.google.android.gms:play-services-maps:18.2.0")
implementation("com.google.android.gms:play-services-location:21.0.1")

// Camera (CameraX is easier than Camera2)
implementation("androidx.camera:camera-camera2:1.3.0")
implementation("androidx.camera:camera-lifecycle:1.3.0")
implementation("androidx.camera:camera-view:1.3.0")

// Networking (for ETS API, weather API)
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
// Or use OkHttp + manual JSON if you prefer

// TFLite (for obstacle / ML later)
implementation("org.tensorflow:tensorflow-lite:2.14.0")
```

Sync project (Sync Now in Android Studio).

---

## Step 5: Package structure (skeleton folders)

Create packages under **app/src/main/java/com/yourapp/ualberta/nav/** (match your package name):

- **ui** – Activities, Fragments, ViewModels (Niharika’s main UI).
- **campus** – Campus graph, routing, Dijkstra (Niharika).
- **voice** – TTS helper (AudioGuide), STT / intent parsing (Niharika).
- **transit** – ETS GTFS parsing, Realtime, “my stop” logic (Swayam).
- **weather** – Weather API, ice warning (Swayam).
- **obstacle** – Camera pipeline, obstacle detection (Sanika).

You can add placeholder Kotlin files:

- `voice/AudioGuide.kt`
- `campus/CampusGraph.kt`
- `transit/ETSService.kt`
- `weather/WeatherService.kt`
- `obstacle/ObstacleDetector.kt`

(Empty or with a simple `// TODO` so the team knows where to code.)

---

## Step 6: Permissions (AndroidManifest.xml)

In **app/src/main/AndroidManifest.xml**, inside `<manifest>` add:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

(Record audio for voice input; Camera for obstacle detection.)

---

## Step 7: README for the repo

Add a **README.md** in the project root with:

- Project name and one-line description (e.g. “Accessible navigation for U of A and Edmonton: campus wayfinding, obstacle detection, on-bus your stop next, voice-first for blind users”).
- How to open: Android Studio → Open → select project folder.
- How to run: Connect Android device or start emulator, Run.
- Link to **TASK_SPLIT_SWAYAM_SANIKA_NIHARIKA.md** (or paste the task split into the repo) so everyone sees who does what.

---

## Step 8: Who does what on the skeleton

- **Niharika:** Create Android project (Step 1), add **voice** and **campus** packages and placeholders, TTS helper (AudioGuide), permissions, README. Push first commit and add remote.
- **Swayam:** Add **transit** and **weather** packages and placeholders; add Retrofit (or HTTP) dependencies for ETS and weather.
- **Sanika:** Add **obstacle** package and placeholder; add Camera and TFLite dependencies.

Then everyone pulls, and you continue from **TASK_SPLIT_SWAYAM_SANIKA_NIHARIKA.md**.

---

## Quick checklist

- [ ] Android project created (Kotlin, min SDK 24).
- [ ] Git init, .gitignore, first commit.
- [ ] Remote repo created, push, collaborators added.
- [ ] Dependencies added (Maps, Location, Camera, Retrofit/OkHttp, TFLite).
- [ ] Packages created: ui, campus, voice, transit, weather, obstacle.
- [ ] Permissions added: INTERNET, LOCATION, CAMERA, RECORD_AUDIO.
- [ ] README with description and link to task split.
- [ ] Everyone has cloned repo and can build and run the app.

After this, you have a single Android codebase and repo; stick to Android only for the hackathon and use this skeleton to divide work cleanly.

# AccessU – Testing Guide (Sanika's Obstacle & Camera)

## How to Run

### Option A: Physical phone (recommended)
1. Connect Android phone via USB
2. Enable USB debugging (Settings → Developer options)
3. Android Studio → Select your phone in device dropdown → Run (green button)
4. Grant camera permission when prompted

### Option B: Emulator
1. Tools → Device Manager → Create/Start an emulator (e.g. Pixel 6, API 34)
2. Select emulator in device dropdown → Run
3. Grant camera permission
4. **Note:** Emulator uses virtual camera (default scene: room with objects). Point webcam at real objects if your emulator supports it.

---

## Test Checklist – Obstacle Detection

| # | Test | Steps | Expected |
|---|------|-------|----------|
| 1 | **Camera opens** | Launch app, grant camera | Live camera preview on screen |
| 2 | **Obstacle – person** | Point camera at a person | Hears "Obstacle ahead, move left" or "move right" |
| 3 | **Obstacle – chair** | Point camera at chair | Hears direction |
| 4 | **Obstacle – furniture** | Point at table, couch, etc. | Hears direction |
| 5 | **Clear path** | Point at empty hallway/floor | No speech |
| 6 | **Direction change** | Move camera so obstacle moves from left to right | Direction updates after ~4 sec |
| 7 | **TTS works** | Any obstacle in frame | Clear spoken announcement |
| 8 | **Optional UI** | Obstacle detected | Warning badge appears at bottom (~5 sec) |

---

## Quick Test (Emulator)

1. Run app on emulator
2. You should see the default virtual scene (e.g. room with objects)
3. If ML Kit detects objects in that scene → you’ll hear TTS
4. If nothing is detected → try a physical device with real camera

---

## Quick Test (Phone)

1. Run app on phone
2. Walk slowly in a hallway or room
3. Point camera forward at chest height
4. Approach a chair or person → should hear "Obstacle ahead, move left/right"

---

## Troubleshooting

| Issue | What to try |
|------|-------------|
| No camera preview | Check camera permission; try another app to confirm camera works |
| No speech | Ensure volume is up; check TTS language (default US English) |
| Never detects | Point at clear objects (chair, person); ensure good lighting |
| Speaks too often | Adjust confidence in `ObstacleDetector.kt` (e.g. 0.35 → 0.5) |
| Wrong direction | Hold phone more stable; move slowly |

---

## Files to Know

- `ObstacleDetector.kt` – Detection logic, confidence, path zone
- `ObstacleCameraScreen.kt` – Camera UI, frame processing rate
- `CameraPipeline.kt` – Frame capture and RGB conversion
- `AudioGuide.kt` – TTS (Niharika’s)

---

## Next Steps (After Testing)

1. **Integrate with Niharika** – She’ll wire Walking/On bus modes and pass `isActive` to your screen.
2. **Add distance (optional)** – Integrate MiDaS or similar depth model for "Obstacle 2 metres ahead".
3. **Calibrate** – If too many or too few alerts, tweak confidence and thresholds in `ObstacleDetector.kt`.

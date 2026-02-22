# Sanika's Task Status – Point by Point

## Goal
In walking mode, camera runs and speaks obstacle/clear path guidance. Voice-only; no need to look at screen.

---

## Task 1: Camera pipeline (Fri) – DONE

| Spec | Status |
|------|--------|
| Camera preview (Camera2 or CameraX) | Done – CameraX |
| Capture every 2nd or 3rd frame | Done – every 2nd frame |
| Pass to background thread/coroutine | Done – `Dispatchers.Default` coroutine |
| Verify frame received / FPS | Done – Logcat FPS logging |

---

## Task 2: Model integration (Sat AM) – PARTIAL

| Spec | Status |
|------|--------|
| Add depth (MiDaS) or object detection (YOLOv8) as TFLite | Partial – using ML Kit Object Detection (not TFLite MiDaS/YOLOv8) |
| Run inference on downscaled frame (256x256 or 320x320) | Partial – full resolution to ML Kit (it handles internally) |
| Get output: depth map or bounding boxes | Done – ML Kit returns bounding boxes |

---

## Task 3: Obstacle logic (Sat PM) – DONE

| Spec | Status |
|------|--------|
| Filter person, chair, etc. in front | Done – Home goods, Fashion, Food, Plant, unclassified |
| Decide position: left, center, right | Done – left/right from bbox |
| Output string: "Obstacle ahead" or "Obstacle 2 metres ahead, move right" | Partial – "Obstacle ahead, move left/right" (no distance yet; needs depth model) |

---

## Task 4: Obstacle TTS (Sat PM) – DONE

| Spec | Status |
|------|--------|
| Use Niharika's AudioGuide.speak() only | Done |
| Speak on obstacle detected | Done – "Obstacle ahead, move left/right" |
| Throttle: 5–10 seconds | Done – 6 seconds |

---

## Task 5: Hook into walking mode (Sun AM) – DONE

| Spec | Status |
|------|--------|
| Run camera only when Walking mode + nav active | Done – `isActive` parameter |
| When On bus, stop camera and stop messages | Done – `isActive = false` stops camera |

---

## Task 6: Polish (Sun PM) – DONE

| Spec | Status |
|------|--------|
| Tune threshold/distance so not too noisy | Done – center path 40%, throttle, confidence |
| Optional: Obstacle indicator on UI | Done – warning badge on obstacle |

---

## New: GPS-style guidance – DONE

| Condition | Message |
|-----------|---------|
| Obstacle in front (center path) | "Obstacle ahead, move left" or "move right" |
| Clear path (nothing in center; things on sides OK) | "Clear path ahead, go straight" |

---

## Not done yet
- Distance ("2 metres ahead") – requires MiDaS or depth model
- TFLite MiDaS / YOLOv8 – using ML Kit instead

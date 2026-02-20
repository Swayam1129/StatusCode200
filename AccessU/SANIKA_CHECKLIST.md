# Sanika – Obstacle and Camera – Completion Checklist

## Goal
In walking mode, camera runs and speaks Obstacle ahead, move left/right (and optionally distance). Voice-only users never need to look at the screen.

---

## Task 1: Camera pipeline (Fri) – DONE

| Requirement | Status |
|-------------|--------|
| Open camera preview (Camera2 or CameraX) | Done – CameraX |
| Capture every 2nd or 3rd frame | Done – every 2nd frame |
| Pass to background thread/coroutine | Done – `Dispatchers.Default` |
| Verify frame received / FPS | Done – Logcat FPS logging |

---

## Task 2: Model integration (Sat AM) – PARTIAL

| Requirement | Status |
|-------------|--------|
| Add depth (MiDaS) or object detection (YOLOv8) as TFLite | Partial – using ML Kit Object Detection (not TFLite) |
| Run inference on downscaled frame (256x256 or 320x320) | Partial – ML Kit handles internally, full res |
| Get output: depth map or bounding boxes | Done – bounding boxes from ML Kit |

---

## Task 3: Obstacle logic (Sat PM) – PARTIAL

| Requirement | Status |
|-------------|--------|
| Filter person, chair, etc. in front | Done |
| Decide position: left, center, right | Done – left/right |
| Output: "Obstacle 2 metres ahead, move right" or "Obstacle ahead" | Partial – "Obstacle ahead, move left/right" (no distance) |

---

## Task 4: Obstacle TTS (Sat PM) – DONE

| Requirement | Status |
|-------------|--------|
| Use Niharika's AudioGuide.speak() only | Done |
| Call AudioGuide.speak(Obstacle ahead, move right) | Done |
| Throttle: 5–10 seconds | Done – 5 seconds |

---

## Task 5: Hook into walking mode (Sun AM) – DONE

| Requirement | Status |
|-------------|--------|
| Run camera only when Walking mode + nav active | Done – `isActive` parameter |
| When On bus, stop camera and messages | Done – `isActive = false` |

---

## Task 6: Polish (Sun PM) – DONE

| Requirement | Status |
|-------------|--------|
| Tune threshold/distance so not too noisy | Done |
| Optional: simple Obstacle indicator on UI | Done – warning badge on obstacles |

---

## Summary

| Category | Done | Partial | Not Done |
|----------|------|---------|----------|
| Tasks | 4 | 2 | 0 |

**What's missing (optional):**
- **Distance** ("2 metres ahead") – needs depth model (MiDaS)
- **TFLite MiDaS/YOLOv8** – using ML Kit instead (works for object detection)

**Everything else is implemented.**

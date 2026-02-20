# Obstacle Detection – Testing Guide

## How to Test

### 1. Run on a physical Android phone
- Connect phone via USB, enable USB debugging
- In Android Studio: select your phone → Run
- Grant camera permission when prompted

### 2. Indoor testing (recommended)
- Walk slowly in a hallway or room
- Point the camera at chest height, facing forward
- Hold the phone roughly level (not tilted up at ceiling)

### 3. Test cases

| Scenario | Expected behavior |
|----------|-------------------|
| **Chair in path** | Speak "Obstacle ahead, move left" or "move right" after ~1 second |
| **Person ahead** | Speak direction after ~1 second |
| **Clear hallway** | No speech |
| **Side wall only** | No speech (side walls are ignored) |
| **Empty room** | No speech |

### 4. What was changed for accuracy
- **Stability filter**: Speaks only when the same obstacle is detected in 2 of the last 3 frames (reduces random false alarms)
- **Path zone**: Only center 70% of frame (ignores side walls)
- **Confidence**: 55% minimum
- **Always specific**: Only "move left" or "move right", never "move left or right"
- **6 second throttle**: Same message not repeated within 6 seconds

### 5. If it still misbehaves
- **Speaks too much**: Increase confidence in ObstacleDetector (e.g. 0.55 → 0.65) or tighten path zone
- **Never speaks**: Lower confidence (e.g. 0.55 → 0.45) or widen path zone
- **Wrong direction**: Hold phone more stable; ML Kit can be sensitive to motion blur

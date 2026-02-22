# Indoor Path Recording Guide

After recording path videos, follow these steps to add them to AccessU.

## 1. Record the video

- Walk the path from origin to destination
- Hold phone at chest height, camera facing forward
- Keep a steady pace (walking speed)
- Avoid occlusion (don't cover the camera)

## 2. Extract keyframes

From the project root:

```bash
chmod +x scripts/extract_keyframes.sh
./scripts/extract_keyframes.sh /path/to/your/video.mp4 etlc_to_busstop 0.5
```

- `0.5` = one frame every 2 seconds (adjust based on path length)
- For a 2-minute path: ~60 frames
- Output goes to `app/src/main/assets/paths/etlc_to_busstop/keyframes/`

## 3. Update metadata (optional)

Edit `app/src/main/assets/paths/<path_id>/metadata.json` and set `keyframeCount` to the number of extracted frames.

Or add a new path to `app/src/main/assets/paths/paths_index.json`:

```json
{
  "id": "your_path_id",
  "name": "Your Path Name",
  "description": "Short description",
  "origin": "Origin Building",
  "destination": "Destination",
  "keyframeCount": 60,
  "estimatedDurationSeconds": 120
}
```

Create the folder structure:

```
app/src/main/assets/paths/your_path_id/
  metadata.json
  keyframes/
    frame_0001.jpg
    frame_0002.jpg
    ...
```

## 4. Rebuild the app

```bash
./gradlew assembleDebug
```

## Existing paths (placeholders)

- **etlc_to_busstop** – ETLC to University Transit Centre
- **ccis_to_etlc** – CCIS to ETLC

Both have empty keyframes folders. Add keyframes after recording.

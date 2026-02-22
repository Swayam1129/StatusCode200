#!/bin/bash
# Extract keyframes from recorded path video for AccessU Indoor Navigation.
# Run after recording a path video. Place output in:
#   app/src/main/assets/paths/<path_id>/keyframes/
#
# Usage: ./extract_keyframes.sh <video_file> <path_id> [fps]
#   fps: frames per second to extract (default: 0.5 = one frame every 2 seconds)

VIDEO="$1"
PATH_ID="$2"
FPS="${3:-0.5}"

if [ -z "$VIDEO" ] || [ -z "$PATH_ID" ]; then
  echo "Usage: $0 <video_file> <path_id> [fps]"
  echo "Example: $0 ~/path_etlc_busstop.mp4 etlc_to_busstop 0.5"
  exit 1
fi

OUT_DIR="app/src/main/assets/paths/${PATH_ID}/keyframes"
mkdir -p "$OUT_DIR"

# Extract frame every 2 seconds (fps=0.5) - adjust for your video length
ffmpeg -i "$VIDEO" -vf "fps=$FPS" -q:v 2 "${OUT_DIR}/frame_%04d.jpg"

echo "Extracted keyframes to $OUT_DIR"
ls -la "$OUT_DIR" | head -20

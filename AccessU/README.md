# StatusCode200

Voice-first accessible navigation for University of Alberta and Edmonton: campus routing, obstacle detection, and real-time bus stop alerts for blind and low-vision users.

## Setup

**Google Maps (turn-by-turn walking):** Get an API key from [Google Cloud Console](https://console.cloud.google.com/). Enable **Directions API**. Add to `local.properties`:
```
GOOGLE_MAPS_API_KEY=your_key_here
```
Without it, campus routes use the local building graph; with it, "Get Google Turn-by-Turn" fetches walking steps from Google.

**Places API + Geocoding API (for real building coordinates):** Enable **Places API** (Text Search) and **Geocoding API** in Google Cloud Console. The app uses Places Text Search with campus location bias first, then Geocoding as fallback. Coordinates are cached locally. Use "Refresh Coordinates" to refetch. Routing never uses CAMPUS_CENTER fallback—unresolved nodes block routing with a clear message.

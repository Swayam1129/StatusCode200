# QR Code Indoor Navigation Setup

## 1. Generate QR codes

Open `scripts/generate_qr_codes.html` in a browser. It will generate QR codes for each path node.

**Print the page** and cut out each QR code. Laminate if possible for durability.

## 2. Place QR codes along the path

Place them at each node position (entrances, turns, exits):

- **ETLC → Bus Stop**: n1 (ETLC entrance) → n2 (junction) → n3 (exit) → n4 (bus stop)
- **CCIS → ETLC**: n1 (CCIS entrance) → n2 (junction) → n3 (ETLC entrance)

## 3. QR format

Each QR encodes: `pathId:nodeId`

- Example: `etlc_to_busstop:n1`
- The app scans and looks up the node in the path graph

## 4. Add or edit paths

Edit `app/src/main/assets/paths/<path_id>/graph.json`:

```json
{
  "pathId": "your_path_id",
  "nodes": [
    {"id": "n1", "name": "Start", "instruction": "...", "nextNodeId": "n2"},
    {"id": "n2", "name": "Turn", "instruction": "...", "nextNodeId": null}
  ]
}
```

Add the path to `paths_index.json` and add its nodes to `generate_qr_codes.html`.

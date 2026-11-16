# Geely Cityray (G426_J1) cluster font

Source: [PR #3](https://github.com/mmwtl/AtlasDim/pull/3), commit
`0b6fc4cd66e04e7a3afb2055a9bc81e2054ba085`. Its author reports an offline
extraction from a user-provided 60,536,188-byte KZB backup and a visual match
on a real Cityray. This integration has not repeated the extraction or vehicle check.
Do not commit the proprietary KZB or fonts.

QNX resource path:
`/apps/cluster/FX11_HEV/bin/hmi/GLY_FS11-A2J1/ClusterHMI/resource.kzb`

| Typeface | Offset (hex) | Length | SHA-256 |
| --- | --- | ---: | --- |
| Adelle Sans Regular | 0x22B9C | 277648 | 0248c869a38968e4ab0b246fc157e2c5ad88660feee19a6124598363b3a25e86 |

The Cityray menu offers System (ID 0) and “Как на приборной панели” (ID 1,
Adelle Sans Regular). The earlier GeelyBCTF mapping was superseded by PR #3.
IDs 2–4 from older settings fall back to System in the editor and renderer.
Atlas keeps its existing built-in and imported font choices.

Safety and compatibility:
- Only read the QNX resource, never modify it.
- Use the exact Cityray resource path; Atlas retains its original wildcard.
- Verify extracted font length and SHA-256 before caching. As in the profiles
  branch, do not gate on the enclosing KZB size: the font bytes are checked directly.
- Use a distinct `Cityray-AdelleSans-Regular.ttf` cache filename, so the earlier
  GeelyBCTF cache cannot be reused accidentally.
- `dd` uses 4-byte blocks for this font: its offset is not divisible by 8.
- Choose font resources from the scene's saved `vehicle`, rather than mutable
  global model state. The editor uses the currently selected vehicle profile.
- Other Cityray firmware versions are unverified; a matching font is not proof
  that scene layouts or QNX daemon behavior are compatible.

Clock, fuel and temperature editors also have four arrow buttons, one scene pixel
per tap, clamped to the panel bounds. Dragging and resizing retain their 4-pixel
snap; background geometry and placement presets retain their existing behavior.

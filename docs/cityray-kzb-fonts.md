# Geely Cityray (G426_J1) – verified instrument-cluster font

Source: user-provided original Cityray Kanzi `resource.kzb` backup
(60,536,188 bytes), tested on the actual Cityray instrument cluster.
**Do not commit or redistribute the proprietary KZB or the extracted font.**

QNX path (verified on Cityray G426_J1):

```text
/apps/cluster/FX11_HEV/bin/hmi/GLY_FS11-A2J1/ClusterHMI/resource.kzb
```

Only **two built-in choices** are exposed in the Cityray profile for Clock,
Fuel and Temperature: **Android system** (font ID `0`) and
**"Как на приборной панели"** (font ID `1`, Adelle Sans Regular).
Older GeelyBCTF and user-font choices remain available in the original Geely
profile, but they are hidden and not rendered in the Cityray profile.

## Confirmed Adelle Sans Regular asset

| Item | Value |
| --- | --- |
| File in Kanzi resources | `AdelleSans-Regular.ttf` |
| SFNT offset | `0x22B9C` / decimal `142236` |
| Byte length | `277648` |
| SHA-256 | `0248c869a38968e4ab0b246fc157e2c5ad88660feee19a6124598363b3a25e86` |
| Verified KZB length | `60536188` |
| Android cache name | `Cityray-AdelleSans-Regular.ttf` |

The selected-font workflow reuses `ClusterFont.fetch(QnxShell, SceneConfig)`,
triggered by the existing "Показать / применить" action. This is a read-only
operation on the QNX source. The known offset and byte length are multiples
of **4**, not 8; using `dd bs=4 skip=35559 count=69412` reads exactly the
font. `openssl base64` transfers data over the already-established Telnet
shell, Android verifies size and SHA-256, then stores the bytes in app-private
storage and loads them with `Typeface.createFromFile`. On subsequent
applications the cached font is reused. Until extraction, the preview falls
back to the Android system font.

The exact KZB file size and extracted font SHA-256 are checked for this firmware.
Other Cityray firmware resources have not been verified.

## 1-pixel positioning controls

Each Clock, Fuel and Temperature `LayerEditor` has an additional d-pad
below the **existing** coordinates and quick-position presets. Each tap changes
only `x` or `y` by one *cluster coordinate pixel* (`SceneConfig.WIDTH=1920`,
`HEIGHT=532`), subject to `0 <= x <= WIDTH-w` and
`0 <= y <= HEIGHT-h`. It updates the existing text fields and preview and
uses the original Save/Apply mechanism. This intentionally bypasses the
preview's existing **4-pixel drag snapping**; dragging, resizing, presets,
display / z and background positioning are left as they were.

Suggested offline regression checks:
1. Original Geely model still offers the five font choices and original offsets.
2. Cityray shows precisely two choices per panel; switching from a legacy
   selection reverts it to System before applying.
3. The three sets of arrows move their own panel only; width and height do
   not change. Coordinates are bounded by the 1920 x 532 scene.
4. Every one-pixel change is visible in numeric fields and the preview,
   then persists with the existing "Показать / применить" action.
5. Changing the model cannot enable other fonts for Cityray in the renderer.
6. On a compatible parked/test vehicle, verify the extracted Adelle resource
   and ensure vital instrument and warning indications remain unobstructed.

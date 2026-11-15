# Geely Cityray (G426_J1) Kanzi fonts

Source: user-provided backup of the Cityray instrument-cluster resource file,
verified offline (60,536,188 bytes). Do not commit the proprietary KZB or fonts.

QNX resource path:
`/apps/cluster/FX11_HEV/bin/hmi/GLY_FS11-A2J1/ClusterHMI/resource.kzb`

The original AtlasDim wildcard remains valid:
`/apps/cluster/*/bin/hmi/*/ClusterHMI/resource.kzb`.

| Typeface | Offset (hex) | Length | SHA-256 |
| --- | --- | ---: | --- |
| GeelyBCTF-Regular.ttf | 0x3992620 | 83288 | 5c9b8ed5763106dffaa65a0264e8fe98351be75604403520a81eaf544ff05db2 |
| GeelyBCTF-Switches.ttf | 0x39A6B80 | 82312 | f73a22cf07912c7399749aa683fffd0da12d3e54a9e2840a5284de56496c2f1d |
| GeelyBCTF-Bold.ttf | 0x396AA1C | 80268 | 0a59701719059ee8b42684b2067611872456fda438d7155cb56b13da5ee3ac27 |
| GeelyBCTF-Extralight.ttf | 0x397E3B0 | 82536 | 403b569420679f3dd41e32434cb679d80e29ab1b3fa54c73c5f943eb1d2a6365 |

Regular, Switches and Bold are mapped to existing font choices.
Extralight is documented only, not exposed as a fourth built-in choice.

Safety and compatibility:
- Only read the QNX resource, never modify it.
- The Cityray profile checks the resource file length before extracting.
- Checks SHA-256 of each extracted font before caching.
- Uses distinct cache names for Cityray, preserving original profile files.
- Existing installations default to the original profile.
- Other Cityray firmware versions are **not** yet verified.
- Vehicle-specific selection is currently for font resources only, not an
  assurance that all scene layouts or QNX daemon functions are compatible.

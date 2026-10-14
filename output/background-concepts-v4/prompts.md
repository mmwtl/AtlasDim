# Правка нижней правой области · версия 4

Каждый фон и каждый эскиз исправлены отдельным вызовом встроенного image_gen. CLI/API fallback не использовался. Исходные варианты: [версия 3](../background-concepts-v3/index.html).

## 1. Графитовая дорога

### Чистый фон

Вход: `../background-concepts-v3/01-graphite-road-background-1920x532.png`.

```text
Use case: precise local correction of an automotive cluster wallpaper. Edit the supplied flat ultrawide image in its original framing, keeping the composition, road geometry, horizon, palette and all other regions unchanged. The problem is the remaining excessive dark pocket / vignette in the lower-right foreground, around normalized x=.84–.92, y=.68–.84. Restore the CONTINUOUS normal surface texture through this whole lower-right area and its surrounding feathered border (x=.78–.925, y=.60–.87), so it matches the adjacent foreground at x=.70–.82. Road/asphalt/gridded plane must be visibly present in this area, with exactly its usual brightness, perspective and detail. Do NOT create a brighter patch, glow, halo, badge, pedestal, shadow tile, rectangular plate or spotlight. The surface must be an uninterrupted part of the scene, not a reserved icon zone. Do not darken this area to accommodate a future icon. Keep the genuine narrow pure black FAR side edges, especially the far-right gear-column zone beyond x=.935 and far-left brake zone x<.065. Keep the lower status strip black and keep the center scene unchanged. Preserve the source's extremely wide aspect ratio: it fills the entire frame with no margins, new bezel, border or cropping.
This is the CLEAN BACKGROUND for variant Графитовая дорога, with no UI or icons. Keep it completely clean: no cars, digits, letters, icons or borders. The requested edit is only the foreground surface continuity; preserve this theme: Minimal matte graphite asphalt road, two restrained desaturated steel-blue edge lines and a few long-spaced dim dash marks. Thin low cool-blue horizon glow, dark navy sky. No landscape props. Calm understated OEM aesthetic.. In the lower-right foreground, replace all ink-black reserved-icon-area darkening with the existing undistorted road surface and continuous fine asphalt texture. The foreground surface at x=.88,y=.78 should have the same natural local exposure as x=.80,y=.78, with no dedicated badge/pocket.
```

### Эскиз

Вход 1: `../background-concepts-v3/01-graphite-road-sketch.png`. Вход 2: исправленный фон из предыдущего вызова.

```text
Use case: precise-object-edit / automotive UI mockup correction.
Input image 1 is the EDIT TARGET: finished instrument cluster mockup for "Графитовая дорога". Input image 2 is the matching corrected CLEAN BACKGROUND, used only as a reference for ordinary foreground surface texture.
Perform ONE local correction in image 1: erase the excessive black reserved-icon pocket / shadow / vignette behind and around the SMALL white front-view car glyph in the lower-right, near normalized x=.875–.89, y=.76. Reconstruct the continuous ordinary asphalt texture through the whole surrounding lower-right region x=.79–.925,y=.66–.84, matching the adjacent surface exposure at x=.78–.84. Use image 2 for the natural uninterrupted surface, aligned to the existing geometry in image 1. The glyph should simply float over the SAME surface, without any local darkening, brightening, halo, spotlight, drop shadow, black rounded tile, rectangle, pedestal or plate. There must be visible ordinary surface immediately on every side of the small glyph.
The SMALL CAR GLYPH itself must remain unchanged in its original position, size, white color and shape. Do not emphasize it. Also preserve the LARGE top-down car at center, speed 0, km/h, warning icons, Eco, R N D P and original selected-P outline, left parking brake icon, rpm reading, every lower strip icon and text, clock and temperature. All unchanged.
Keep black only at the genuine far edges for the opaque native brake and gear icons: far right beyond x=.94, far left beyond x=.075, and the original bottom status strip. The far-right edge may fade into black BELOW the gear column only after x=.925, so the little car and its surrounding surface do not become a black island. Do not expand black side bands. Do not move any icon or change values, scenery, horizon, road lines, grid layout, central car or panel framing. Return the SAME flat screen-only panorama and the same image-1 aspect ratio, with no bezel, border, title, new text or margins. The sky is smooth empty dark navy with a thin blue horizon, NO mountains anywhere.
```

## 2. Синяя сетка

### Чистый фон

Вход: `../background-concepts-v3/02-blue-grid-background-1920x532.png`.

```text
Use case: precise local correction of an automotive cluster wallpaper. Edit the supplied flat ultrawide image in its original framing, keeping the composition, road geometry, horizon, palette and all other regions unchanged. The problem is the remaining excessive dark pocket / vignette in the lower-right foreground, around normalized x=.84–.92, y=.68–.84. Restore the CONTINUOUS normal surface texture through this whole lower-right area and its surrounding feathered border (x=.78–.925, y=.60–.87), so it matches the adjacent foreground at x=.70–.82. Road/asphalt/gridded plane must be visibly present in this area, with exactly its usual brightness, perspective and detail. Do NOT create a brighter patch, glow, halo, badge, pedestal, shadow tile, rectangular plate or spotlight. The surface must be an uninterrupted part of the scene, not a reserved icon zone. Do not darken this area to accommodate a future icon. Keep the genuine narrow pure black FAR side edges, especially the far-right gear-column zone beyond x=.935 and far-left brake zone x<.065. Keep the lower status strip black and keep the center scene unchanged. Preserve the source's extremely wide aspect ratio: it fills the entire frame with no margins, new bezel, border or cropping.
This is the CLEAN BACKGROUND for variant Синяя сетка, with no UI or icons. Keep it completely clean: no cars, digits, letters, icons or borders. The requested edit is only the foreground surface continuity; preserve this theme: Sparse geometric midnight-navy perspective grid with thin restrained cyan-blue lines and wide spacing. Horizontal cross-lines remain much dimmer than diagonal lines, low soft blue horizon glow. No asphalt, buildings, mountains, stars or objects. Restrained digital-tech aesthetic.. In the lower-right foreground, replace all ink-black reserved-icon-area darkening with the existing undistorted navy plane and unbroken thin cyan perspective grid. The foreground surface at x=.88,y=.78 should have the same natural local exposure as x=.80,y=.78, with no dedicated badge/pocket.
```

### Эскиз

Вход 1: `../background-concepts-v3/02-blue-grid-sketch.png`. Вход 2: исправленный фон из предыдущего вызова.

```text
Use case: precise-object-edit / automotive UI mockup correction.
Input image 1 is the EDIT TARGET: finished instrument cluster mockup for "Синяя сетка". Input image 2 is the matching corrected CLEAN BACKGROUND, used only as a reference for ordinary foreground surface texture.
Perform ONE local correction in image 1: erase the excessive black reserved-icon pocket / shadow / vignette behind and around the SMALL white front-view car glyph in the lower-right, near normalized x=.875–.89, y=.76. Reconstruct the continuous navy perspective grid plane, carrying the thin cyan lines and normal navy field uninterrupted through the whole surrounding lower-right region x=.79–.925,y=.66–.84, matching the adjacent surface exposure at x=.78–.84. Use image 2 for the natural uninterrupted surface, aligned to the existing geometry in image 1. The glyph should simply float over the SAME surface, without any local darkening, brightening, halo, spotlight, drop shadow, black rounded tile, rectangle, pedestal or plate. There must be visible ordinary surface immediately on every side of the small glyph.
The SMALL CAR GLYPH itself must remain unchanged in its original position, size, white color and shape. Do not emphasize it. Also preserve the LARGE top-down car at center, speed 0, km/h, warning icons, Eco, R N D P and original selected-P outline, left parking brake icon, rpm reading, every lower strip icon and text, clock and temperature. All unchanged.
Keep black only at the genuine far edges for the opaque native brake and gear icons: far right beyond x=.94, far left beyond x=.075, and the original bottom status strip. The far-right edge may fade into black BELOW the gear column only after x=.925, so the little car and its surrounding surface do not become a black island. Do not expand black side bands. Do not move any icon or change values, scenery, horizon, road lines, grid layout, central car or panel framing. Return the SAME flat screen-only panorama and the same image-1 aspect ratio, with no bezel, border, title, new text or margins. The sky is smooth empty dark navy with a thin blue horizon, NO mountains anywhere.
```

## 3. Горный перевал

### Чистый фон

Вход: `../background-concepts-v3/03-mountain-pass-background-1920x532.png`.

```text
Use case: precise local correction of an automotive cluster wallpaper. Edit the supplied flat ultrawide image in its original framing, keeping the composition, road geometry, horizon, palette and all other regions unchanged. The problem is the remaining excessive dark pocket / vignette in the lower-right foreground, around normalized x=.84–.92, y=.68–.84. Restore the CONTINUOUS normal surface texture through this whole lower-right area and its surrounding feathered border (x=.78–.925, y=.60–.87), so it matches the adjacent foreground at x=.70–.82. Road/asphalt/gridded plane must be visibly present in this area, with exactly its usual brightness, perspective and detail. Do NOT create a brighter patch, glow, halo, badge, pedestal, shadow tile, rectangular plate or spotlight. The surface must be an uninterrupted part of the scene, not a reserved icon zone. Do not darken this area to accommodate a future icon. Keep the genuine narrow pure black FAR side edges, especially the far-right gear-column zone beyond x=.935 and far-left brake zone x<.065. Keep the lower status strip black and keep the center scene unchanged. Preserve the source's extremely wide aspect ratio: it fills the entire frame with no margins, new bezel, border or cropping.
This is the CLEAN BACKGROUND for variant Горный перевал, with no UI or icons. Keep it completely clean: no cars, digits, letters, icons or borders. The requested edit is only the foreground surface continuity; preserve this theme: Blue-hour straight mountain road with low layered slate-blue mountain silhouettes and modest mist, low blue-grey horizon, sparse roadside guardrails, no fine foliage texture. Natural atmospheric travel aesthetic.. In the lower-right foreground, replace all ink-black reserved-icon-area darkening with the existing undistorted road surface and continuous fine asphalt texture. The foreground surface at x=.88,y=.78 should have the same natural local exposure as x=.80,y=.78, with no dedicated badge/pocket.
```

### Эскиз

Вход 1: `../background-concepts-v3/03-mountain-pass-sketch.png`. Вход 2: исправленный фон из предыдущего вызова.

```text
Use case: precise-object-edit / automotive UI mockup correction.
Input image 1 is the EDIT TARGET: finished instrument cluster mockup for "Горный перевал". Input image 2 is the matching corrected CLEAN BACKGROUND, used only as a reference for ordinary foreground surface texture.
Perform ONE local correction in image 1: erase the excessive black reserved-icon pocket / shadow / vignette behind and around the SMALL white front-view car glyph in the lower-right, near normalized x=.875–.89, y=.76. Reconstruct the continuous ordinary asphalt texture through the whole surrounding lower-right region x=.79–.925,y=.66–.84, matching the adjacent surface exposure at x=.78–.84. Use image 2 for the natural uninterrupted surface, aligned to the existing geometry in image 1. The glyph should simply float over the SAME surface, without any local darkening, brightening, halo, spotlight, drop shadow, black rounded tile, rectangle, pedestal or plate. There must be visible ordinary surface immediately on every side of the small glyph.
The SMALL CAR GLYPH itself must remain unchanged in its original position, size, white color and shape. Do not emphasize it. Also preserve the LARGE top-down car at center, speed 0, km/h, warning icons, Eco, R N D P and original selected-P outline, left parking brake icon, rpm reading, every lower strip icon and text, clock and temperature. All unchanged.
Keep black only at the genuine far edges for the opaque native brake and gear icons: far right beyond x=.94, far left beyond x=.075, and the original bottom status strip. The far-right edge may fade into black BELOW the gear column only after x=.925, so the little car and its surrounding surface do not become a black island. Do not expand black side bands. Do not move any icon or change values, scenery, horizon, road lines, grid layout, central car or panel framing. Return the SAME flat screen-only panorama and the same image-1 aspect ratio, with no bezel, border, title, new text or margins.
```

## 4. Световой тоннель

### Чистый фон

Вход: `../background-concepts-v3/04-light-tunnel-background-1920x532.png`.

```text
Use case: precise local correction of an automotive cluster wallpaper. Edit the supplied flat ultrawide image in its original framing, keeping the composition, road geometry, horizon, palette and all other regions unchanged. The problem is the remaining excessive dark pocket / vignette in the lower-right foreground, around normalized x=.84–.92, y=.68–.84. Restore the CONTINUOUS normal surface texture through this whole lower-right area and its surrounding feathered border (x=.78–.925, y=.60–.87), so it matches the adjacent foreground at x=.70–.82. Road/asphalt/gridded plane must be visibly present in this area, with exactly its usual brightness, perspective and detail. Do NOT create a brighter patch, glow, halo, badge, pedestal, shadow tile, rectangular plate or spotlight. The surface must be an uninterrupted part of the scene, not a reserved icon zone. Do not darken this area to accommodate a future icon. Keep the genuine narrow pure black FAR side edges, especially the far-right gear-column zone beyond x=.935 and far-left brake zone x<.065. Keep the lower status strip black and keep the center scene unchanged. Preserve the source's extremely wide aspect ratio: it fills the entire frame with no margins, new bezel, border or cropping.
This is the CLEAN BACKGROUND for variant Световой тоннель, with no UI or icons. Keep it completely clean: no cars, digits, letters, icons or borders. The requested edit is only the foreground surface continuity; preserve this theme: Dark anthracite straight tunnel road with repeating slim cool-blue illuminated sidewall ribs receding to a low central distant opening. Keep ribs sparse and subdued, no roof ribs crossing upper central space; matte asphalt center and soft side reflections. Restrained architectural sci-fi aesthetic.. In the lower-right foreground, replace all ink-black reserved-icon-area darkening with the existing undistorted road surface and continuous fine asphalt texture. The foreground surface at x=.88,y=.78 should have the same natural local exposure as x=.80,y=.78, with no dedicated badge/pocket.
```

### Эскиз

Вход 1: `../background-concepts-v3/04-light-tunnel-sketch.png`. Вход 2: исправленный фон из предыдущего вызова.

```text
Use case: precise-object-edit / automotive UI mockup correction.
Input image 1 is the EDIT TARGET: finished instrument cluster mockup for "Световой тоннель". Input image 2 is the matching corrected CLEAN BACKGROUND, used only as a reference for ordinary foreground surface texture.
Perform ONE local correction in image 1: erase the excessive black reserved-icon pocket / shadow / vignette behind and around the SMALL white front-view car glyph in the lower-right, near normalized x=.875–.89, y=.76. Reconstruct the continuous ordinary asphalt texture through the whole surrounding lower-right region x=.79–.925,y=.66–.84, matching the adjacent surface exposure at x=.78–.84. Use image 2 for the natural uninterrupted surface, aligned to the existing geometry in image 1. The glyph should simply float over the SAME surface, without any local darkening, brightening, halo, spotlight, drop shadow, black rounded tile, rectangle, pedestal or plate. There must be visible ordinary surface immediately on every side of the small glyph.
The SMALL CAR GLYPH itself must remain unchanged in its original position, size, white color and shape. Do not emphasize it. Also preserve the LARGE top-down car at center, speed 0, km/h, warning icons, Eco, R N D P and original selected-P outline, left parking brake icon, rpm reading, every lower strip icon and text, clock and temperature. All unchanged.
Keep black only at the genuine far edges for the opaque native brake and gear icons: far right beyond x=.94, far left beyond x=.075, and the original bottom status strip. The far-right edge may fade into black BELOW the gear column only after x=.925, so the little car and its surrounding surface do not become a black island. Do not expand black side bands. Do not move any icon or change values, scenery, horizon, road lines, grid layout, central car or panel framing. Return the SAME flat screen-only panorama and the same image-1 aspect ratio, with no bezel, border, title, new text or margins.
```

## 5. Медный рассвет

### Чистый фон

Вход: `../background-concepts-v3/05-copper-dawn-background-1920x532.png`.

```text
Use case: precise local correction of an automotive cluster wallpaper. Edit the supplied flat ultrawide image in its original framing, keeping the composition, road geometry, horizon, palette and all other regions unchanged. The problem is the remaining excessive dark pocket / vignette in the lower-right foreground, around normalized x=.84–.92, y=.68–.84. Restore the CONTINUOUS normal surface texture through this whole lower-right area and its surrounding feathered border (x=.78–.925, y=.60–.87), so it matches the adjacent foreground at x=.70–.82. Road/asphalt/gridded plane must be visibly present in this area, with exactly its usual brightness, perspective and detail. Do NOT create a brighter patch, glow, halo, badge, pedestal, shadow tile, rectangular plate or spotlight. The surface must be an uninterrupted part of the scene, not a reserved icon zone. Do not darken this area to accommodate a future icon. Keep the genuine narrow pure black FAR side edges, especially the far-right gear-column zone beyond x=.935 and far-left brake zone x<.065. Keep the lower status strip black and keep the center scene unchanged. Preserve the source's extremely wide aspect ratio: it fills the entire frame with no margins, new bezel, border or cropping.
This is the CLEAN BACKGROUND for variant Медный рассвет, with no UI or icons. Keep it completely clean: no cars, digits, letters, icons or borders. The requested edit is only the foreground surface continuity; preserve this theme: Straight charcoal road at dawn with thin desaturated copper-amber low horizon glow and dark indigo sky. Thin amber road edges and a few very dim warm roadside markers. Low distant mountain silhouettes, no visible sun. Warm restrained serene aesthetic.. In the lower-right foreground, replace all ink-black reserved-icon-area darkening with the existing undistorted road surface and continuous fine asphalt texture. The foreground surface at x=.88,y=.78 should have the same natural local exposure as x=.80,y=.78, with no dedicated badge/pocket.
```

### Эскиз

Вход 1: `../background-concepts-v3/05-copper-dawn-sketch.png`. Вход 2: исправленный фон из предыдущего вызова.

```text
Use case: precise-object-edit / automotive UI mockup correction.
Input image 1 is the EDIT TARGET: finished instrument cluster mockup for "Медный рассвет". Input image 2 is the matching corrected CLEAN BACKGROUND, used only as a reference for ordinary foreground surface texture.
Perform ONE local correction in image 1: erase the excessive black reserved-icon pocket / shadow / vignette behind and around the SMALL white front-view car glyph in the lower-right, near normalized x=.875–.89, y=.76. Reconstruct the continuous ordinary asphalt texture through the whole surrounding lower-right region x=.79–.925,y=.66–.84, matching the adjacent surface exposure at x=.78–.84. Use image 2 for the natural uninterrupted surface, aligned to the existing geometry in image 1. The glyph should simply float over the SAME surface, without any local darkening, brightening, halo, spotlight, drop shadow, black rounded tile, rectangle, pedestal or plate. There must be visible ordinary surface immediately on every side of the small glyph.
The SMALL CAR GLYPH itself must remain unchanged in its original position, size, white color and shape. Do not emphasize it. Also preserve the LARGE top-down car at center, speed 0, km/h, warning icons, Eco, R N D P and original selected-P outline, left parking brake icon, rpm reading, every lower strip icon and text, clock and temperature. All unchanged.
Keep black only at the genuine far edges for the opaque native brake and gear icons: far right beyond x=.94, far left beyond x=.075, and the original bottom status strip. The far-right edge may fade into black BELOW the gear column only after x=.925, so the little car and its surrounding surface do not become a black island. Do not expand black side bands. Do not move any icon or change values, scenery, horizon, road lines, grid layout, central car or panel framing. Return the SAME flat screen-only panorama and the same image-1 aspect ratio, with no bezel, border, title, new text or margins.
```

## Экспорт

Фоны: центральное пропорциональное кадрирование исходников 2172×724 до 2172×602, затем 1920×532. Эскизы: исходные 2172×724. Точные боковые чёрные зоны и переходы заданы при экспорте. Справа ниже передач переход отодвинут к крайней правой части. Локальная маска под маленькой машиной не применяется; нижняя правая поверхность восстановлена в самих исходных изображениях.


## Уточнение третьего варианта · r2

Исправлена только геометрия правой маски экспорта: переход по всей высоте занимает x=0.89…0.925; область x≥0.925 чёрная. Смещение края наружу ниже передач отключено. Фон и эскиз повторно экспортированы из тех же исходников, без новой генерации сцены. Изменились только пиксели правого нижнего края; остальные восемь PNG совпадают с предыдущими побайтно. Размер фона сохранён: 1920×532.


## Без разметки

Дата: 2026-10-12. Встроенный `image_gen`, режим правки по одному входному изображению; `transparent_background=false`. По одной попытке на каждый фон (два вызова всего). CLI/API и OpenCV inpaint не использовались.

### 01 · Графитовая дорога

Вход: `01-graphite-road-background-1920x532.png`.
Выход: `01-graphite-road-background-1920x532-nomarks.png`.

```text
Use case: precise-object-edit. Input image 1 is the EDIT TARGET, a flat opaque 1920x532 automotive instrument-cluster wallpaper. Remove ONLY every road marking: all white/grey dashed lane lines and BOTH glowing blue continuous road-edge stripes. Re-render their entire footprints as uninterrupted matte graphite asphalt, with crisp fine granular asphalt texture as sharp and detailed as the adjacent original foreground. No blurry inpaint patches, no smears, no ghost stripes, no linear reflections, no painted lines of any kind. The asphalt-to-shoulder transition may be subtle material contrast, never a bright line. Preserve the exact source composition and framing, central vanishing point at approximately x=960,y=157 and horizontal horizon at y=157 in the 1920x532 coordinate system; preserve the thin low cool-blue HORIZONTAL horizon glow, empty dark navy sky, matte graphite road, original exposure and perspective. Keep all scenery unchanged; no landscape props, mountains, cars, UI, text, icons or borders. Preserve the source pure black side zones and fades, including left x<.065, right x>.935, and the original black lower strip. Return the SAME ultrawide aspect ratio and same full frame, preferably exactly 1920x532. Do not add margins or crop or stretch. Calm understated OEM aesthetic. Critical: road surface is entirely unmarked, uniformly fine textured and sharp; the horizontal horizon glow stays, the diagonal blue road-edge lines must be completely absent.
```

### 03 · Горный перевал

Вход: `03-mountain-pass-background-1920x532-r2.png`.
Выход: `03-mountain-pass-background-1920x532-nomarks.png`.

```text
Use case: precise-object-edit. Input image 1 is the EDIT TARGET, a flat opaque 1920x532 automotive instrument-cluster wallpaper. Remove ONLY every painted road marking: ALL white dashed lane markings and BOTH white continuous road-edge stripes, all the way to the vanishing point. Regenerate their footprints and the road as uninterrupted matte blue-grey asphalt with crisp fine granular texture as sharp as the source foreground. No blurry inpaint patches, smears, stripe ghosts, light streaks, long linear reflections or markings of any kind. The physical asphalt-to-shoulder / curb transition can remain, but no painted bright edge stripe. Preserve the EXACT source full-frame composition, perspective, central vanishing point approximately x=947,y=176 in 1920x532 coordinates, horizon at the SAME height, source palette, exposure and sky. Preserve all layered slate-blue mountain silhouettes, modest mist, original guardrails, posts and the small discrete glowing blue-white lamps on the guardrail posts, in their exact source positions. Those roadside post lamps are not markings: KEEP THEM. Keep the original pure black side zones and fades, left x<.065, right x>.935, and the black lower status strip. Same ultrawide aspect ratio and same framing, preferably exactly 1920x532, no distortion or zoom or new margins. No text, cars, icons, UI, frame, new vegetation or added objects. Blue-hour calm mountain road. Critical: absolutely no dashed or solid road lines, asphalt entirely unmarked, fine sharp texture everywhere including former stripes; guardrail lamps remain unchanged.
```

### Постобработка и проверка

Оба генератора вернули RGB PNG 2172×724 с дополнительными чёрными полями сверху и снизу. Из каждого результата взят прямоугольник `(left=0, top=60, right=2172, bottom=662)`, то есть 2172×602, затем уменьшен до 1920×532 фильтром Pillow LANCZOS. Отношение сторон кадра отличается от целевого лишь на 0.029%; геометрия визуально совпадает при наложении 50/50 с исходником. Привязка подтверждена по горизонту/точке схода, силуэтам гор и фонарикам, без отдельного перемещения частей сцены.

При экспорте восстановлены точные RGB (0,0,0) в тех пикселях исходника, которые уже были абсолютно чёрными в боковых областях x<192 / x≥1708 и нижней области y≥475. Дополнительно x<125, x≥1796 и y≥520 зафиксированы абсолютно чёрными. Это только контроль крайних чёрных зон; дорожное полотно не дорисовывалось, не размывалось, не ретушировалось и не повышалась его резкость программно. Итог — полностью непрозрачные PNG RGB, без альфа-канала.

Просмотрены оба полных кадра, наложения на исходники и по три кропа дорожного полотна в масштабе 200% (ближайший сосед, без сглаживания): центр и бывшая разметка слева/справа. Разметки, светящихся диагональных полос и размытых пятен не обнаружено. Дискретные фонарики на ограждении 03 сохранены. Фактура асфальта слегка более выраженная/крупная, чем в исходниках; точного попиксельного совпадения остальных деталей генеративная правка не гарантирует. Проверка только изображений, без ГУ/ADAS.

Наложения и кропы: `qa-nomarks/`. Отчёт: `qa-nomarks/report.md`.

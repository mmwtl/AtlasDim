# QA · Фаза 1 — карта дисплеев (Presentation)

## Устройство и сборка

- ГУ G636 / OneOS 1.0.20250623G312, Android 11 (API 30), IVI 1440×1920 (дисплей 0).
- APK: `app/build/outputs/apk/debug/AtlasDim-debug.apk`,
  `com.mmwtl.atlasdim` v0.1.0 (versionCode 1), debug, разрешений нет.
- Сборка: JDK 17, compileSdk 35, minSdk 26, targetSdk 30.

## Установка и запуск

```sh
adb -s SERIAL install -r app/build/outputs/apk/debug/AtlasDim-debug.apk
adb -s SERIAL shell am start -n com.mmwtl.atlasdim/.MainActivity
adb -s SERIAL logcat -s AtlasDim
```

## Шаги

1. Открыть AtlasDim на IVI: в логе должна уйти строка `Display map (5): …`
   (id/uniqueId/разрешение/имя/флаги каждого дисплея) — сверить с
   `dumpsys display` из `reference/dumps/display.txt`.
2. Для дисплеев 1, 3, 4 (и при желании 2) нажать «Presentation» и смотреть,
   какой физический экран засветился: заливка + «AtlasDim · Display N» + часы.
   Цвет: 1 — синий, 2 — пурпурный, 3 — оранжевый, 4 — красный.
3. Повторное нажатие — скрыть; переключение — сначала скрыть, потом показать
   на новом дисплее.
4. Проверка на ГУ: смотреть приборку и центральный экран (PSD и HUD в машине нет).

## Ожидаемые проверки (по RESEARCH.md п. 2)

| Дисплей | Разрешение | Ожидание |
|---|---|---|
| 0 | 1440×1920 | Presentation на IVI поверх приложения |
| 1 | 800×480 | «TEST_HA1» — ничего (проверено) |
| 2 | 2880×1080 | PSD — экрана в машине нет, видно не будет |
| 3 | 1920×532 | ничего (проверено) |
| 4 | 1920×532 | «DIM» по DrakeStone — ничего (проверено) |

## Результат

- Локально: сборка успешна, APK без разрешений, Presentation-транзакции
  логируются (shown / ошибка / НЕ показан).
- На ГУ (2026-10-03, ручной эквивалент без APK): `am start --display N -f 0x18000000
  -n com.android.settings/.Settings` на Android-дисплеях 4, 3, 1 (3 и 4 — дважды).
  Window Manager принимает запуск, SurfaceFlinger рисует слой во весь экран, но
  **ни на одном физическом экране картинки нет**; приборка не меняется. HUD в
  машине отсутствует.
- Объяснение: на QNX Android-картинку забирают только `vmwindow_csd` (dpy-3) и
  `vmwindow_psd` (dpy-5); приборка — QNX dpy-1 (`cluster-hmi`) + dpy-2
  (`cluster-bg-hmi`), туда Android-слои не выводятся (RESEARCH.md п. 2).
- Итог фазы: **вывод на приборку через Android display 1/3/4 невозможен.**
  Presentation-кнопки оставлены как контроль (ожидается `shown=true` без
  видимой картинки); прогон самого APK — по желанию, чтобы зафиксировать лог.

## Ограничения

- uniqueId достаётся рефлексией `Display.getUniqueId()` (скрытый API): если
  hidden-API блокирует, в карточке будет `?` — сведения всё равно есть в
  `dumpsys display`.
- Флаги показаны только публичные (presentation/secure/protected-buffers/
  private/round); полные флаги — в dumpsys.
- Приложение не проверяет, куда физически идёт framebuffer — только факт
  показа Presentation и реакцию Window Manager.

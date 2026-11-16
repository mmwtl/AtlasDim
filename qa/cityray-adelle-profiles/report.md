# Cityray Adelle and 1 px positioning in vehicle profiles

Date: 2026-11-16. Branch: `vehicle-profiles`, based on `b8b7da9`.
APK: `0.18.0-vehicle-profiles`, versionCode 58, debug.
Environment: macOS, JDK 17, Android SDK 35. `adb devices`: no attached devices.
No QNX connection or vehicle operation was performed.

## Changes and source

Adapted PR #3 (`0b6fc4c`) to the existing `Vehicle` / `SceneConfig.vehicle`
architecture: exact Cityray KZB path, Adelle offset/length/SHA-256, distinct
font cache, two Cityray font options and renderer fallback for older IDs.
Atlas font choices and resources retain their existing behavior.
Added four 1 px position buttons for clock, fuel and temperature, with bounds
clamping. Existing drag/resize snap, background controls and presets are unchanged.
Retained font-byte SHA-256 validation from the profiles branch without adding
an enclosing-KZB size gate. See `docs/cityray-kzb-fonts.md`.

## Checks

- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed, 0 errors and 16 warnings; the project already disables
  `ExpiredTargetSdkVersion` for its fixed targetSdk 30.
- `git diff --check`: passed.
- Static review: font fetching and rendering use the scene's saved vehicle;
  editor uses the selected profile. Adelle uses `dd bs=4` since offset `0x22B9C`
  is not divisible by 8. New filename prevents old GeelyBCTF cache reuse.
- Static review: buttons update X/Y with `syncing` set and then refresh the
  preview once; the normal valid-draft save path preserves integer coordinates.
- `:app:assembleDebugAndroidTest`: failed with six existing compilation errors
  in `SceneTestRunner.java`: five three-argument `Fuel.of` calls (now four
  arguments), and the removed two-argument `ScenePreview` constructor.
  The runner and these APIs were not modified by this integration; the same
  mismatched calls are present in the base of PR #2. Instrumentation was not run.

## Remaining checks

On a parked Cityray G426_J1, verify Adelle download/cache and appearance;
clock/fuel/temperature arrow presses at interior and boundary coordinates;
save/reopen and apply; profile switching and settings import with older font IDs.
Check Atlas retains its built-in and imported font choices. Build/lint and the
source PR author's vehicle report do not establish this integrated APK's behavior
on OneOS/QNX. No font binary or proprietary resource was added to the repository.

## Inline coordinate controls follow-up

Replaced the separate arrow cross with buttons beside X and Y, as selected by
the user. Width and height occupy a second row; background retains its original
four-field row. Arrow touch targets are 48 × 48 dp before app UI scaling.

Validation on Android 11 / API 30 ARM64 AVD `atlashu`, emulator-5554,
1440 × 1920, density 160, app UI scale 1.5, Cityray profile, debug APK:

- `:app:assembleDebug :app:lintDebug`: passed.
- Actual UI taps: right X 188 → 189; left X 189 → 188;
  up Y 164 → 163; down Y 163 → 164. Each tap preserved the other coordinate
  and width/height (464 × 212).
- After the right tap, force-stop and relaunch restored X=189 and Y=164.
- Fuel and temperature tabs each expose four coordinate arrows; background
  exposes none. Clock screenshot visually reviewed for layout and clipping.
- No connection to QNX or attempt to apply a scene. Font appearance on the
  real cluster remains unverified by this emulator check.

### Vector arrow polish

Replaced font glyphs with a 24 dp vector arrow with rounded strokes, rotated
for each direction inside centered 48 dp ImageButtons. Retained content
descriptions and existing click handlers for all non-background panels.
`:app:assembleDebug :app:lintDebug` passed. On the same emulator, measured
bright-icon bounds in a fresh screenshot against UIAutomator button bounds:
all four icon centers exactly matched their button centers in both axes.

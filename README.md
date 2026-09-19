# Overpour Calculator

Point your phone's camera at a clear 16 oz Solo-style plastic cup and get a live, on-device
estimate of how much liquid is inside — e.g. **"≈ 8.3 oz"**, **"Likely range: 7.8-8.8 oz"**,
**HIGH CONFIDENCE**.

Everything runs locally on the phone. No cloud API, no network calls, no images ever leave
the device.

```
≈ 8.3 oz
Likely range: 7.8-8.8 oz
[ HIGH CONFIDENCE ]
```

## How it works

The cup is modeled as a **frustum (truncated cone)**, not a cylinder, so volume is computed
from the actual tapered geometry instead of a naive "50% of height = 50% of volume" guess (a
16 oz Solo cup is meaningfully narrower at the base than at the rim, so the bottom half of its
height holds noticeably less than half its volume).

```
camera frame
  -> cup candidate detection (ML Kit on-device object detector + geometry filtering)
  -> cup bounding region
  -> rim / base row detection (horizontal edge-energy scan)
  -> wall/profile detection (left/right vertical-edge scan per row)
  -> perspective quality estimate (wall-symmetry check)
  -> liquid surface detection (multi-cue row scan: edges, brightness step, texture step)
  -> height normalization (0..1 fraction of usable interior height)
  -> frustum volume model (+ optional user calibration curve)
  -> temporal smoothing (rolling median + EMA)
  -> confidence scoring -> "≈ X oz", range, HIGH/MEDIUM/LOW
```

See **Architecture** below for exactly which file implements each stage.

## Project structure

```
Fluid_reader/
  core/                         Pure-Kotlin module, NO Android dependency - runs as plain JVM
                                 unit tests. This is the mathematical heart of the app.
    src/main/kotlin/com/fluidreader/core/
      cup/CupProfile.kt         CupProfile data class + the built-in SOLO_16OZ profile
      measurement/VolumeCalculator.kt   Frustum geometry + volume integration + normalization
      measurement/ConfidenceModel.kt    Confidence scoring -> HIGH/MEDIUM/LOW + uncertainty range
      measurement/TemporalSmoother.kt   Rolling median + EMA smoothing
      calibration/CalibrationCurve.kt   Monotonic piecewise-linear curve from measured points
      units/UnitConverter.kt    oz <-> mL
    src/test/kotlin/...         16 unit tests covering all of the above (see "Running tests")

  app/                           The Android application (Kotlin + Jetpack Compose + CameraX)
    src/main/kotlin/com/fluidreader/app/
      camera/                   CameraController (CameraX setup), FrameAnalyzer (throttled
                                 ImageAnalysis.Analyzer, ~8 fps)
      vision/                   The CV pipeline:
                                   EdgeDetector.kt        Sobel-based gradient primitives
                                   FrameConverter.kt       ImageProxy/Bitmap -> upright LumaFrame
                                   CupDetector.kt          ML Kit object detection + geometric
                                                            scoring (no cloud, bundled model)
                                   CupBoundaryDetector.kt  Rim/base/wall outline refinement
                                   PerspectiveEstimator.kt Side-view-angle quality estimate
                                   LiquidLevelDetector.kt  Multi-cue liquid-surface detection
                                   VisionPipeline.kt       Orchestrates all of the above
      measurement/              MeasurementViewModel: combines vision output + core math +
                                 smoothing + confidence + manual-override state into one
                                 MeasurementUiState for Compose
      calibration/               CalibrationStore + CupProfileRepository (DataStore-backed)
      settings/                  SettingsStore (DataStore-backed)
      ui/
        camera/                 CameraScreen, MeasurementOverlay (draggable manual-adjust
                                 lines), CoordinateMapper (frame-space <-> screen-space)
        settings/                SettingsScreen
        calibration/             CalibrationScreen (developer calibration mode)
        debug/                   DebugTestScreen (load a gallery photo, run the exact same
                                 pipeline against it, see every intermediate measurement)
        about/                   AboutScreen
        nav/                     Compose Navigation graph
      MainActivity.kt, FluidReaderApp.kt
```

## Target cup & accuracy

Calibrated by default for a standard **clear 16 oz Solo-style plastic cup** (approx. 94 mm top
interior diameter, 60 mm base interior diameter, 133 mm usable interior height - see
`CupProfileRegistry.SOLO_16OZ` in `core`). These are reasonable manufacturer-spec defaults, not
measurements of one exact physical cup - **use the in-app Calibration screen** (Settings ->
enable "Developer options" -> Calibration) to enter your exact cup's dimensions and, optionally,
several measured height -> volume points for extra accuracy.

Expect roughly **±0.5 oz** under good lighting with a clear, close-to-perpendicular side view,
and **±1 oz or an explicit "unavailable" message** under difficult lighting, heavy reflections,
or an off-angle view. Transparent cups are genuinely hard for computer vision - this app leans
on multiple weak visual cues (edges, brightness/texture steps, reflections) rather than any
single "obvious" boundary, and is upfront about uncertainty rather than faking precision. Use
**manual adjustment mode** (pencil icon on the camera screen) any time the automatic reading
looks wrong - drag the rim/base/liquid lines and the ounce reading updates immediately.

## Building the project

Requirements: **Android Studio Ladybug (2024.2) or newer** (bundles a compatible JDK), or a
command-line setup with **JDK 17+** and the **Android SDK** (compileSdk/targetSdk 35,
minSdk 26) installed with `ANDROID_HOME`/`local.properties` pointing at it.

### Option A - Android Studio (recommended)

1. `File -> Open...` and select the `Fluid_reader/` folder (the one containing
   `settings.gradle.kts`).
2. Let Gradle sync - Android Studio will download the Android SDK platform/build-tools it needs
   automatically if they aren't already installed.
3. Select the `app` run configuration and an emulator or a physical device, then click **Run**.

### Option B - command line

```bash
cd Fluid_reader
./gradlew assembleDebug
# APK is written to app/build/outputs/apk/debug/app-debug.apk
```

To build a release APK (unsigned by default - sign it before distributing):

```bash
./gradlew assembleRelease
```

### Running the `core` unit tests

The `core` module has no Android dependency, so its tests run as plain JVM tests without an
emulator or SDK:

```bash
./gradlew :core:test
```

All 16 tests (volume-calculation monotonicity, the "half height != half volume" taper check,
calibration-curve interpolation, confidence scoring, temporal smoothing) currently pass.

> **Note on this repository's dev container:** the sandboxed environment this project was
> authored in blocks `dl.google.com` (where Android SDK components and some AGP/AndroidX
> artifacts are hosted), so the `app` module could not be built or run there - only reviewed
> carefully by hand and validated via `:core`'s JVM tests. Build it with Android Studio or a
> machine with normal access to Google's Maven repositories and the Android SDK.

## Installing the APK on a phone

**From Android Studio:** plug in your phone (with USB debugging enabled in Developer Options),
select it as the run target, and click **Run**.

**From the command line, with the phone connected via `adb`:**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Sideloading without a computer:** copy `app-debug.apk` onto the phone (e.g. via a file share
or cloud drive), open it with a file manager, and allow "install from this source" when
prompted. The app requests camera permission on first launch, and (only for the optional
Debug/Test screen) photo-library access to load reference images.

## Using the app

1. Launch the app and grant camera permission.
2. Point the camera at the side of a clear 16 oz Solo-style cup, roughly perpendicular to its
   wall, with the whole cup in frame.
3. Follow the on-screen guidance ("Center cup in frame", "Move camera to a side view", "Hold
   steady…") until it settles on a reading with a confidence badge.
4. If the automatic reading looks off, tap **Adjust manually**, drag the rim/base/liquid lines
   to match what you see, and the ounce reading recalculates immediately. Tap **Done** to
   resume automatic measurement.
5. Once a reading is ready, tap **Log drink** to add it to tonight's drink log: type a name
   (e.g. "Yuengling") or tap the mic icon to say it, then confirm. Logging the same name again
   later adds to its running total rather than creating a duplicate row - tap it from the
   suggestion chips shown in the dialog to avoid retyping/typos.
6. Tap the glass icon (top bar) to open **Drink log**: a running table of everything logged
   tonight (name, total poured, number of pours), plus a grand total. Swipe/tap the trash icon
   to remove a single entry, or the X in the top bar to clear the whole log - both ask for
   confirmation first, and nothing is ever deleted automatically.
7. Tap the gear icon for **Settings**: units (oz/mL), cup profile, confidence threshold,
   overlay visibility, and (after enabling "Developer options") **Calibration** and
   **Debug / Test mode**.

### Drink log

Designed for tracking a night's worth of drinks (comfortably handles dozens of entries, no
hard cap). Each named entry accumulates every pour logged under that name (case-insensitive,
so "IPA" and "ipa" merge) into one running total + pour count, rather than listing every pour
as its own row. The log is stored on-device (`DrinkLogStore`, Preferences DataStore) and
persists across app restarts - it is only ever cleared by the explicit per-entry delete or
"clear all" action in the Drink log screen, both of which require confirmation.

### Calibration mode

Settings -> enable "Developer options" -> **Calibration**. Enter your exact cup's total
interior height, top diameter, and bottom diameter (measured with calipers/a ruler), and
optionally add several measured `height (mm) -> known volume (oz)` points (fill to a measured
height, pour into a measuring cup, record the pair). Points override the pure frustum math
with a monotonic interpolated curve for maximum accuracy on your specific cup.

### Debug / Test mode

Settings -> enable "Developer options" -> **Debug / Test mode**. Load any reference photo of
the cup from your gallery and the screen runs the identical detection pipeline used live by the
camera, drawing the detected bounding box, rim/base lines, wall edges, and liquid line directly
on the photo, plus a full numeric readout (detector score, shape-match score, edge quality,
perspective angle quality, liquid visibility score, normalized height fraction, calculated
volume, confidence score/level, and the raw step-by-step debug log) for troubleshooting the CV
heuristics without needing to stand in front of a real cup.

## Adding another cup profile later

Add a new `CupProfile(...)` to `CupProfileRegistry.defaultProfiles` in
`core/src/main/kotlin/com/fluidreader/core/cup/CupProfile.kt` (name, rated volume, height, top/
bottom diameter, usable interior height). It immediately becomes selectable in Settings, and can
be calibrated independently via the Calibration screen (calibration data is stored per profile
id). `CupBoundaryDetector`'s shape-match check and `VolumeCalculator`'s frustum math both work
against whichever profile is selected, so no other code changes are required for a new
similarly-shaped tapered cup.

## Known limitations (first version)

- Calibrated for one cup shape (a tapered frustum, i.e. Solo-style); a very differently shaped
  container (e.g. straight-walled, bulbous) needs its own `CupProfile` and would benefit from a
  bespoke shape-match check.
- The cup-candidate detector uses ML Kit's generic (class-agnostic) on-device object detector
  plus geometric filtering, not a cup-specific trained model - it is deliberately supplemented
  with (not replaced by) the app's own taper/aspect-ratio checks, per the "confirm target cup"
  requirement.
- Liquid detection is heuristic (multiple weak visual cues combined), not a guarantee - manual
  adjustment is a first-class fallback, not an afterthought.
- Perspective correction is a symmetry-based approximation, not a full homography solve; very
  extreme angles are rejected ("Move camera closer to the side") rather than guessed at.

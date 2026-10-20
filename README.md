# Netra AI — "See the world through sound"

Netra AI is an Android application designed for blind and low-vision users. It keeps the smartphone camera active while walking, detects surrounding obstacles with fine-grained precision, and speaks prioritized, localized audio alerts in English and Hindi.

> **Important Limitation & Safety Notice:**
> Netra assists with navigation and situational awareness, but **it does not replace a white cane or guide dog**.
> **Caution:** Netra cannot detect stairs, potholes, or open drains yet. Always use traditional mobility aids.

---

## Architecture & Features

### PART A — High-Accuracy Detection Pipeline
1. **Sensor Rotation & Upright Mapping**:
   - CameraX rotation angle (`imageProxy.imageInfo.rotationDegrees`) is passed directly to MediaPipe via `ImageProcessingOptions.setRotationDegrees()`.
   - Bounding boxes and horizontal frame thirds (`LEFT`, `AHEAD`, `RIGHT`) are strictly computed in the upright device orientation.
2. **Upgraded Neural Network Model (EfficientDet-Lite2)**:
   - Upgraded from Lite0 to Google's official **EfficientDet-Lite2** model (`efficientdet_lite2.tflite`, 7.51 MB INT8 quantized, bundled in `app/src/main/assets/`).
   - Hardware Acceleration: Configured to attempt `Delegate.GPU` acceleration first, with automated seamless fallback to `Delegate.CPU`.
3. **Safety Label Whitelist & Explicit Confidence Thresholds**:
   - Only 16 walking-safety critical classes are accepted: `person`, `bicycle`, `car`, `motorcycle`, `bus`, `truck`, `dog`, `cow`, `horse`, `chair`, `bench`, `couch`, `dining table`, `potted plant`, `traffic light`, `stop sign`. All other objects are filtered out.
   - Thresholds: $\ge 0.55$ for vehicles (`car`, `bus`, `truck`, `motorcycle`, `bicycle`) and $\ge 0.60$ for all other obstacles (`person`, `chair`, etc.).
   - Detection sensitivity modes: `LOW` (+0.10 threshold), `NORMAL` (default), and `HIGH` (-0.10 threshold).
4. **Temporal Confirmation Filter (`TemporalTracker`)**:
   - Obstacles are only announced after being detected in at least **3 of the last 5 analyzed frames** with matching label and similar spatial coordinates, eliminating transient flicker and single-frame hallucinations.
5. **Distance Estimation with Hysteresis (`DistanceEstimator`)**:
   - Area-ratio thresholds with buffer bands prevent flip-flopping between `NEAR` and `VERY_CLOSE` on adjacent frames:
     - `FAR` $\to$ `NEAR`: ratio $\ge 0.06$ | `NEAR` $\to$ `FAR`: ratio $< 0.04$
     - `NEAR` $\to$ `VERY_CLOSE`: ratio $\ge 0.22$ | `VERY_CLOSE` $\to$ `NEAR`: ratio $< 0.18$
6. **Borderline Confidence "Maybe" Phrasing**:
   - Detections near the minimum threshold are qualified with uncertainty:
     - English: *"Maybe car ahead, very close."*
     - Hindi: *"शायद कार सामने, बहुत पास।"*
7. **Debug Tuning Overlay (`DebugOverlay`)**:
   - Can be toggled in Settings or by long-pressing the top status pill.
   - Visualizes live bounding boxes with label, confidence percentage, real-time FPS, and inference latency in milliseconds.
   - Logs accepted detections to Logcat via `Log.d("NetraDetection", ...)`.

---

### PART B — Smart Flashlight (Feature 7)
1. **Luma Estimation**:
   - Efficiently samples luminance directly from the Y-plane (`imageProxy.planes[0]`) with a 16-pixel stride, minimizing CPU overhead.
2. **Auto Hysteresis & 5-Second Hold**:
   - In `AUTO` mode, turns the torch ON when ambient luma drops below `45` and OFF when luma rises above `75`.
   - Enforces a minimum 5-second hold duration to prevent strobing under transient shadows or passing lights.
   - Spoken confirmations: *"Low light, flashlight on"* / *"Flashlight off"* (or Hindi equivalents).
3. **Hardware Fallback & Lifecycle Safety**:
   - Checks `camera.cameraInfo.hasFlashUnit()`; speaks *"Flashlight unavailable on this device"* once on unsupported hardware without crashing.
   - Automatically extinguishes torch when walking mode is stopped or when the app is placed in the background (`onStop()`).

---

### PART C — Professional Dark UI Redesign
1. **Visual Hierarchy & Accessibility (WCAG AA)**:
   - Fullscreen camera preview with a near-black design (`#0C0E12`), Electric Cyan primary accent (`#00E5FF`), Electric Amber status highlights (`#FFD600`), and Danger Red (`#FF1744`).
   - 16-24dp rounded corners, minimum 8dp spacing grid, and bold typography scaling.
2. **Top Status Pill & Settings**:
   - Displays real-time operational status: `WALKING` / `READY`, active language (`EN` / `HI`), and flashlight mode (`AUTO` / `ON` / `OFF`). Long press toggles the debug overlay.
   - Header button opens the accessible modal Settings bottom sheet.
3. **Dismissible Disclaimer**:
   - The limitation disclaimer card is dismissible via a dedicated "Close" icon and remembers its state in DataStore.
4. **Bottom Control Dock**:
   - Primary **Start/Stop Walking Mode** action button: **88dp height**, high-contrast Green (Ready) / Red (Active).
   - Secondary action row with **72dp height** tactile buttons:
     - **Flashlight**: Cycles through `AUTO` $\to$ `ON` $\to$ `OFF`.
     - **Language**: One-tap toggle between English and Hindi.
     - **SOS**: Emergency alert button placeholder with distinct danger haptic pulse.
     - **Settings**: Opens modal settings sheet (language, speech rate slider, flashlight mode, sensitivity, debug overlay).
5. **Haptic Feedback**:
   - Button presses produce tactile haptic clicks.
   - Priority 1 vehicle alerts trigger a multi-pulse danger vibration effect.

---

## Dependencies Added

1. **`com.google.mediapipe:tasks-vision:0.10.20`**
   - Official Google MediaPipe library providing on-device machine learning vision pipelines. Version `0.10.20` provides 16 KB memory page compatibility and GPU delegate fallback support.
2. **`androidx.datastore:datastore-preferences:1.1.3`**
   - Jetpack DataStore library for asynchronous, coroutine-backed preference persistence.
3. **`androidx.compose.material:material-icons-extended`**
   - Provides extended Material icons (`FlashAuto`, `FlashOn`, `FlashOff`, `Language`, `Warning`, `Settings`, `Close`) used in the 72dp control dock. Managed by the Compose BOM (`2026.02.01`).

---

## Running Automated Unit Tests

All detection geometry, temporal trackers, filtering thresholds, distance hysteresis, cooldown trackers, alert engine rules, and flashlight controllers are covered by pure unit tests.

Execute all 48 unit tests via Gradle:
```bash
./gradlew testDebugUnitTest
```

### Verified Test Suites (48 Tests, 0 Failures):
- `TemporalTrackerTest` (5 tests): Temporal confirmation (3 of 5 frames), transient glitch rejection, spatial matching.
- `DetectionFilterTest` (4 tests): Whitelist filtering, vehicle (0.55) vs other (0.60) thresholds, borderline confidence detection, sensitivity adjustments.
- `DistanceHysteresisTest` (6 tests): Anti-jitter buffer bands for FAR, NEAR, and VERY_CLOSE transitions.
- `FlashlightAutoControllerTest` (5 tests): Luma thresholds, 5-second anti-strobe hold timer, hysteresis state retention.
- `AlertEngineTest` (7 tests): Priority 1 interruption, Priority 2 queuing, Priority 3 idle-only speech, "Maybe" borderline phrasing in EN/HI, multi-hazard sorting.
- `AlertCooldownTrackerTest` (6 tests): Cooldown suppression, closer-distance immediate repeats, tracker reset.
- `DirectionTest` (5 tests): Horizontal thirds of upright frame, boundary coordinates.
- `DistanceTest` (6 tests): Area-ratio dimension calculations, distance ordering.
- `AlertPriorityTest` (3 tests): Label-to-priority mappings.
- `ExampleUnitTest` (1 test): Sanity baseline.

---

## How to Test on a Physical Phone

1. **Build & Install**:
   ```bash
   ./gradlew installDebug
   ```
   *(or run from Android Studio onto your connected device).*

2. **Verify Upright Frame Detection & Accuracy**:
   - Open Netra AI and tap **"Start Walking Mode"** (large 88dp button).
   - Point your phone at a person standing in the center:
     - Listen for *"Person ahead, near."*
     - Move the camera so the person is in the left third: listen for *"Person left, near."*
     - Verify orientation: hold the phone in portrait orientation. The direction will strictly align with the real-world position without 90-degree offset.
   - Long-press the top status pill to turn on the **Debug Overlay**:
     - Observe the green/yellow/red bounding boxes mapped directly over detected objects.
     - Note the HUD stats in the top corner displaying real-time camera FPS and inference latency (~40-70ms on modern devices).

3. **Verify Temporal Confirmation & False Positive Suppression**:
   - Rapidly pan past a complex background or briefly obscure the lens:
     - Notice that momentary 1-frame glitches are silenced.
     - Hold the camera steady on an object for 1 second: notice that once confirmed (3 of 5 frames), the alert speaks cleanly.

4. **Verify Borderline Confidence Phrasing**:
   - Point the camera at a partially visible or distant car:
     - When confidence is borderline (0.55 - 0.67), Netra announces: *"Maybe car ahead, far."*
     - Switch to Hindi: notice *"शायद कार सामने, दूर।"*

5. **Verify Distance Hysteresis**:
   - Approach an obstacle slowly:
     - Listen for transition from *"near"* to *"very close"*.
     - Take a half-step back and forth at the boundary: notice the speech does not rapidly oscillate between *"near"* and *"very close"*.

6. **Verify Smart Flashlight**:
   - Tap the **Flashlight** button in the dock to switch between `AUTO`, `ON`, and `OFF`.
   - In `AUTO` mode, walk into a dimly lit room or cover the camera lens:
     - After ambient light drops below threshold, the torch automatically illuminates and announces: *"Low light, flashlight on"*.
     - Uncover the lens or enter a bright room: after the 5-second hold period, the torch extinguishes and announces: *"Flashlight off"*.
   - Tap **"Stop Walking Mode"** or press the phone's Home button: verify the torch turns off immediately.

7. **Verify Settings Sheet & Customization**:
   - Tap the **Settings** icon at the top right or bottom dock.
   - Adjust speech speed (0.75x to 1.5x) and verify voice pace adjusts.
   - Change sensitivity (`Low`, `Normal`, `High`) and verify detection responsiveness.
   - Toggle language and verify immediate UI and voice localization.

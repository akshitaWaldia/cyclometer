# Implementation Plan: CycleComp

## Overview

Incremental implementation of the CycleComp cycling computer app for Android (Kotlin + Jetpack Compose). Tasks are ordered so that a buildable APK is available every 2–3 tasks for on-device testing on a Samsung S24. Property-based tests (Kotest) are batched into dedicated testing tasks after APK checkpoints, so implementation flows uninterrupted and tests validate working code.

## Tasks

- [x] 1. Project setup and Gradle configuration
  - Create Android Studio project with package `com.cyclecomp.app`
  - Configure `build.gradle.kts` (app) with all dependencies: Compose BOM, Material3, Hilt, Room, DataStore, Google Maps, FIT SDK, Health Connect, AppAuth, OkHttp, Kotest, MockK
  - Configure `build.gradle.kts` (project) with Hilt and KSP/KAPT plugins
  - Add `AndroidManifest.xml` permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `WAKE_LOCK`, `INTERNET`
  - Declare foreground service for ride recording in manifest
  - Add Google Maps API key reference via `BuildConfig` / `local.properties`
  - Add Strava OAuth client ID placeholder in `local.properties`
  - Create `CycleCompApplication.kt` with `@HiltAndroidApp`
  - Create `MainActivity.kt` with `@AndroidEntryPoint` and empty Compose `setContent`
  - _Requirements: 9.1, 1.1_

- [x] 2. Core data models, DI modules, and preference storage
  - [x] 2.1 Create domain models
    - Create `com.cyclecomp.app.domain.model` package
    - Implement `RideData`, `TrackPoint`, `LapData`, `RiderProfile`, `SensorSnapshot`, `GpsReading`, `BleDevice`, `PairedDevice` data classes
    - Implement enums: `ConnectionState`, `GpsSource`, `HeartRateZone`, `SensorType`, `RideState`, `SyncState`
    - _Requirements: 2.3, 5.1, 7.1, 9.2_

  - [x] 2.2 Create Room database, DAOs, and entities
    - Implement `RideEntity`, `TrackPointEntity`, `LapEntity` with Room annotations
    - Create `RideDao` with insert, query-by-id, query-all, delete operations
    - Create `CycleCompDatabase` with `@Database` annotation
    - _Requirements: 13.1_

  - [x] 2.3 Create DataStore preferences wrapper
    - Implement `PreferenceKeys` object and `UserPreferencesRepository` for rider profile, theme, paired devices, and Strava tokens
    - _Requirements: 18.1, 18.2, 16.3_

  - [x] 2.4 Create Hilt DI modules
    - Create `AppModule` providing Room database, DAOs, DataStore, CoroutineDispatchers
    - Create `RepositoryModule` providing repository bindings
    - _Requirements: 18.2_

- [x] 3. Dashboard UI shell with mock data
  - [x] 3.1 Implement Theme Engine
    - Create `ThemeEngine` interface and `ThemeEngineImpl` backed by DataStore
    - Create `CycleCompTheme` composable with light/dark color schemes and font scaling (1.5x for large font)
    - Wire night mode and large font toggles into Compose `MaterialTheme`
    - _Requirements: 16.1, 16.2, 16.4_

  - [x] 3.2 Build Dashboard screen with tile layout
    - Create `DashboardViewModel` with hardcoded mock `SensorSnapshot` data
    - Create `DashboardScreen` composable with tile-based layout: Power + Speed (top row), Heart Rate (double-sized) + Cadence (second row), Map placeholder (middle), Distance + Time (bottom row), Gradient/TSS/Elevation/Calories in remaining space
    - Implement styled tile backgrounds for visual distinction
    - Add ride control buttons: Start, Pause, Stop, Lap
    - Add ride state indicator (Recording / Paused / Stopped)
    - Keep screen awake with `FLAG_KEEP_SCREEN_ON` while ride is active
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 17.4_

  - [x] 3.3 Set up Navigation and Settings stub
    - Add `navigation-compose` with two destinations: Dashboard and Settings
    - Create stub `SettingsScreen` composable with placeholder fields for weight, bike weight, FTP
    - Wire navigation from Dashboard to Settings via a gear icon
    - _Requirements: 18.1_

- [x] 4. APK Checkpoint 1 — Dashboard shell with mock data
  - Ensure the project builds a debug APK (`./gradlew assembleDebug`)
  - Verify the APK installs on Samsung S24 and shows the dashboard with mock metrics, tile layout, ride controls, and navigation to settings stub
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Property-based tests batch 1 — Models, theme, and formatting
  - [x] 5.1 Write property test for heart rate zone classification
    - **Property 4: Heart Rate Zone Classification**
    - For any HR 0–220 bpm, zone function returns exactly one zone whose range contains the input
    - **Validates: Requirements 2.3**

  - [x] 5.2 Write property test for preference persistence round-trip
    - **Property 21: Preference Persistence Round-Trip**
    - Save rider profile + theme prefs to DataStore, load back, verify identical values
    - **Validates: Requirements 16.3, 18.2**

  - [x] 5.3 Write property test for night mode round-trip
    - **Property 19: Night Mode Round-Trip**
    - Enable night mode then disable → original light scheme restored; color scheme is pure function of flag
    - **Validates: Requirements 16.1, 16.4**

  - [x] 5.4 Write property test for font scaling
    - **Property 20: Font Scaling**
    - Large font mode produces exactly 1.5x default size; disabling restores original
    - **Validates: Requirements 16.2**

  - [x] 5.5 Write property test for duration and distance formatting
    - **Property 12: Duration and Distance Formatting Round-Trip**
    - Format duration to HH:MM:SS and parse back → same duration (truncated to seconds); format distance to 2dp and parse back → within 0.005 km
    - **Validates: Requirements 7.2, 7.4**

- [x] 6. BLE Manager and sensor connection
  - [x] 6.1 Implement BLE Manager
    - Create `BleManager` interface and `BleManagerImpl`
    - Implement BLE scanning with service UUID filters (HR 0x180D, CSC 0x1816) and 10-second timeout
    - Implement connect/disconnect with GATT callbacks
    - Implement characteristic notification subscription returning `Flow<ByteArray>`
    - Implement reconnection logic: on disconnect during ride, retry every 5s for 60s, then transition to DISCONNECTED
    - Implement auto-connect on launch from stored paired devices in DataStore
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [x] 6.2 Implement BLE characteristic parsers
    - Parse HR Measurement (0x2A37): handle 8-bit and 16-bit formats, extract bpm
    - Parse CSC Measurement (0x2A5B): extract cumulative crank revolutions and last event time, derive RPM
    - _Requirements: 2.1, 3.1_

  - [x] 6.3 Create sensor scan/connect UI
    - Create `SensorScanScreen` composable showing discovered devices list
    - Allow user to tap a device to connect; show connection state per device
    - Show "Reconnecting..." state with spinner on tiles when sensor is reconnecting
    - Show persistent banner for disconnected sensors
    - _Requirements: 1.1, 1.2, 1.5_

  - [x] 6.4 Create Hilt BLE module
    - Provide `BleManager` singleton via Hilt `@Singleton`
    - _Requirements: 1.1_

- [x] 7. GPS Provider and speed/distance calculation
  - [x] 7.1 Implement GPS Provider
    - Create `GpsProvider` interface and `GpsProviderImpl` using `FusedLocationProviderClient`
    - Emit `StateFlow<GpsReading?>` with lat, lon, altitude, speed, accuracy, source
    - Implement Haversine distance accumulation between consecutive GPS points
    - Implement cumulative elevation gain (sum of positive altitude deltas only)
    - Implement gradient calculation: `(altitudeChange / horizontalDistance) * 100` over 50m rolling window
    - Implement GPS fallback: if phone GPS unavailable >5s and watch GPS available, switch to WATCH source
    - _Requirements: 4.1, 4.4, 4.5, 6.1, 6.3, 7.1_

  - [x] 7.2 Implement average speed over last kilometer
    - Track sliding window of track points; when cumulative distance > 1 km, compute avg speed = 1 km / time for that segment
    - _Requirements: 4.3_

- [x] 8. Sensor Hub integration and live dashboard wiring
  - [x] 8.1 Implement Sensor Hub
    - Create `SensorHub` interface and `SensorHubImpl`
    - Combine BLE HR flow, BLE CSC flow, and GPS flow into unified `StateFlow<SensorSnapshot>` using `combine()`
    - Implement heart rate zone classification from bpm
    - Implement staleness detection: if sensor has not emitted within timeout, report null instead of last value
    - _Requirements: 2.1, 2.3, 2.4, 3.1, 3.3_

  - [x] 8.2 Wire Sensor Hub to Dashboard ViewModel
    - Replace mock data in `DashboardViewModel` with live `SensorSnapshot` collection
    - Display real HR (double-sized, with zone), cadence, speed, distance, elevation, gradient on tiles
    - Show "no signal" / "No GPS" when sensor values are null
    - _Requirements: 2.2, 2.4, 3.2, 3.3, 4.2, 4.5, 6.2, 6.4, 7.2, 7.4_

- [x] 9. APK Checkpoint 2 — Live sensor data on dashboard
  - Ensure the project builds a debug APK
  - Verify the APK connects to BLE sensors (Watch HR + Magene cadence), shows live GPS speed/distance, and displays real-time metrics on the dashboard
  - Ensure all tests pass, ask the user if questions arise.

- [-] 10. Property-based tests batch 2 — BLE, GPS, and sensor logic
  - [ ] 10.1 Write property test for BLE reconnection schedule
    - **Property 1: BLE Reconnection Schedule**
    - On disconnect during ride, exactly 12 attempts over 60s at 5s intervals, then DISCONNECTED
    - **Validates: Requirements 1.4, 1.5**

  - [ ] 10.2 Write property test for BLE characteristic parsing
    - **Property 3: BLE Characteristic Parsing**
    - For any valid HR/CSC byte array, parsing produces non-negative integer matching Bluetooth GATT spec
    - **Validates: Requirements 2.1, 3.1**

  - [ ] 10.3 Write property test for auto-reconnect on launch
    - **Property 2: Auto-Reconnect on Launch**
    - For any set of previously paired devices in preferences, app launch triggers connection attempt to every stored device
    - **Validates: Requirements 1.6**

  - [ ] 10.4 Write property test for Haversine distance accumulation
    - **Property 10: Distance Accumulation via Haversine**
    - For any sequence of GPS coords, cumulative distance = sum of Haversine between consecutive points, monotonically non-decreasing
    - **Validates: Requirements 7.1**

  - [ ] 10.5 Write property test for cumulative elevation gain
    - **Property 8: Cumulative Elevation Gain**
    - For any altitude sequence, gain = sum of positive deltas only (descents ignored)
    - **Validates: Requirements 6.1**

  - [ ] 10.6 Write property test for gradient calculation
    - **Property 9: Gradient Calculation**
    - For two GPS points, gradient = (altitude_change / horizontal_distance) * 100, bounded -100% to +100%
    - **Validates: Requirements 6.3**

  - [ ] 10.7 Write property test for GPS fallback source selection
    - **Property 6: GPS Fallback Source Selection**
    - Phone available → PHONE; phone unavailable + watch available → WATCH; both unavailable → NONE
    - **Validates: Requirements 4.4, 4.5**

  - [ ] 10.8 Write property test for average speed over last kilometer
    - **Property 25: Average Speed Over Last Kilometer**
    - When distance > 1 km, avg speed of last km = 1 km / elapsed time of that segment
    - **Validates: Requirements 4.3**

  - [ ] 10.9 Write property test for no stale sensor data
    - **Property 5: No Stale Sensor Data**
    - If sensor has not emitted within staleness timeout, hub reports null rather than last value
    - **Validates: Requirements 2.4, 3.3**

- [x] 11. Power Estimator
  - [x] 11.1 Implement Power Estimator
    - Create `PowerEstimator` interface and `PowerEstimatorImpl`
    - Implement physics model: `P_total = P_gravity + P_rolling + P_aero + P_accel`
    - Use rider weight + bike weight from `RiderProfile` (defaults 75kg + 9kg)
    - Implement average power (running mean) and normalized power (30s rolling avg → 4th power → mean → 4th root)
    - Return 0 W when speed is 0
    - Wire into Sensor Hub pipeline and Dashboard ViewModel
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 12. Elevation/Gradient display and Calorie & TSS Calculator
  - [x] 12.1 Wire elevation gain and gradient to dashboard
    - Connect `GpsProvider.cumulativeElevationGainM` and `currentGradientPercent` to dashboard tiles
    - _Requirements: 6.2, 6.4_

  - [x] 12.2 Implement Calorie & TSS Calculator
    - Create `CalorieAndTssCalculator` interface and `CalorieAndTssCalculatorImpl`
    - Implement Keytel calorie formula using HR, weight, age
    - Implement TSS formula: `(duration_sec * NP * IF) / (FTP * 3600) * 100` where `IF = NP / FTP`
    - Default FTP = 200 W if not configured
    - Wire into Sensor Hub pipeline and display on dashboard
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 13. Ride Recorder, Auto-Pause, and Lap Manager
  - [x] 13.1 Implement Ride Recorder
    - Create `RideRecorder` interface and `RideRecorderImpl`
    - Implement state machine: IDLE → RECORDING (start), RECORDING → PAUSED (pause), PAUSED → RECORDING (resume), RECORDING/PAUSED → STOPPED (stop)
    - Track elapsed time using `SystemClock.elapsedRealtime()`, excluding paused periods
    - Collect `SensorSnapshot` stream and append `TrackPoint` entries to ride timeline
    - On stop, produce complete `RideData` with all accumulated metrics
    - _Requirements: 13.1, 17.1, 17.2, 17.3_

  - [x] 13.2 Implement Auto-Pause Controller
    - Create `AutoPauseController` interface and `AutoPauseControllerImpl`
    - When speed < 2 km/h for >3 seconds → auto-pause; when speed > 2 km/h → immediate resume
    - Wire auto-pause events into Ride Recorder pause/resume
    - Show "Paused" indicator on dashboard during auto-pause
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [x] 13.3 Implement Lap Manager
    - Create `LapManager` interface and `LapManagerImpl`
    - On lap mark: capture current lap time, distance, avg power; reset counters for new lap
    - Store completed laps in ride data
    - Wire lap button on dashboard to `LapManager.markLap()`
    - _Requirements: 11.1, 11.2, 11.3_

  - [x] 13.4 Wire ride controls to Dashboard
    - Connect Start/Pause/Stop/Lap buttons to Ride Recorder and Lap Manager
    - Display ride state indicator, elapsed time (HH:MM:SS), and "Paused" banner
    - On stop, prompt user to save/export
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 7.3, 7.4_

- [x] 14. APK Checkpoint 3 — Full ride recording
  - Ensure the project builds a debug APK
  - Verify the APK records a complete ride with start/pause/resume/stop, auto-pause, lap marks, power estimation, calories, TSS, and all metrics displayed live
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Property-based tests batch 3 — Power, ride recording, and calculations
  - [ ] 15.1 Write property test for power estimation formula
    - **Property 7: Power Estimation Formula**
    - For valid speed ≥0, gradient -45% to +45%, rider weight 30–200 kg, bike weight 3–30 kg: result = P_gravity + P_rolling + P_aero; result = 0 when speed = 0
    - **Validates: Requirements 5.1, 5.4**

  - [ ] 15.2 Write property test for calorie and TSS calculation
    - **Property 13: Calorie and TSS Calculation**
    - For valid HR 40–220, weight 30–200 kg, duration >0s, NP ≥0, FTP >0: calorie matches Keytel formula, TSS matches formula, both non-negative
    - **Validates: Requirements 8.1, 8.3, 8.5**

  - [ ] 15.3 Write property test for profile update propagation
    - **Property 23: Profile Update Propagation**
    - After weight change during ride, subsequent power calculations use updated values; same speed/gradient with different weight → different power
    - **Validates: Requirements 18.3**

  - [ ] 15.4 Write property test for ride state machine transitions
    - **Property 22: Ride State Machine Transitions**
    - Only valid transitions: IDLE→RECORDING, RECORDING→PAUSED, PAUSED→RECORDING, RECORDING→STOPPED, PAUSED→STOPPED
    - **Validates: Requirements 17.1, 17.2, 17.3**

  - [ ] 15.5 Write property test for auto-pause state machine
    - **Property 14: Auto-Pause State Machine**
    - Speed < 2 km/h for >3s → paused; speed > 2 km/h → immediate resume; never paused while speed > 2 km/h
    - **Validates: Requirements 12.1, 12.2**

  - [ ] 15.6 Write property test for elapsed time excluding paused periods
    - **Property 11: Elapsed Time Excludes Paused Periods**
    - Elapsed = total time minus sum of all paused intervals
    - **Validates: Requirements 7.3, 12.3**

  - [ ] 15.7 Write property test for lap mark captures and resets
    - **Property 15: Lap Mark Captures and Resets**
    - Completed lap contains accumulated time/distance/power since last mark; new lap counters reset to zero
    - **Validates: Requirements 11.1, 11.2**

- [x] 16. Google Maps integration with kill switch
  - [x] 16.1 Implement Map View composable
    - Add Google Maps Compose SDK `MapView` to the dashboard middle section
    - Display cyclist's current GPS location as a marker/dot on the map
    - Support loading a pre-planned route file and rendering it as a polyline overlay
    - Implement turn-by-turn navigation cues when following a route
    - _Requirements: 10.1, 10.2, 10.3_

  - [x] 16.2 Implement map kill switch
    - Add kill switch toggle to dashboard (battery icon or toggle button)
    - When active: disable map rendering, stop map GPS updates
    - When deactivated: resume map rendering and location tracking within 3 seconds
    - _Requirements: 10.4, 10.5_

- [x] 17. File export — FIT and GPX
  - [x] 17.1 Implement FIT Exporter
    - Create `FitExporter` interface and `FitExporterImpl` using Garmin FIT SDK
    - Write FileId, Activity, Session, Lap, and Record messages from `RideData`
    - Implement `deserialize` for round-trip verification
    - Handle storage errors: catch IOException, retain RideData in memory, notify user for retry
    - _Requirements: 13.2, 13.4, 13.6_

  - [x] 17.2 Implement GPX Exporter
    - Create `GpxExporter` interface and `GpxExporterImpl`
    - Generate GPX 1.1 XML with `<trk>`, `<trkseg>`, `<trkpt>` elements
    - Write HR, cadence, power into `<extensions>` using Garmin TrackPointExtension schema
    - Implement `deserialize` for round-trip verification
    - _Requirements: 13.3, 13.5, 13.6_

  - [x] 17.3 Wire export into ride stop flow
    - On ride stop, prompt user to save; serialize to both .FIT and .GPX
    - Save files to app-specific external storage
    - Persist `RideEntity` with file paths to Room database
    - Show error message and retry option on export failure
    - _Requirements: 13.1, 13.6_

- [x] 18. APK Checkpoint 4 — Map and file export
  - Ensure the project builds a debug APK
  - Verify the APK shows live map with location, map kill switch works, and completed rides export to .FIT and .GPX files on device storage
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Property-based tests batch 4 — Map, serialization, and export
  - [ ] 19.1 Write property test for map kill switch state
    - **Property 24: Map Kill Switch State**
    - Kill switch active → map rendering disabled, no map GPS updates processed; deactivated → map re-enabled
    - **Validates: Requirements 10.4**

  - [ ] 19.2 Write property test for FIT serialization round-trip
    - **Property 16: FIT Serialization Round-Trip**
    - For any valid `RideData`, serialize to .FIT then deserialize → equivalent data within floating-point tolerance
    - **Validates: Requirements 13.2, 13.4**

  - [ ] 19.3 Write property test for GPX serialization round-trip
    - **Property 17: GPX Serialization Round-Trip**
    - For any valid `RideData`, serialize to .GPX then deserialize → equivalent data within floating-point tolerance
    - **Validates: Requirements 13.3, 13.5**

- [-] 20. Strava sync
  - [x] 20.1 Implement Strava Sync Service
    - Create `StravaSyncService` interface and `StravaSyncServiceImpl`
    - Implement OAuth 2.0 PKCE flow using AppAuth library
    - Store access/refresh tokens in encrypted DataStore
    - Implement `.FIT` file upload to Strava API via OkHttp multipart POST
    - Implement auto token refresh on 401
    - Handle network errors: queue ride for retry, notify user
    - Handle duplicate upload (Strava rejects): mark as uploaded
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

  - [x] 20.2 Wire Strava upload into post-ride flow
    - After ride save, offer "Upload to Strava" button
    - Show sync state (Authenticating / Uploading / Success / Failed) on UI
    - _Requirements: 14.2, 14.3_

- [ ] 21. Health Connect integration
  - [x] 21.1 Implement Health Connect Service
    - Create `HealthConnectService` interface and `HealthConnectServiceImpl`
    - Check availability and permissions; prompt install if not present
    - Write `ExerciseSessionRecord` (BIKING), `HeartRateRecord`, `DistanceRecord`, `TotalCaloriesRecord`, `ExerciseRouteRecord` from `RideData`
    - Handle permission denial gracefully; handle write failure with retry option
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_

  - [x] 21.2 Wire Health Connect into post-ride flow
    - After ride save, automatically write to Health Connect if permissions granted
    - Show success/failure status; allow manual retry
    - _Requirements: 15.1, 15.5_

- [ ] 22. APK Checkpoint 5 — Strava and Health Connect
  - Ensure the project builds a debug APK
  - Verify the APK uploads rides to Strava via OAuth, writes ride data to Health Connect, and handles errors gracefully
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 23. Property-based tests batch 5 — Health Connect mapping
  - [ ] 23.1 Write property test for Health Connect record mapping completeness
    - **Property 18: Health Connect Record Mapping Completeness**
    - For any valid `RideData`, mapping produces ExerciseSessionRecord (BIKING) with correct times/distance, HeartRateRecord matching HR data, TotalCaloriesRecord matching calories, ExerciseRouteRecord with all GPS points
    - **Validates: Requirements 15.1, 15.2**

- [ ] 24. Settings screen and rider profile
  - [x] 24.1 Implement full Settings screen
    - Create `SettingsViewModel` backed by `UserPreferencesRepository`
    - Build Settings UI with input fields: rider weight (kg), bike weight (kg), FTP (W)
    - Show current values with defaults (75 kg, 9 kg, 200 W)
    - Persist on save; propagate updated values to PowerEstimator and CalorieAndTssCalculator immediately
    - Add night mode toggle and large font toggle to settings
    - Add paired sensor management (list paired devices, forget device)
    - Add Strava account connection/disconnection
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 16.1, 16.2_

- [x] 25. Polish, foreground service, and final APK
  - [x] 25.1 Implement foreground service for ride recording
    - Create `RideRecordingService` as a foreground service with persistent notification
    - Keep BLE connections and GPS alive during ride even when app is backgrounded
    - _Requirements: 9.5, 1.3_

  - [x] 25.2 UI polish and edge case handling
    - Ensure all error banners display correctly (BLE disconnect, GPS loss, export failure)
    - Verify "Paused" indicator visibility during auto-pause
    - Verify screen stays awake during active ride
    - Handle Bluetooth disabled prompt and missing permissions dialogs
    - _Requirements: 12.4, 9.5, 17.4_

  - [x] 25.3 Final integration verification
    - Verify complete ride flow: start → record with all sensors → auto-pause → lap → stop → export FIT/GPX → upload Strava → write Health Connect
    - Verify settings changes propagate to power/calorie calculations mid-ride
    - Verify night mode and large font apply correctly across all screens
    - _Requirements: all_

- [x] 26. Final APK Checkpoint — Complete app
  - Ensure the project builds a release-candidate debug APK
  - Verify all features work end-to-end on Samsung S24: BLE sensors, GPS, power, ride recording, map, export, Strava, Health Connect, night mode, settings
  - Ensure all property-based and unit tests pass, ask the user if questions arise.

## Notes

- Property-based tests are batched into dedicated tasks (5, 10, 15, 19, 23) after APK checkpoints so implementation flows uninterrupted
- APK checkpoints (tasks 4, 9, 14, 18, 22, 26) produce installable APKs for on-device testing on Samsung S24
- Each task references specific requirements for traceability
- Property tests validate the 25 correctness properties from the design document using Kotest (minimum 100 iterations each)
- All code uses Kotlin + Jetpack Compose with Hilt DI throughout

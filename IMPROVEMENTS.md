# CycleComp Improvement Recommendations

This document outlines recommended improvements for the CycleComp cycling computer application, organized by priority and category.

---

## Critical: Potential Bugs

### 1. Power Estimation Model Produces Unrealistic Values (1300W+)
**File:** `app/src/main/java/com/cyclecomp/app/domain/sensor/PowerEstimatorImpl.kt`

**Observed Issue:** Power readings of 1300W+ which are unrealistic for normal cycling (pros sprint at ~1500-2000W for seconds, sustained >500W is elite-level).

**Root Causes Identified:**

#### A. Unbounded Acceleration Component (Primary Culprit)
```kotlin
// Lines 79-84
val dtSec = (now - prevTime) / 1000.0
if (dtSec > 0.0) {
    val acceleration = (speedMps - prevSpeed) / dtSec
    pAccel = totalMass * acceleration * speedMps
}
```
**Problem:** If `dtSec` is very small (e.g., 50ms between GPS updates), tiny speed changes produce massive acceleration values.

**Example:** Speed changes from 7.0 to 7.3 m/s in 50ms:
- Acceleration = 0.3 / 0.05 = 6 m/s² (unrealistic - that's 0-60 km/h in 2.7 seconds!)
- pAccel = 84kg × 6 × 7.15 = **3,600W** just from acceleration!

**Fixes needed:**
1. Require minimum `dtSec` threshold (e.g., 0.5 seconds) before calculating acceleration
2. Apply low-pass filter to speed values before computing delta
3. Clamp acceleration to realistic bounds (±2 m/s² max for cycling)

#### B. No Speed Smoothing for GPS Noise
GPS can produce jittery speed readings. A momentary spike from 25 to 35 km/h due to GPS error causes huge acceleration power.

**Fix:** Apply exponential moving average or Kalman filter to speed before power calculation.

#### C. No Gradient Smoothing
```kotlin
// Line 63
val slopeRad = atan(gradientPercent / 100.0)
```
**Problem:** GPS-derived gradients can spike to 30-50% momentarily due to altitude noise.

**Example:** 30% gradient at 25 km/h = ~1700W just from gravity!

**Fix:** The 50m rolling window in `GpsProviderImpl` may not be enough. Consider:
- Larger smoothing window
- Capping gradient to realistic maximum (±25%)
- Requiring multiple consistent readings

#### D. No Maximum Power Cap
```kotlin
// Line 90
val totalPower = max(0.0, pGravity + pRolling + pAero + pAccel)
```
**Problem:** Only prevents negative power, no upper sanity check.

**Fix:** Add realistic maximum cap:
```kotlin
val totalPower = max(0.0, pGravity + pRolling + pAero + pAccel).coerceAtMost(2000.0)
```

#### E. Missing Input Validation
No validation that inputs are within reasonable ranges:
- Speed: 0-35 m/s (0-126 km/h)
- Gradient: -30% to +30%
- Headwind: -20 to +20 m/s

**Recommended Complete Fix:**
```kotlin
// 1. Smooth speed with EMA
private var smoothedSpeed = 0.0
private const val SPEED_ALPHA = 0.3  // smoothing factor

fun update(rawSpeedMps: Double, gradientPercent: Double, headwindMps: Double) {
    // Smooth speed
    smoothedSpeed = SPEED_ALPHA * rawSpeedMps + (1 - SPEED_ALPHA) * smoothedSpeed
    val speedMps = smoothedSpeed

    // Clamp gradient
    val clampedGradient = gradientPercent.coerceIn(-25.0, 25.0)

    // Only compute acceleration if dt is reasonable
    val dtSec = (now - prevTime) / 1000.0
    val pAccel = if (dtSec in 0.5..2.0) {
        val acceleration = ((speedMps - prevSpeed) / dtSec).coerceIn(-2.0, 2.0)
        totalMass * acceleration * speedMps
    } else 0.0

    // Cap total power
    val totalPower = max(0.0, pGravity + pRolling + pAero + pAccel).coerceAtMost(2000.0)
}
```

---

### 2. HeartRateZone Boundary Crash
**File:** `app/src/main/java/com/cyclecomp/app/domain/model/Enums.kt`

**Issue:** `HeartRateZone.fromBpm()` uses `entries.first { bpm in it.range }` which throws `NoSuchElementException` if BPM > 220 (outside all defined zones).

**Fix:** Use `firstOrNull()` with a fallback to ZONE5 for values above the maximum.

---

### 3. SensorHub Empty Device Address
**File:** `app/src/main/java/com/cyclecomp/app/domain/sensor/SensorHubImpl.kt` (lines 102-108)

**Issue:** Collects from `deviceAddress = ""` which won't connect to any actual BLE device.

**Fix:** Ensure device address is populated from paired device storage or user selection.

---

### 4. Normalized Power Window Data Loss
**File:** `app/src/main/java/com/cyclecomp/app/domain/sensor/PowerEstimatorImpl.kt` (lines 133-135)

**Issue:** The 30-second rolling window trimming logic removes only the first element when size > 60, but adds one element per call. Under high-frequency updates, this could cause unbounded growth.

**Fix:** Use a proper circular buffer or ensure trim happens before/after add consistently.

---

### 5. Data Display Lag (Multiple Seconds Behind Real-Time)
**Files:** Multiple - architectural issue

**Observed Issue:** Dashboard data lags several seconds behind real-time sensor readings.

**Root Causes Identified:**

#### A. GPS Callbacks on Main Thread
**File:** `app/src/main/java/com/cyclecomp/app/data/gps/GpsProviderImpl.kt` (line 96)
```kotlin
fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
```
**Problem:** GPS callbacks run on the main UI thread. If UI is busy rendering, GPS processing is delayed.

**Fix:** Use a background `HandlerThread` or `Dispatchers.Default` for GPS processing:
```kotlin
private val gpsHandler = HandlerThread("GpsThread").apply { start() }

fusedClient.requestLocationUpdates(
    request,
    callback,
    gpsHandler.looper  // Background thread instead of main
)
```

#### B. Multiple Independent Flow Collectors Without Coordination
**File:** `app/src/main/java/com/cyclecomp/app/domain/sensor/SensorHubImpl.kt`
```kotlin
// Line 77: HR from wearable → emitSnapshot()
// Line 88: HR from Health Connect → emitSnapshot()
// Line 105: CSC data → emitSnapshot()
// Line 125: GPS location → emitSnapshot()
```
**Problem:** Each data source independently calls `emitSnapshot()`, creating race conditions and redundant state updates. No throttling or debouncing.

**Fix:** Use `combine()` to merge all flows and emit once:
```kotlin
combine(
    wearableHrReceiver.latestHeartRate,
    gpsProvider.location,
    bleManager.cscData
) { hr, gps, csc ->
    buildSnapshot(hr, gps, csc)
}.collect { snapshot ->
    _sensorSnapshot.value = snapshot
}
```

#### C. Multiple ViewModel Collectors Causing Cascading Updates
**File:** `app/src/main/java/com/cyclecomp/app/ui/dashboard/DashboardViewModel.kt`

The ViewModel has 12+ separate `collect` blocks in `init{}`, each updating `_uiState` separately, causing cascading recompositions that block the main thread.

**Fix:** Combine related flows and update UI state atomically.

---

### 6. GPX/FIT Files May Not Save (Null External Storage)
**File:** `app/src/main/java/com/cyclecomp/app/ui/dashboard/DashboardViewModel.kt` (line 513)

```kotlin
val exportDir = File(appContext.getExternalFilesDir(null), "rides")
```

**Issue:** `getExternalFilesDir(null)` can return `null` if:
- External storage is unavailable (USB storage mode)
- Storage is full
- Running on certain emulator configurations

When null, `File(null, "rides")` creates a relative path that may fail silently or write to wrong location.

**Symptoms:**
- Files appear to export successfully but aren't found later
- Previous rides disappear after app restart
- Export works sometimes but not always

**Fix:**
```kotlin
val externalDir = appContext.getExternalFilesDir(null)
val exportDir = if (externalDir != null) {
    File(externalDir, "rides")
} else {
    // Fallback to internal storage
    File(appContext.filesDir, "rides")
}
if (!exportDir.exists() && !exportDir.mkdirs()) {
    throw IOException("Failed to create export directory: ${exportDir.absolutePath}")
}
```

---

### 7. Memory Leak in SensorHubImpl - Accumulating CSC Jobs
**File:** `app/src/main/java/com/cyclecomp/app/domain/sensor/SensorHubImpl.kt` (lines 133-141)

```kotlin
private fun collectCscFromDevice(address: String) {
    collectJobs += scope.launch {
        bleManager.getCharacteristicFlow(address, BleManagerImpl.CSC_MEASUREMENT_UUID)
            .collectLatest { data ->
                processCscData(data)
                emitSnapshot()
            }
    }
}
```

**Issue:** Every time a device connects (line 117 triggers this), `collectCscFromDevice()` adds a new Job to `collectJobs`. If a device disconnects and reconnects multiple times during a ride, jobs accumulate:
- Old jobs for the same device aren't cancelled
- Memory grows with each reconnection
- Multiple collectors for same device cause duplicate processing

**Fix:**
```kotlin
private val cscJobs = mutableMapOf<String, Job>()

private fun collectCscFromDevice(address: String) {
    // Cancel existing job for this address before creating new one
    cscJobs[address]?.cancel()

    cscJobs[address] = scope.launch {
        bleManager.getCharacteristicFlow(address, BleManagerImpl.CSC_MEASUREMENT_UUID)
            .collectLatest { data ->
                processCscData(data)
                emitSnapshot()
            }
    }
}

override fun stop() {
    collectJobs.forEach { it.cancel() }
    collectJobs.clear()
    cscJobs.values.forEach { it.cancel() }
    cscJobs.clear()
    // ...rest of cleanup
}
```

---

## High Priority

### 8. Refactor DashboardViewModel (583 lines)
**File:** `app/src/main/java/com/cyclecomp/app/ui/dashboard/DashboardViewModel.kt`

**Issue:** This ViewModel handles too many responsibilities:
- Sensor data aggregation
- Ride state management
- Export operations (FIT/GPX)
- Sync operations (Strava/Health Connect)
- Error monitoring (BLE, GPS, Bluetooth)

**Recommendation:** Extract into focused use cases:
```
DashboardViewModel (coordinator)
├── SensorMonitoringUseCase
├── RideControlUseCase
├── ExportUseCase
└── SyncUseCase
```

---

### 9. Complete Missing Property-Based Tests
**File:** `.kiro/specs/cycling-computer/tasks.md`

**Missing test batches:**
- Batch 10: BLE reconnection properties
- Batch 15: GPS fallback semantics
- Batch 19: Serialization round-trip invariants
- Batch 23: Additional edge case coverage

**Recommendation:** Implement these tests to achieve full coverage of the 25 correctness properties defined in the design spec.

---

### 10. Enable ProGuard for Release Builds
**File:** `app/build.gradle.kts`

**Issue:** `isMinifyEnabled = false` in release configuration.

**Recommendation:** Enable minification with proper ProGuard/R8 rules to reduce APK size and obfuscate code.

---

### 11. Implement GPS Fallback from Watch
**File:** `app/src/main/java/com/cyclecomp/app/data/gps/GpsProviderImpl.kt`

**Issue:** The design spec mentions watch GPS fallback when phone GPS is unavailable, but this isn't implemented in `GpsProviderImpl`.

**Recommendation:** Add fallback logic to receive GPS data from the Wear OS companion app via Wearable Data Layer.

---

## Medium Priority

### 12. Add Dependency Injection to Wear OS App
**File:** `wear/src/main/java/com/cyclecomp/wear/`

**Issue:** Uses static singleton pattern (`PhoneCommandListenerService.sharedHealthServicesManager`) instead of proper DI.

**Recommendation:** Add Hilt to the Wear module for consistency with the phone app architecture.

---

### 13. Replace Manual JSON Parsing with Serialization Library
**File:** `app/src/main/java/com/cyclecomp/app/data/ble/BleManagerImpl.kt` (lines 386-479)

**Issue:** Manual JSON parsing for paired device storage is error-prone and verbose.

**Recommendation:** Use `kotlinx.serialization` (already available via Ktor) or add Moshi for type-safe JSON handling.

---

### 14. Centralize Default Constants
**Files:** Multiple locations

**Issue:** Default values are scattered throughout the codebase:
- Rider weight: 75kg
- Bike weight: 9kg
- FTP: 200W
- CdA, CRR, rho values

**Recommendation:** Create a `DefaultValues.kt` constants object and reference it everywhere.

---

### 15. Add Compose Error Boundaries
**File:** `app/src/main/java/com/cyclecomp/app/MainActivity.kt`

**Issue:** Basic try-catch exists but no comprehensive error handling for Compose crashes.

**Recommendation:** Implement error boundary composables to gracefully handle and display errors in the UI.

---

### 16. Fix Unused Constant Duplication
**File:** `app/src/main/java/com/cyclecomp/app/data/gps/GpsProviderImpl.kt`

**Issue:** `EARTH_RADIUS_KM = 6371.0` is defined as a constant but the value is duplicated inline in `haversineKm()`.

**Fix:** Use the constant in the function.

---

## Low Priority (Nice to Have)

### 17. Add Project README
**Location:** Project root

**Issue:** No README.md exists at the project root.

**Recommendation:** Add a README with:
- Project overview
- Features list
- Setup instructions
- Build commands
- Architecture diagram
- Screenshots

---

### 18. Improve Lap Management
**File:** `app/src/main/java/com/cyclecomp/app/domain/ride/LapManagerImpl.kt`

**Recommendation:** Consider adding:
- Auto-lap by distance (every X km)
- Auto-lap by time (every X minutes)
- Lap comparison (current vs previous/best)

---

### 19. Add Offline Maps Support
**Current:** Google Maps with internet dependency

**Recommendation:** Consider adding offline tile caching or integrating MapLibre for offline-first mapping.

---

### 20. Implement Ride History Screen
**Files:** UI layer

**Issue:** Rides are recorded and exported but there's no history/list view in the app.

**Recommendation:** Add a ride history screen showing:
- Past rides list
- Basic stats per ride
- Delete/export options

---

### 21. Add ANT+ Protocol Support
**Current:** BLE-only sensors

**Recommendation:** Consider adding ANT+ support for broader sensor compatibility (Garmin devices, older power meters).

---

### 22. Implement Structured Workouts
**Enhancement:** Allow users to create and follow structured interval workouts with:
- Target power zones
- Interval timers
- Visual/audio cues

---

## Code Quality Improvements

### 23. Add KDoc Comments to Public APIs
Many public interfaces and functions lack documentation. Priority files:
- `SensorHub.kt`
- `RideRecorder.kt`
- `PowerEstimator.kt`
- `GpsProvider.kt`

---

### 24. Add Static Analysis
**Recommendation:** Add detekt or ktlint configuration for consistent code style enforcement.

---

### 25. Add CI/CD Pipeline
**Recommendation:** Set up GitHub Actions for:
- Build verification on PR
- Test execution
- Lint checks
- APK artifact generation

---

## Performance Optimizations

### 26. Optimize Sensor Data Collection Rate
**Current:** 1-second updates for most sensors

**Recommendation:** Consider adaptive rates:
- Higher frequency during speed changes
- Lower frequency when stable
- User-configurable update rates

---

### 27. Implement GPS Filtering
**File:** `GpsProviderImpl.kt`

**Recommendation:** Add Kalman filtering for GPS data to reduce noise and improve accuracy, especially for gradient calculations.

---

### 28. Battery Optimization
**Recommendations:**
- Add battery-aware feature toggling
- Reduce GPS polling when stopped
- Batch Health Connect writes

---

## Security Improvements

### 29. Strava Token Storage
**Current:** Stored in DataStore

**Recommendation:** Use EncryptedSharedPreferences or Android Keystore for OAuth tokens.

---

### 30. Add Input Validation
Validate all sensor data boundaries:
- Speed: 0-120 km/h reasonable range
- Cadence: 0-200 RPM
- Heart rate: 30-220 BPM
- Power: 0-2500W

---

## Summary Table

| Priority | Count | Est. Effort |
|----------|-------|-------------|
| Critical (Bugs) | 7 | 1-2 days |
| High | 4 | 2-3 days |
| Medium | 5 | 1-2 weeks |
| Low | 6 | Ongoing |
| Code Quality | 3 | 1 week |
| Performance | 3 | 1 week |
| Security | 2 | 2-4 hours |

**Total items: 30 improvements identified**

---

*Document generated: 2026-03-27*

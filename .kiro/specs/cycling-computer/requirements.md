# Requirements Document

## Introduction

CycleComp is a native Android cycling computer application built with Kotlin and Jetpack Compose. The app turns a Samsung S24 phone into a full-featured bike computer, displaying real-time ride metrics on a single-screen dashboard layout. It connects to a Samsung Galaxy Watch 8 via BLE for heart rate and secondary GPS data, and to a Magene S314 BLE sensor for cadence. Power is estimated through a mathematical model (no physical power meter). The app records rides in .FIT and .GPX formats, syncs directly to Strava, and writes completed ride data to Android Health Connect for sharing with Samsung Health and other fitness apps. Google Maps integration provides live location tracking and turn-by-turn route following, with a kill switch to conserve battery.

## Glossary

- **App**: The CycleComp Android application running on the phone
- **Dashboard**: The single main screen displaying all ride metrics in a tile-based layout
- **BLE_Manager**: The subsystem responsible for scanning, connecting, and maintaining Bluetooth Low Energy connections to external sensors
- **Sensor_Hub**: The subsystem that aggregates data from all sensor sources (watch, cadence sensor, phone) and resolves fallback logic
- **Watch**: The Samsung Galaxy Watch 8, acting as a BLE sensor source for heart rate and secondary GPS
- **Cadence_Sensor**: The Magene S314 BLE cadence sensor
- **Power_Estimator**: The subsystem that calculates estimated cycling power in Watts using a mathematical model based on speed, gradient, rider weight, and other parameters
- **GPS_Provider**: The subsystem that obtains location and speed data from the phone GPS, with watch GPS as a secondary source
- **Map_View**: The Google Maps component embedded in the dashboard for live location and route display
- **Ride_Recorder**: The subsystem that records all ride data over time for export and analysis
- **File_Exporter**: The subsystem that serializes ride data into .FIT and .GPX file formats
- **Strava_Sync**: The subsystem that uploads completed ride files to Strava via the Strava API
- **Health_Connect**: The Android Health Connect API integration that writes completed ride data (exercise session, heart rate, distance, calories, route) to the Health Connect datastore, making it available to Samsung Health and other compatible fitness apps
- **Auto_Pause**: The feature that automatically pauses ride recording when the cyclist stops moving
- **Lap_Manager**: The subsystem that tracks lap-level statistics (lap time, lap distance, lap average power)
- **Theme_Engine**: The subsystem controlling visual appearance including night mode and font sizing

## Requirements

### Requirement 1: BLE Sensor Discovery and Connection

**User Story:** As a cyclist, I want the app to discover and connect to my BLE sensors, so that I can receive heart rate and cadence data during my ride.

#### Acceptance Criteria

1. WHEN the cyclist initiates sensor scanning, THE BLE_Manager SHALL discover all available BLE heart rate and cadence sensor devices within range and display them in a list within 10 seconds.
2. WHEN the cyclist selects a sensor from the discovered list, THE BLE_Manager SHALL establish a BLE connection to the selected sensor within 5 seconds.
3. WHILE a BLE sensor is connected, THE BLE_Manager SHALL maintain the connection and receive data updates at the sensor's native broadcast rate.
4. IF a BLE sensor connection is lost during a ride, THEN THE BLE_Manager SHALL attempt automatic reconnection every 5 seconds for up to 60 seconds.
5. IF automatic reconnection fails after 60 seconds, THEN THE BLE_Manager SHALL display a persistent notification indicating the disconnected sensor.
6. WHEN the app is launched, THE BLE_Manager SHALL attempt to reconnect to previously paired sensors automatically.

### Requirement 2: Heart Rate Data Acquisition

**User Story:** As a cyclist, I want to see my real-time heart rate from my watch, so that I can monitor my effort during a ride.

#### Acceptance Criteria

1. WHILE the Watch is connected via BLE, THE Sensor_Hub SHALL receive heart rate data in beats per minute (bpm) from the Watch.
2. THE Dashboard SHALL display the current heart rate value in bpm at double the font size of other metric tiles.
3. WHEN heart rate data is received, THE Sensor_Hub SHALL determine and display the current heart rate zone (zones 1 through 5) in parentheses next to the heart rate value.
4. IF the Watch heart rate signal is unavailable, THEN THE Sensor_Hub SHALL indicate "no signal" on the heart rate tile rather than displaying stale data.

### Requirement 3: Cadence Data Acquisition

**User Story:** As a cyclist, I want to see my real-time cadence from my Magene sensor, so that I can maintain an optimal pedaling rhythm.

#### Acceptance Criteria

1. WHILE the Cadence_Sensor is connected via BLE, THE Sensor_Hub SHALL receive cadence data in revolutions per minute (RPM) from the Cadence_Sensor.
2. THE Dashboard SHALL display the current cadence value in RPM.
3. IF the Cadence_Sensor signal is unavailable, THEN THE Sensor_Hub SHALL display "no signal" on the cadence tile rather than displaying stale data.

### Requirement 4: Speed Measurement via GPS

**User Story:** As a cyclist, I want to see my current speed and average speed, so that I can pace my ride effectively.

#### Acceptance Criteria

1. WHILE a ride is active, THE GPS_Provider SHALL calculate current speed in km/h from phone GPS location updates.
2. THE Dashboard SHALL display the current real-time speed in km/h.
3. THE Dashboard SHALL display the average speed over the last completed kilometer in km/h.
4. IF phone GPS signal is unavailable and Watch GPS is available, THEN THE GPS_Provider SHALL use Watch GPS data as a fallback source for speed calculation.
5. IF both phone GPS and Watch GPS signals are unavailable, THEN THE GPS_Provider SHALL display "no GPS" on the speed tile.

### Requirement 5: Power Estimation

**User Story:** As a cyclist, I want to see my estimated power output in Watts, so that I can gauge my effort without a physical power meter.

#### Acceptance Criteria

1. WHILE a ride is active, THE Power_Estimator SHALL calculate estimated power output in Watts using current speed, gradient, rider weight, bike weight, and aerodynamic coefficients.
2. THE Dashboard SHALL display the current estimated power in Watts (W).
3. THE Dashboard SHALL display the average estimated power in Watts (W) for the current ride.
4. WHEN the rider is stationary, THE Power_Estimator SHALL display 0 W for current power.

### Requirement 6: Elevation and Gradient Tracking

**User Story:** As a cyclist, I want to see my elevation gain and current gradient, so that I can understand the terrain I am riding.

#### Acceptance Criteria

1. WHILE a ride is active, THE GPS_Provider SHALL track cumulative elevation gain in meters from barometric altimeter or GPS altitude data.
2. THE Dashboard SHALL display total elevation gain in meters (m).
3. WHILE a ride is active, THE GPS_Provider SHALL calculate the current road gradient as a percentage based on altitude change over horizontal distance.
4. THE Dashboard SHALL display the current gradient as a percentage (%).

### Requirement 7: Distance and Time Tracking

**User Story:** As a cyclist, I want to see my total distance and elapsed ride time, so that I can track my ride progress.

#### Acceptance Criteria

1. WHILE a ride is active, THE GPS_Provider SHALL calculate cumulative ride distance in kilometers from GPS position data.
2. THE Dashboard SHALL display total ride distance in km with two decimal places.
3. WHILE a ride is active, THE Ride_Recorder SHALL track elapsed ride time excluding auto-paused periods.
4. THE Dashboard SHALL display elapsed ride time in HH:MM:SS format.

### Requirement 8: Calories and Training Stress Score

**User Story:** As a cyclist, I want to see my estimated calories burned and Training Stress Score, so that I can understand my training load.

#### Acceptance Criteria

1. WHILE a ride is active, THE App SHALL calculate estimated calories burned based on heart rate, rider weight, and ride duration.
2. THE Dashboard SHALL display estimated calories burned in kilocalories (kcal).
3. WHILE a ride is active, THE App SHALL calculate Training Stress Score (TSS) based on normalized power, functional threshold power (FTP), and ride duration.
4. THE Dashboard SHALL display the current TSS value as a whole number.
5. WHEN the rider has not configured an FTP value, THE App SHALL use a default FTP of 200 W for TSS calculation.

### Requirement 9: Dashboard Layout

**User Story:** As a cyclist, I want a single-screen dashboard that shows all my ride data at a glance, so that I can read metrics without navigating between screens.

#### Acceptance Criteria

1. THE Dashboard SHALL display all metric tiles in a single vertically scrollable screen without requiring horizontal swiping or screen navigation.
2. THE Dashboard SHALL arrange tiles as follows: top row contains Power (W) and Speed (km/h with avg); second row contains Heart Rate (bpm, double-sized) and Cadence (RPM); middle section contains the Map_View; bottom row contains Distance (km) and Time (HH:MM:SS).
3. THE Dashboard SHALL display additional metrics (gradient %, TSS, elevation gain, calories) in available space around the primary tiles.
4. THE Dashboard SHALL render each tile with a styled background to provide visual distinction between metrics.
5. WHILE a ride is active, THE Dashboard SHALL keep the screen awake and prevent the device from sleeping.

### Requirement 10: Google Maps Integration

**User Story:** As a cyclist, I want to see my current location on a map and follow a pre-planned route, so that I can navigate during my ride.

#### Acceptance Criteria

1. THE Map_View SHALL display the cyclist's current GPS location on a Google Maps view embedded in the dashboard.
2. WHEN the cyclist loads a pre-planned route file, THE Map_View SHALL render the route as an overlay on the map.
3. WHILE following a pre-planned route, THE Map_View SHALL provide turn-by-turn navigation cues.
4. WHEN the cyclist activates the map kill switch, THE Map_View SHALL disable all map rendering and GPS map updates to conserve battery.
5. WHEN the map kill switch is deactivated, THE Map_View SHALL resume map rendering and location tracking within 3 seconds.

### Requirement 11: Lap Tracking

**User Story:** As a cyclist, I want to record laps during my ride, so that I can analyze interval performance.

#### Acceptance Criteria

1. WHEN the cyclist triggers a lap marker, THE Lap_Manager SHALL record the current lap time, lap distance, and lap average power.
2. WHEN a new lap is started, THE Lap_Manager SHALL reset lap-specific counters (lap time, lap distance, lap power accumulator) to zero.
3. THE App SHALL store all lap records as part of the ride data for export.

### Requirement 12: Auto-Pause

**User Story:** As a cyclist, I want the app to automatically pause recording when I stop, so that my ride statistics reflect actual moving time.

#### Acceptance Criteria

1. WHEN the cyclist's speed drops below 2 km/h for more than 3 seconds, THE Auto_Pause SHALL pause ride recording automatically.
2. WHEN the cyclist's speed exceeds 2 km/h after an auto-pause, THE Auto_Pause SHALL resume ride recording automatically.
3. WHILE ride recording is auto-paused, THE Ride_Recorder SHALL stop accumulating elapsed ride time and distance.
4. WHILE ride recording is auto-paused, THE Dashboard SHALL display a visible "Paused" indicator.

### Requirement 13: Ride Recording and File Export

**User Story:** As a cyclist, I want to save my completed rides as .FIT and .GPX files, so that I can keep a record and analyze my data.

#### Acceptance Criteria

1. WHEN the cyclist ends a ride, THE Ride_Recorder SHALL save the complete ride data including all GPS points, heart rate, cadence, power, elevation, laps, and timestamps.
2. THE File_Exporter SHALL serialize ride data into a valid .FIT file conforming to the Garmin FIT SDK specification.
3. THE File_Exporter SHALL serialize ride data into a valid .GPX file conforming to the GPX 1.1 schema.
4. FOR ALL valid ride recordings, serializing to .FIT then deserializing SHALL produce a data set equivalent to the original ride data (round-trip property).
5. FOR ALL valid ride recordings, serializing to .GPX then deserializing SHALL produce a data set equivalent to the original ride data (round-trip property).
6. IF file export fails due to storage error, THEN THE File_Exporter SHALL notify the cyclist with a descriptive error message and retain the ride data in memory for retry.

### Requirement 14: Strava Sync

**User Story:** As a cyclist, I want to upload my rides directly to Strava, so that I can share and analyze my rides on the platform.

#### Acceptance Criteria

1. WHEN the cyclist requests a Strava upload after completing a ride, THE Strava_Sync SHALL authenticate with the Strava API using OAuth 2.0.
2. WHEN authenticated, THE Strava_Sync SHALL upload the ride .FIT file to Strava and confirm successful upload to the cyclist.
3. IF the Strava upload fails due to network error, THEN THE Strava_Sync SHALL queue the ride for retry and notify the cyclist of the failure.
4. IF the Strava authentication token has expired, THEN THE Strava_Sync SHALL refresh the token automatically before attempting upload.

### Requirement 15: Health Connect Integration

**User Story:** As a cyclist, I want my completed ride data pushed to Health Connect, so that Samsung Health and other fitness apps can access my cycling sessions.

#### Acceptance Criteria

1. WHEN a ride is saved, THE Health_Connect SHALL write an ExerciseSession of type BIKING to the Health Connect datastore including start time, end time, and total distance.
2. WHEN a ride is saved, THE Health_Connect SHALL write heart rate samples, calories burned, and route GPS points as associated Health Connect records.
3. IF Health Connect is not installed on the device, THEN THE App SHALL prompt the cyclist to install Health Connect and skip the write without error.
4. WHEN the cyclist has not granted Health Connect write permissions, THE App SHALL request the required permissions before attempting to write ride data.
5. IF the Health Connect write fails, THEN THE App SHALL notify the cyclist with a descriptive error message and retain the ride data for retry.

### Requirement 16: Night Mode and Accessibility

**User Story:** As a cyclist, I want a night mode and large font option, so that I can read the screen in varying light conditions and at a glance while riding.

#### Acceptance Criteria

1. WHEN the cyclist enables night mode, THE Theme_Engine SHALL switch the dashboard to a dark color scheme with high-contrast text.
2. WHEN the cyclist enables the large font option, THE Theme_Engine SHALL increase all metric font sizes by 50% relative to the default size.
3. THE Theme_Engine SHALL persist the cyclist's night mode and font size preferences across app restarts.
4. WHEN the cyclist disables night mode, THE Theme_Engine SHALL revert to the default light color scheme.

### Requirement 17: Ride Start, Stop, and Controls

**User Story:** As a cyclist, I want clear controls to start, pause, and stop my ride, so that I have full control over recording.

#### Acceptance Criteria

1. WHEN the cyclist taps the start button, THE Ride_Recorder SHALL begin recording ride data and start the elapsed time counter.
2. WHEN the cyclist taps the pause button, THE Ride_Recorder SHALL pause ride recording and stop accumulating time and distance.
3. WHEN the cyclist taps the stop button, THE Ride_Recorder SHALL end the ride and prompt the cyclist to save and export the ride data.
4. THE Dashboard SHALL display the current ride state (recording, paused, stopped) with a clear visual indicator.

### Requirement 18: Rider Profile Configuration

**User Story:** As a cyclist, I want to configure my weight, bike weight, and FTP, so that power estimation and TSS calculations are accurate for me.

#### Acceptance Criteria

1. THE App SHALL provide a settings screen where the cyclist can enter rider weight in kilograms, bike weight in kilograms, and Functional Threshold Power (FTP) in Watts.
2. THE App SHALL persist all rider profile settings across app restarts.
3. WHEN rider profile values are updated, THE Power_Estimator SHALL use the updated values for all subsequent power calculations within the same ride.
4. IF the cyclist has not configured rider weight, THEN THE App SHALL use a default rider weight of 75 kg.
5. IF the cyclist has not configured bike weight, THEN THE App SHALL use a default bike weight of 9 kg.

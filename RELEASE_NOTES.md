# MissionTrackerMap Release v1.5.0

## Features & Improvements

### 📱 Android Tracker App
- **Fix Manual Sync Permissions**: Fixed a bug where manually syncing a single mission deleted the local `.owner` file, causing the "Edit Mission" option to disappear from the menu.
- **Rules updates**: Updated project rules to ensure developers keep version strings synchronized between build files and UI views.

---

# MissionTrackerMap Release v1.4.0

## Features & Improvements

### 📱 Android Tracker App
- **Scrollable Mission Point Details**: Added option to scroll down/up if the mission point description is longer text that does not fit the view, both in the mission point editor dialog and the map point detail dialog.

---

# MissionTrackerMap Release v1.3.0

## Features & Improvements

### 📱 Android Tracker App
- **GPS Override Disabled by Default**: Coordinates now come from the physical GPS receiver by default instead of using the mocked/override coordinate coordinates.
- **Password Visibility Toggle**: Added an eye icon/toggle to the login screen to allow users to show or hide their password input.

---

# MissionTrackerMap Release v1.2.0

## Features

### 📱 Android Tracker App
- **Interactive Map View (`MapScreen.kt`)**: Displays game map and tracks real-time location.
- **GPS Integration (`FusedLocationProvider.kt`)**: Uses Google Play Services for accurate location data.
- **Coordinate Transformation (`AffineTransformer.kt`)**: Real-time conversion between GPS coordinates and map pixels.
- **Mission Editor (`EditMissionScreen.kt`)**: Add, modify, and delete mission points directly on the device.
- **Cloud Syncing (`SupabaseSyncManager.kt` & `SupabaseAuthManager.kt`)**: Seamless user authentication and database synchronization via Supabase.
- **Secure Credentials (`CredentialManager.kt`)**: Safe storage of authentication and API keys on the device.

### 💻 Calibration Editor (Desktop)
- **Map Visualizer**: Load and display PNG maps.
- **Navigation**: Full support for Zoom and Pan controls.
- **Calibration Point Management**: Interactively create, edit, and position calibration points.
- **Data Export**: Save and load calibrated points to structured JSON format.

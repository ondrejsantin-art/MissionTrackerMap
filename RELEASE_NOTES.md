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

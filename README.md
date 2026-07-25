# MissionTrackerMap

Toolkit for GPS-based outdoor games. Consists of a desktop map calibration editor and an Android tracking application.

## Components

- **Android Tracker**: Kotlin-based Android application. Tracks real-time GPS location, performs coordinate transformation, and syncs data via Supabase.
- **Calibration Editor**: PySide6 desktop application to calibrate map images (PNG) and export control points to JSON.

---

## 📱 Android Tracker Installation

### Option 1: Download Pre-built Release APK (Recommended)
1. Go to the **Releases** section on GitHub.
2. Download the latest `MissionTrackerMap-v*.apk` file.
3. Transfer the APK to your Android device (or download it directly on the device).
4. Enable installation from unknown sources in your device settings.
5. Open the APK file to install the application.

### Option 2: Build and Install from Source
#### Prerequisites
- JDK 17
- Android SDK & ADB configured in path

#### Build Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/ondrejsantin-art/MissionTrackerMap.git
   cd MissionTrackerMap/android
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install on a connected device/emulator:
   - Using ADB:
     ```bash
     adb install app/build/outputs/apk/debug/MissionTrackerMap-debug.apk
     ```
   - Using the `android` developer tool:
     ```bash
     android run --apks=android/app/build/outputs/apk/debug/MissionTrackerMap-debug.apk
     ```

---

## 💻 Calibration Editor Installation

### Prerequisites
- Python 3.10+
- PySide6, Pydantic, keyring, requests

### Setup & Run
1. Navigate to the editor directory:
   ```bash
   cd calibration-editor
   ```
2. Create virtual environment and install dependencies:
   ```bash
   python -m venv ../.venv
   source ../.venv/bin/activate
   pip install -r requirements.txt
   ```
3. Run the desktop application:
   ```bash
   ./run.sh
   ```

---

## Features

### Android Tracker
- **Interactive Map View (`MapScreen.kt`)**: Renders game map and real-time location.
- **GPS Integration (`FusedLocationProvider.kt`)**: Accurate coordinate tracking.
- **Coordinate Transformation (`AffineTransformer.kt`)**: Dynamic GPS-to-pixel mapping.
- **Mission Editor (`EditMissionScreen.kt`)**: Define custom game missions. Features include:
  - Defining complete missions with custom metadata and descriptions.
  - Adding, modifying, and deleting sequential mission points (checkpoints).
  - Setting specific descriptions, coordinates, and instructions for each mission point.
- **Cloud Syncing (`SupabaseSyncManager.kt`)**: Supabase authentication and database sync.
- **Secure Credentials (`CredentialManager.kt`)**: Device-secured API key storage.

### Calibration Editor
- **Map Visualizer**: Load and interact with PNG maps.
- **Pan & Zoom**: Smooth navigation across large images.
- **Calibration Points**: Interactively set, edit, and adjust reference points.
- **Data Export**: Save calibration data to JSON format.

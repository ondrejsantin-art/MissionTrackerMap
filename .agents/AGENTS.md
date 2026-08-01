# Project Rules

To run the Android application on a connected phone, use:
```bash
android run --apks=android/app/build/outputs/apk/debug/MissionTrackerMap-debug.apk
```

- Always write unit tests for any newly developed features and validate the code against them.
- When creating a new release:
  1. Generate release notes from recent commits and prepend to `RELEASE_NOTES.md`.
  2. Update `versionCode` and `versionName` in `android/app/build.gradle.kts`.
  3. Update the version string in `android/app/src/main/java/com/example/missiontrackermap/ui/MapScreen.kt`.
  4. Commit changes with message `chore: prepare release vX.Y.Z` and push.
  5. Create and push an annotated git tag for the release (e.g., `vX.Y.Z`).

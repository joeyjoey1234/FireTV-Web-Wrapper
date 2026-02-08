# Fire TV Web Wrapper

Minimal Fire TV-focused Android WebView wrapper that launches any self-hosted web UI with D-pad friendly navigation.

## Features
- Guided server URL onboarding flow with validation and persistence
- Settings screen for HTTP/HTTPS toggles, user-agent switching (Mobile, Desktop, Auto), and SSL allowances
- Enhanced TV navigation helpers (focus outlines, spatial navigation, modal trapping, click dispatch fixes)
- Back-stack protection so login pages are not revisited accidentally
- Optional debug-only WebView inspection

## Repository layout
Only the files that are required to build the APK are intended for Git, everything else (build outputs, keystores, IDE metadata, etc.) is ignored via `.gitignore`.

```
app/                     Android source, resources, and manifest
build.gradle.kts         Root Gradle configuration
settings.gradle.kts      Gradle project definition
gradle/ + gradlew*       Wrapper so `./gradlew` works without extra installs
keystore.properties.example  Template for local release signing secrets (never commit the real file)
README.md                This document
```

## Prerequisites
- JDK 17+
- Android SDK / command line tools with API 30 (Android 11 / Fire OS 8) platform installed
- adb for sideloading the resulting APK (optional but recommended)

## Local setup
1. Clone the repository.
2. Copy `keystore.properties.example` to `keystore.properties` (kept out of Git) and fill it with your keystore details if you plan to ship a signed release. Skip this step for debug builds.
3. Run `./gradlew tasks` once to download the wrapper dependencies.

## Building
### Debug build (installable immediately)
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build (for GitHub downloads)
```bash
./gradlew assembleRelease
```
If `keystore.properties` is present, the release APK is signed automatically. Otherwise Gradle will emit `app-release-unsigned.apk` that you must sign manually:
```bash
${'$'}ANDROID_SDK_ROOT/build-tools/<version>/apksigner sign \
  --ks my-release-key.jks \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```
Rename the signed artifact (e.g., `firetv-web-wrapper-1.0.0.apk`) before uploading.

## Publishing the first GitHub release
1. Ensure `./gradlew assembleRelease` succeeds and produce a signed APK as described above.
2. Create a Git tag that matches the app `versionName` (currently `v1.0.0`).
3. Draft a GitHub release for that tag and attach the signed APK so users can download it without cloning the repo.
4. Repeat these steps for future versions—no additional files need to be committed besides the source and Gradle wrapper.

## Configuration quick reference
- Server URL: stored under `Prefs.KEY_SERVER_URL` and editable from the Settings screen.
- Allow HTTP / invalid SSL: toggles exposed in Settings for environments that rely on self-signed certs.
- User-agent modes: Auto (default), Mobile, Desktop. Changing the mode triggers a WebView reload.

## Application ID
`tv.firetvwebwrapper.app`

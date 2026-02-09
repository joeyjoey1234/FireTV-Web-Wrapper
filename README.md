# Fire TV Web Wrapper

Minimal Fire TV-focused Android WebView wrapper that launches any self-hosted web UI with D-pad friendly navigation.

## Features
- Guided server URL onboarding flow with validation and persistence
- Settings screen for HTTP/HTTPS toggles, user-agent switching (Mobile, Desktop, Auto), and SSL allowances
- Multi-home bookmark manager to store multiple server URLs and pick the active home screen on the fly
- Built-in GitHub release update checker with on-device APK download + installer handoff
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
2. Copy `keystore.properties.example` to `keystore.properties` (kept out of Git) and point it at your release keystore. The alias **must** be `joepe-na-signer` so it matches the signing config baked into the app. Example:
   ```properties
   storeFile=/absolute/path/to/firetv-release.jks
   storePassword=***
   keyAlias=joepe-na-signer
   keyPassword=***
   ```
   Skip this step for debug builds.
3. Run `./gradlew tasks` once to download the wrapper dependencies.

## Building
### Debug build (installable immediately)
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build (for GitHub downloads)
```bash
./gradlew clean assembleRelease
```
With `keystore.properties` present the APK is signed automatically at `app/build/outputs/apk/release/app-release.apk`. Rename and checksum it before uploading so users can validate the download (swap `1.1.0` for whatever `versionName` you're shipping):
```bash
cp app/build/outputs/apk/release/app-release.apk firetv-web-wrapper-1.1.0.apk
sha256sum firetv-web-wrapper-1.1.0.apk
```
If you skip `keystore.properties` Gradle emits `app-release-unsigned.apk`; sign it manually with `apksigner` before distribution.

## Publishing GitHub releases
1. Confirm `./gradlew clean assembleRelease` succeeds, rename the APK to `firetv-web-wrapper-1.1.0.apk` (match the `versionName`), and capture the SHA-256 hash.
2. Tag the commit that contains the release build: `git tag v1.1.0 -m "FireTV Web Wrapper 1.1.0" && git push origin v1.1.0`.
3. Push the branch if you have not already: `git push origin main`.
4. Create the GitHub release, attach the renamed APK, and include the checksum in the release notes. Example using the GitHub CLI:
   ```bash
   gh release create v1.1.0 firetv-web-wrapper-1.1.0.apk \
     --title "FireTV Web Wrapper 1.1.0" \
     --notes "- Multi-home bookmark workflow\n- GitHub-powered in-app updater\n- Navigation & focus polish"
   ```
5. The in-app updater fetches `https://api.github.com/repos/joeyjoey1234/FireTV-Web-Wrapper/releases/latest`. If you host the code under a different owner or repo name, update the constants in `UpdateApi.kt` accordingly before tagging.
6. Repeat these steps for future versions and increment `versionCode`/`versionName` in `app/build.gradle.kts` as needed.

## Configuration quick reference
- Server URL: stored under `Prefs.KEY_SERVER_URL` and editable from the Settings screen.
- Saved home URLs / bookmarks: managed by `BookmarkStore` with JSON stored under `Prefs.KEY_BOOKMARKS`. The Settings shortcut opens the bookmark manager activity.
- Allow HTTP / invalid SSL: toggles exposed in Settings for environments that rely on self-signed certs.
- User-agent modes: Auto (default), Mobile, Desktop. Changing the mode triggers a WebView reload.
- Update checks: `Settings > Support > Check for updates` hits the GitHub Releases API and downloads the latest APK when a newer `versionName` is published.

## Application ID
`tv.firetvwebwrapper.app`

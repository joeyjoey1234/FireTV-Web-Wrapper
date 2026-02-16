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
### Automated (recommended)
This repository includes `.github/workflows/release.yml`, which publishes a release automatically when you push a semver tag like `v1.1.2`.

It uses GitHub Actions' built-in `GITHUB_TOKEN` for release API access (no PAT required).

Required repository secrets:
- `RELEASE_KEYSTORE_BASE64` (base64-encoded JKS file)
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`

The workflow enforces:
- tag `vX.Y.Z` matches `versionName` in `app/build.gradle.kts`
- signed `assembleRelease` build
- release asset upload (`firetv-web-wrapper-<version>.apk`)
- checksum upload (`firetv-web-wrapper-<version>.apk.sha256`)

Release flow:
1. Update `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push `main`.
3. Create and push tag (example): `git tag v1.1.2 && git push origin v1.1.2`.

### Manual fallback
1. Confirm `./gradlew clean assembleRelease` succeeds, rename the APK to match `versionName`, and capture SHA-256.
2. Push branch and tag.
3. Create GitHub release and upload APK plus checksum.

The in-app updater fetches `https://api.github.com/repos/joeyjoey1234/FireTV-Web-Wrapper/releases/latest`. If the repository owner/name changes, update `UpdateApi.kt`.

## Configuration quick reference
- Server URL: stored under `Prefs.KEY_SERVER_URL` and editable from the Settings screen.
- Saved home URLs / bookmarks: managed by `BookmarkStore` with JSON stored under `Prefs.KEY_BOOKMARKS`. The Settings shortcut opens the bookmark manager activity.
- Allow HTTP / invalid SSL: toggles exposed in Settings for environments that rely on self-signed certs.
- User-agent modes: Auto (default), Mobile, Desktop. Changing the mode triggers a WebView reload.
- Update checks: `Settings > Support > Check for updates` hits the GitHub Releases API and downloads the latest APK when a newer `versionName` is published.

## Application ID
`tv.firetvwebwrapper.app`

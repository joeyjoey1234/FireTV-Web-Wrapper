# FireTV Web Wrapper — Agent Notes

## Build & Environment
- Use the included Gradle wrapper (`./gradlew`). No need to install Gradle globally.
- JDK 17 already available in the workspace.
- Android SDK command-line tools + platform 34 are installed under `/home/leet/android-sdk`. Run `export ANDROID_SDK_ROOT=/home/leet/android-sdk && export ANDROID_HOME=/home/leet/android-sdk` before invoking Gradle in new shells.
- Debug build: `./gradlew assembleDebug` (installs with `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
- Release build: `./gradlew assembleRelease`. Provide `keystore.properties` to sign automatically; otherwise Gradle outputs `app-release-unsigned.apk`.

## Signing
- Copy `keystore.properties.example` to `keystore.properties` (ignored by Git) and fill in `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- Without that file the release build remains unsigned; use `apksigner` afterward to sign before distribution.

## Repository Hygiene
- `.gitignore` already excludes build outputs, IDE metadata, keystore files, and APK/AAB artifacts. Keep only the minimal source/Gradle wrapper files in version control, as requested by the user.

## Release Publishing
- Current versionName/versionCode: `1.0.0` / `1`.
- Release APK output path: `app/build/outputs/apk/release/app-release-unsigned.apk` (hash example available via `sha256sum`).
- Tag releases (`v1.0.0` etc.) and upload the signed APK as a GitHub release asset.

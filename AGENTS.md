# FireTV Web Wrapper — Agent Notes

## Build & Environment
- Use the included Gradle wrapper (`./gradlew`). No need to install Gradle globally.
- JDK 17 already available in the workspace.
- Android SDK command-line tools + platform 34 are installed under `/home/leet/android-sdk`. Run `export ANDROID_SDK_ROOT=/home/leet/android-sdk && export ANDROID_HOME=/home/leet/android-sdk` before invoking Gradle in new shells.
- Debug build: `./gradlew assembleDebug` (installs with `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
- Release build: `./gradlew clean assembleRelease`. Output lands at `app/build/outputs/apk/release/app-release.apk`; rename to `firetv-web-wrapper-<version>.apk` and run `sha256sum` before uploading.

## Signing
- Copy `keystore.properties.example` to `keystore.properties` (ignored by Git) and fill in `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. The alias is fixed to `joepe-na-signer`; builds will fail if it differs.
- Without that file the release build remains unsigned; use `apksigner` afterward to sign before distribution.

## Repository Hygiene
- `.gitignore` already excludes build outputs, IDE metadata, keystore files, and APK/AAB artifacts. Keep only the minimal source/Gradle wrapper files in version control, as requested by the user.

## Release Publishing
- Current versionName/versionCode: `1.1.0` / `2`. Update these in `app/build.gradle.kts` before tagging future releases.
- Run `./gradlew clean assembleRelease`, then copy `app/build/outputs/apk/release/app-release.apk` to `firetv-web-wrapper-<version>.apk` and capture `sha256sum` for the release notes.
- Tag the build commit (`git tag v1.1.0 && git push origin v1.1.0`), push `main`, and create the GitHub release attaching the renamed APK + checksum.
- The in-app updater expects releases to live at `joeyjoey1234/FireTV-Web-Wrapper`. Adjust `UpdateApi.kt` if the repository slug changes.

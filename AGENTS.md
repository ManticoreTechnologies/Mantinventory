# Agents

## Cursor Cloud specific instructions

### Project overview

Mantinventory is a standalone native Android app (Kotlin + Jetpack Compose) for physical inventory management. There is no backend, no web frontend, and no external services. All data is local (Room/SQLite). See `Readme.md` for feature details.

### Environment

- **Android SDK** is installed at `/opt/android-sdk` with platform 35 and build-tools 35.0.0.
- **JDK 21** is pre-installed (JVM target is 17; JDK 21 is backwards-compatible).
- `ANDROID_HOME` and `JAVA_HOME` are exported in `~/.bashrc`. A `local.properties` file with `sdk.dir` is created by the update script.
- No Docker, Node.js, or other system services are required.

### Common commands

| Task | Command |
|---|---|
| Build debug APK | `./gradlew :app:assembleDebug` |
| Run unit tests | `./gradlew :app:testDebugUnitTest` |
| Run lint | `./gradlew :app:lintDebug` |
| Full check (build + test) | `./gradlew :app:assembleDebug :app:testDebugUnitTest` |

### Known issues in the codebase

- Lint reports 2 pre-existing errors (`UnsafeOptInUsageError` for CameraX experimental API and `PermissionImpliesUnsupportedChromeOsHardware` for missing `<uses-feature>`). These are not introduced by setup — they exist in the source.
- Kotlin compiler warnings about deprecated API usage (`ArrowBack`, `LocalLifecycleOwner`) and missing `@OptIn` for `ExperimentalCoroutinesApi` are pre-existing.

### Notes

- This is a pure Android project — there is no emulator in the cloud VM, so you cannot run the app on-device. Testing is limited to compilation, unit tests, and lint.
- `local.properties` is gitignored and created fresh by the update script each session.

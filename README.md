# Notification Monitor

Native Android app (Kotlin + Jetpack Compose) that monitors notifications from other apps via `NotificationListenerService`, stores them locally with Room, and lets you filter by source app.

All notification content stays on-device. Nothing is uploaded.

## Requirements

| Tool | Version |
|------|---------|
| Java (JDK) | 17 |
| Android SDK (compile / target) | 35 |
| Android `minSdk` | 26 |
| Build Tools | 35.0.0 |
| Gradle | 8.11.1 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |

Android Studio is **not** required. Build from the command line.

Set `ANDROID_HOME` (or create `local.properties` with `sdk.dir=...`).

## Development build

```bash
./gradlew assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release APK

```bash
./gradlew assembleRelease
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

Without signing credentials configured, AGP may emit:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## Android App Bundle

```bash
./gradlew bundleRelease
```

Output:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Install with ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# or
adb install app/build/outputs/apk/release/app-release.apk
```

## Unit tests

```bash
./gradlew test
```

## Notification access

The app cannot grant notification-listener access itself. After install:

1. Open **Settings**
2. **Apps** → **Special app access** (wording varies by OEM)
3. **Notification access**
4. Enable **Notification Monitor**

Or use the in-app **Open notification access settings** button (Home / Settings).

Monitoring continues while the UI is closed; the system binds `NotificationListenerService` independently of `MainActivity`.

## Features

- Capture notification posted events from other apps
- Local Room history with detail view
- Filter history by package
- Enable/disable monitoring per installed app
- Send a local test notification
- Configurable retention (1 / 7 / 30 days / Forever) with WorkManager cleanup
- Android 13+ `POST_NOTIFICATIONS` request for local notifications only

## Release signing

Do **not** commit keystores. For release builds, provide:

| Variable | Description |
|----------|-------------|
| `KEYSTORE_FILE` | Path to the keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Optional for CodeBuild: `KEYSTORE_BASE64` — base64-encoded keystore; `buildspec.yml` writes it to `/tmp/release.keystore`.

Recommended production flow:

```text
AWS Secrets Manager → CodeBuild env → assembleRelease / bundleRelease
```

Without signing env vars, release builds still produce an APK (unsigned/debug-signed depending on AGP defaults for missing `signingConfig`).

You can also put the same keys in a local (gitignored) `local.properties`.

## AWS CodeBuild

1. Create a CodeBuild project pointing at this repository.
2. Use a Linux image with Corretto 17 (see `buildspec.yml` `runtime-versions`).
3. Use `buildspec.yml` at the repo root.
4. Configure privilege needed to install Android SDK packages (or bake SDK into a custom image).
5. Attach artifact output to S3 / pipeline as needed.
6. Wire signing secrets (`KEYSTORE_BASE64` / passwords) from Secrets Manager or Parameter Store.
7. Expected artifacts:
   - `app/build/outputs/apk/release/app-release.apk`
   - `app/build/outputs/bundle/release/app-release.aab`

Local verification of the same commands CodeBuild runs:

```bash
./gradlew assembleRelease bundleRelease
```

## Project layout

```text
app/src/main/java/com/example/notificationmonitor/
  notification/   # Listener, parser, local NotificationManager
  database/        # Room entities + DAOs
  repository/      # Single UI data access layer
  ui/              # Compose: Home, History, Apps, Settings
  settings/        # Retention preferences
  work/            # Retention cleanup worker
```

Package / application id: `com.example.notificationmonitor` (change in `app/build.gradle.kts` `namespace` / `applicationId`).

## Privacy

- No backend
- No analytics
- Notification text is not logged
- Use **Clear all history** in Settings to wipe local data
# paper-plane

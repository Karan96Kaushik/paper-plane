# PaperPlane

Native Android app (Kotlin + Jetpack Compose) that monitors notifications from other apps via `NotificationListenerService`, stores them locally with Room, and lets you filter by source app.

Notification content stays on-device unless you turn on Supabase push in Settings.

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

## App version

The Gradle build assigns the SemVer `versionName` and a monotonic `versionCode`. It does not use a checked-in version number.

- The latest stable tag on this commit (`v1.2.3` or `1.2.3`) is the released version.
- Later commits follow Conventional Commits: `feat` bumps minor, `fix` bumps patch, and `type!` or a `BREAKING CHANGE:` footer bumps major.
- A commit that is not that tag is a prerelease, for example `1.3.0-dev.4`.
- `versionCode` is the number of commits on `HEAD`, so it increases with every commit. CI fetches the full history and tags before building.
- Override one build with `-Ppaperplane.versionName=` and `-Ppaperplane.versionCode=`, or the environment variables `PAPERPLANE_VERSION_NAME` and `PAPERPLANE_VERSION_CODE`.

```bash
./gradlew printVersion
```

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
4. Enable **PaperPlane**

Or use the in-app **Open notification access settings** button (Home / Settings).

Monitoring continues while the UI is closed; the system binds `NotificationListenerService` independently of `MainActivity`.

## Features

- Capture notification posted events from other apps
- Local Room history with detail view
- Filter history by package
- Enable/disable monitoring per installed app
- Send a local test notification
- Configurable retention (1 / 7 / 30 days / Forever) with WorkManager cleanup
- Ignore a repeat of the same notification within a time window (Settings → Duplicates, default 1 minute)
- Android 13+ `POST_NOTIFICATIONS` request for local notifications only
- Optional Supabase push: sign in with a Supabase user, then captured notifications are inserted for that user. Exclusion rules can skip uploads by wildcard on title, text, sub text, category, or app name

## Supabase push

In **Settings → Supabase**, enter the project URL (`https://your-project.supabase.co`), the project **publishable key** (`sb_publishable_...` from Supabase **Settings → API Keys**), and a table name (default `notifications`). Sign in with the email and password of the Supabase user that should receive the rows. Turn on **Push notifications** and save.

The publishable key only identifies the project. Inserts use that user's access token on the `Authorization` header; the publishable key is sent on `apikey` only. Each row stores their `user_id`. The password is not saved. The session stays on this device and is refreshed when it expires.

New captured notifications are inserted with the REST API. If the device is offline, they stay queued and are sent together when the connection returns. **Push existing history** sends rows that have not been delivered to the current user yet. Changing the project URL or signing in as a different user marks local history as unsent so it can be pushed again.

**Don't upload** rules skip matching notifications. Each rule is a case-insensitive wildcard on one field: title, text, sub text, category, or app name. `*` matches any text and `?` matches one character, and the pattern is compared to the whole field (`*otp*` skips a title that contains OTP). A notification is skipped when any enabled rule matches. Skipped rows stay in local history. Changing or removing a rule queues those rows again so the next upload can send the ones that no longer match.

Create the table in the Supabase SQL editor. Email sign-in must be enabled for the project.

```sql
create table notifications (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  local_id bigint,
  device_id text,
  package_name text,
  app_name text,
  title text,
  text text,
  sub_text text,
  big_text text,
  category text,
  notification_key text,
  posted_at bigint,
  received_at bigint,
  is_ongoing boolean,
  is_clearable boolean
);

alter table notifications enable row level security;

create policy "paperplane insert own rows"
on notifications for insert
to authenticated
with check (auth.uid() = user_id);

create policy "paperplane read own rows"
on notifications for select
to authenticated
using (auth.uid() = user_id);
```

Only the signed-in user can insert or read their own rows. Do not use a secret key (`sb_secret_...`) in the app.

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
app/src/main/java/com/barontech/paperplane/
  notification/   # Listener, parser, local NotificationManager
  database/        # Room entities + DAOs
  repository/      # Single UI data access layer
  ui/              # Compose: Home, History, Apps, Settings
  settings/        # Retention and Supabase preferences
  sync/            # Optional Supabase push
  work/            # Retention cleanup worker
```

Package / application id: `com.barontech.paperplane` (change in `app/build.gradle.kts` `namespace` / `applicationId`).

## Privacy

- No analytics
- Notification text is not logged
- Supabase push is off until you sign in and turn it on in Settings
- The publishable key and user session stay on the device. The password is not stored
- **Clear all history** wipes local data only. Rows already sent to Supabase stay in that project
# paper-plane

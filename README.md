# MuscleTome

[![CI](https://github.com/CJCline/MuscleTome/actions/workflows/ci.yml/badge.svg)](https://github.com/CJCline/MuscleTome/actions/workflows/ci.yml)

MuscleTome is a modern, clean, and intuitive workout tracking application for Android. It helps users manage their exercise routines, log their workouts, and track their fitness progress over time.

## 🚀 Features

*   **Exercise Library:** Comprehensive list of exercises with detailed information.
*   **Workout Routines:** Create and manage personalized workout plans.
*   **Workout Logging:** Easily record your sets, reps, and weights during your training sessions.
*   **Statistics & Tracking:** Visualize your progress and stay motivated.
*   **Material 3 UI:** A modern look and feel with support for dynamic colors and edge-to-edge display.
*   **Offline First:** All data is stored locally using Room, ensuring your logs are always accessible.

## 🛠 Tech Stack

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
*   **Dependency Injection:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room)
*   **Navigation:** [Jetpack Navigation](https://developer.android.com/guide/navigation)
*   **Architecture:** Clean Architecture (Domain, Data, and UI layers)
*   **Image Loading:** [Coil](https://coil-kt.github.io/coil/) (if applicable)

> [!NOTE]
> **Dependency versioning:** `kotlin = 2.2.10` paired with `ksp = 2.3.12` is **not** a mismatch. Since KSP 2.3.0, KSP2 is versioned independently of the Kotlin compiler (the old `<kotlin-version>-<ksp-version>` pinning scheme is gone). CI runs the full build and test suite on every push to guard this pairing.

## 🏗 Getting Started

### Prerequisites

*   Android Studio Ladybug or newer.
*   Android SDK 26 (Android 8.0) or higher.

### Installation

1.  Clone the repository:
    ```bash
    git clone https://github.com/CJCline/MuscleTome.git
    ```
2.  Open the project in Android Studio.
3.  Sync the project with Gradle files.
4.  Run the application on an emulator or a physical device.

## 🗄️ Database schema & migrations

*   The Room database (currently `version = 6` — stale: shipped code is at `version = 10` as of Phase 5; the parked README-version-sync decision means this number is not auto-updated) exports its schema JSON to `app/schemas/`, which is **committed to git**. CI fails if that JSON drifts from the entities in code.
*   `DatabaseModule` registers migrations via `addMigrations(...)` and has **no destructive fallback** — an unhandled schema change crashes loudly instead of silently wiping a user's workout history.
*   To change an entity:
    1.  Bump the `@Database` `version` (e.g. `1` → `2`).
    2.  Add the matching `Migration` (e.g. `MIGRATION_1_2`) to `MuscleTomeMigrations.ALL` with the exact SQL.
    3.  Rebuild with `./gradlew assembleDebug` and commit the regenerated `app/schemas/.../<version>.json` together with the code change.
*   Migrations are covered by an instrumented test (`MigrationTest`, runs via `./gradlew connectedDebugAndroidTest`) that seeds a realistic v1 database and walks the full chain, asserting the data transformations.

## 🧪 CI

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs `./gradlew test` and `./gradlew assembleDebug` on every push to `main` and on every pull request, then verifies that the Room schema JSON in `app/schemas/` is committed and up to date.

## 💾 Backup & restore

MuscleTome is local-first: **your data lives in this app's Room database and nowhere else**.

*   **`allowBackup` is `false`** — workout history (and any future entitlement state) must not silently ride Android's default Auto Backup to Google's cloud. There is no reviewed backup spec yet, so nothing leaves the device without you explicitly doing it.
*   **Settings → Backup & restore** exports a complete JSON document (catalog edits, routines, slots, full log history, selection history, user prefs) via the system file picker — save it anywhere: Documents, Drive, another phone.
*   **Import merges** — rows are upserted, so restoring on a new device preserves anything already there. Older supported format versions remain importable; a backup with a newer format version is rejected with a clear message rather than half-imported. The current backup format is v4.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Built with 💪 using Jetpack Compose.

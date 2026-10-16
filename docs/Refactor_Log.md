# Refactor Log — Step 1: Dependencies & Domain Contract Freeze

| | |
|---|---|
| **Date** | 2026-10-15 |
| **Branch** | `main` (uncommitted working tree on top of `406ed99`) |
| **Source** | `MAIN_READINESS_REPORT.md` §4.1–§4.2 |
| **Goal** | Add the libraries both tracks need, remove the View-system artifacts, and freeze the `:core:domain` contracts **without changing app behaviour** |

---

## 1. Version catalog (`gradle/libs.versions.toml`)

### 1.1 Removed

| Entry | Reason |
|---|---|
| `appcompat` 1.6.1 (version + `androidx-appcompat` library) | XML Views artifact. It goes against the "Compose only" invariant, and no module used it. |
| `material` 1.10.0 (version + `material` library) | Same reason (Material Components for Views). Compose uses `androidx-compose-material3`. |

The app itself was safe to remove these from, because no `build.gradle.kts` referenced either alias (checked with grep before deleting).

### 1.2 Added

| Alias | Coordinates | Version | Consumer (track) |
|---|---|---|---|
| `androidx-room-runtime` | `androidx.room:room-runtime` | 2.6.1 | `:core:database` (A) |
| `androidx-room-ktx` | `androidx.room:room-ktx` | 2.6.1 | `:core:database` (A) — `suspend` DAOs, `Flow` queries |
| `androidx-room-compiler` | `androidx.room:room-compiler` | 2.6.1 | `:core:database` via `ksp(...)` (A) |
| plugin `androidx-room` | `androidx.room` | 2.6.1 | `:core:database` (A) — schema export directory for migrations |
| `firebase-firestore` | `com.google.firebase:firebase-firestore` | from BOM 33.7.0 (→ 25.1.1) | `:core:network/remote` (B) |
| `coil-compose` | `io.coil-kt:coil-compose` | 2.7.0 | `:features:log` (B) |
| `okhttp` | `com.squareup.okhttp3:okhttp` | 4.12.0 | `:core:network/remote` Cloudinary uploader (B) |
| `osmdroid-android` | `org.osmdroid:osmdroid-android` | 6.1.18 | **Unchanged.** It was already present and verified. Its consumer is `:features:map` (A). |

All coordinates were checked against Google Maven / Maven Central (HTTP 200 on the POM). Firestore was confirmed to be managed by `firebase-bom` 33.7.0.

### 1.3 Version decisions

- **Room 2.6.1, not 2.7.x.** 2.6.1 is the latest line built for Kotlin 1.9 metadata, and it is known to work with **KSP1** (`2.0.21-1.0.28`, our current plugin). Room 2.7 moves to a KMP artifact layout and folds `room-ktx` into `room-runtime`. That's a fine upgrade later, but not needed now.
- **KSP / Room alignment.** `room-compiler` runs through the existing `ksp` plugin (`2.0.21-1.0.28`, matching Kotlin 2.0.21). No kapt is introduced. The `androidx.room` Gradle plugin was added to the catalog and declared `apply false` in the root `build.gradle.kts`, next to `ksp`, so `:core:database` only needs `alias(libs.plugins.androidx.room)` plus a `room { schemaDirectory("$projectDir/schemas") }` block.
- **`firebase-firestore`, not `firebase-firestore-ktx`.** Since BOM 32.5.0 the KTX APIs live in the main artifact, and the `-ktx` modules are deprecated (they were removed in BOM 34).
- **Coil 2.7.0, not Coil 3.** Coil 2.x is Android/Compose-only, was compiled with Kotlin 1.9, and works with the Compose BOM we use. Coil 3 is KMP, uses a new group (`io.coil-kt.coil3`) and needs a separate network artifact. It brings no benefit for this project.
- **Cloudinary through HTTP (OkHttp) instead of the Cloudinary Android SDK.** This is a deliberate deviation from the request. `cloudinary-android` (and even `cloudinary-android-core` 3.1.x) declares runtime dependencies on `androidx.appcompat:appcompat`, `com.google.android.material:material`, `legacy-support-v4` and `work-runtime`. Adding it would bring back, transitively, the exact View artifacts §1.1 removes. We only need **unsigned uploads**, which are a single multipart `POST https://api.cloudinary.com/v1_1/<cloud>/image/upload` carrying `upload_preset`. OkHttp covers that, and keeps the API secret out of the APK. If the SDK is wanted later, add `com.cloudinary:cloudinary-android-core` and justify the transitive Views dependencies in `DEV_LOG.md`.

> Catalog entries do nothing until a module declares them, so none of the new libraries is in the APK yet. Each track adds the `implementation(...)` lines to its own module. Per `CLAUDE.md`, each one must be justified in `DEV_LOG.md` when its code lands.

---

## 2. Domain contract freeze (`:core:domain`, `com.wandervox.core.domain`)

| File | Change |
|---|---|
| `model/SyncStatus.kt` | **New.** `enum class SyncStatus { PENDING, SYNCED, FAILED }` |
| `model/LogCategory.kt` | **New.** `enum class LogCategory { FOOD, TRANSPORT, LODGING, SHOPPING, EMERGENCY, GENERAL }`. The enum **name** is the persisted value in Room and Firestore. Labels stay in each feature's string resources. |
| `model/TravelLogEntry.kt` | `category: String = "General"` → `category: LogCategory = LogCategory.GENERAL`. Added `authorName: String? = null`, `updatedAt: Long = timestamp` and `syncStatus: SyncStatus = SyncStatus.PENDING`. All existing fields are kept, with the same order and defaults. |
| `repository/LogRepository.kt` | Added `fun getEntry(entryId: String): Flow<TravelLogEntry?>` and `suspend fun syncPending(): Result<Unit>`. The 5 existing methods are unchanged. |
| `repository/LocationRepository.kt` | **New.** `suspend fun getCurrentLocation(): Result<Coordinates>` |

**Purity check.** `:core:domain` is still a `java-library` + `kotlin.jvm` module. Its runtime classpath is only `kotlin-stdlib` 2.0.21 and `kotlinx-coroutines-core` 1.8.1, and there are zero `android.*` and zero Firebase imports.

---

## 3. Consumers updated so the codebase keeps compiling

| File | Change |
|---|---|
| `core/data/.../InMemoryLogRepository.kt` | Implements `getEntry` (a `map` over the in-memory list) and `syncPending` (a no-op `Result.success`, since there is no remote store). This is still the temporary binding until Track A's `OfflineFirstLogRepository` lands. |
| `features/translator/.../components/LogCategory.kt` → `LogCategoryLabels.kt` (`git mv`) | The feature-level `enum LogCategory(storageKey, labelRes)` is replaced by the extension `val LogCategory.labelRes` over the **domain** enum. This avoids two enums with the same name. |
| `features/translator/src/main/res/values/strings.xml` | Added `translator_category_general` = "General". |
| `features/translator/.../SaveToLogSheet.kt` | Imports the domain `LogCategory`. |
| `features/translator/.../TranslatorUiEvent.kt` | `OnSaveToLog.category: String` → `LogCategory`. |
| `features/translator/.../TranslatorViewModel.kt` | Passes `event.category` through. Removed the `DEFAULT_CATEGORY` constant and the blank-string fallback, because the type now rules out a blank category. |
| `features/translator/.../TranslatorScreen.kt` | Passes the enum instead of `category.storageKey`. |
| `features/translator/src/test/.../TranslatorViewModelTest.kt` | Uses `LogCategory` values, asserts the new `syncStatus == PENDING` default, and `FakeLogRepository` implements the two new methods. |

### Observable differences (intentional, minor)

1. The save sheet now shows a sixth chip, **"General"**, because the chips iterate `LogCategory.entries`. The pre-selected chip is still **Comida** (`FOOD`).
2. The persisted category value changes from `"Food"`-style keys to enum names (`"FOOD"`). **No migration is needed:** the only store today is in memory.
3. The "blank category → General" fallback test case became a typed-category test (`TRANSPORT`). Blank input is no longer representable.

---

## 4. Verification

| Command | Result |
|---|---|
| `./gradlew :core:domain:build` | ✅ BUILD SUCCESSFUL. `domain.jar` contains the 9 expected classes, including `LogCategory`, `SyncStatus` and `LocationRepository`. |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL, 0 Kotlin warnings |
| `./gradlew testDebugUnitTest` | ✅ 38/38 pass, 0 skipped |
| `./gradlew :core:domain:dependencies` | ✅ Only `kotlin-stdlib` and `kotlinx-coroutines-core` |

Not done: running on a device, `lintDebug`, and a resolution check of the new libraries inside a module. They aren't consumed yet, so they first resolve when each track adds them.

---

## 5. Not done in this step (follow-ups)

- `TravelLogEntry.userId` is **still never set** by the Translator. The plan (report §4.2 #4) is for A's `OfflineFirstLogRepository` to stamp it on save.
- Nothing implements `LocationRepository` yet. That's Track A, task A4.
- The `:core:database` namespace is still `com.wandervox.database`. The report recommends renaming it before Room code lands (Track A, task A1).
- `MAIN_READINESS_REPORT.md` §4.5 B5 still says "Cloudinary"; it should read "Cloudinary via OkHttp unsigned upload" (see §1.3).
- Nothing is committed. Suggested message: `chore: freeze log domain contracts and add Room/Firestore/Coil/OkHttp to catalog`.

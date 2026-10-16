# WanderVox — `main` Readiness Report (post `Fix/Fix_Merge`)

| | |
|---|---|
| **Branch** | `main` @ `406ed99` (Merge PR #3 `Fix/Fix_Merge`, 2026-10-15), up to date with `origin/main` |
| **Date** | 2026-10-15 |
| **Method** | Read-only source inspection plus `./gradlew assembleDebug testDebugUnitTest`. Nothing was run on a device. The only file created is this one. |
| **Audience** | Dev A (Lautaro) and Dev B (Lucas), technical sync |

---

## TL;DR

- **The merge is clean.** There is now one theme, one domain root, lint is unblocked, and `CLAUDE.md`/`DEV_LOG.md` are back. The P0/P1 items from `PROJECT_STATUS_AUDIT.md` are closed.
- **Working features:** Auth, Translator and Settings. **Placeholders:** Travel Log and Map are `UnderDevelopmentScreen` with **zero source files**.
- **The translator's "save to log" is not usable yet.** It writes to `InMemoryLogRepository`, `userId` is always `""`, and `coordinates` is always `null`. Fixing this is the first handoff between the tracks.
- **Agree on 3 things at the start of the meeting:** (1) the `:core:domain` contract changes in §4.2, (2) one shared dependencies PR for the version catalog, (3) where the Firestore remote data source lives (§4.1).

---

## 1. Branch & Build Status

| Check | Result |
|---|---|
| Current branch | `main`, up to date with `origin/main` |
| Working tree | Clean except the untracked `PROJECT_STATUS_AUDIT.md` (previous audit). Decide: commit it under `docs/` or delete it. |
| Latest merge | `406ed99` Merge PR #3 `Fix/Fix_Merge` (commits `f0f2e92`, `b239b71`, `f0be983` by Lucas) |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL (3m 5s, 395 tasks, 0 Kotlin warnings) |
| `./gradlew testDebugUnitTest` | ✅ 38/38 pass, 0 skipped. Real tests exist only in Auth (VM), Settings (VM), Translator (VM and shake detector) and `:core:ui` (contrast). Every other module has template tests only. |
| `CLAUDE.md` | Present and tracked (22 lines). It was deleted in `f0f2e92` and restored in `f0be983`. Content is intact. |
| `DEV_LOG.md` | Present and tracked (742 lines). Restored in `f0be983`, and §9 "Post-merge consolidation" documents the fix. |
| Stale branches | `Fix/Fix_Merge`, `feat/lau-voice-implementation` (local and remote) and `origin/feature/firebase-auth` are all merged and can be deleted. |

---

## 2. Consolidation Status (what the merge fixed)

| Item | Status | Evidence |
|---|---|---|
| Duplicate theme in `:core:ui` | ✅ **Resolved** | `com.wandervox.ui.theme` was deleted. The only theme left is `core/ui/.../core/ui/theme/Theme.kt`, and every theme import in the repo points to `com.wandervox.core.ui.theme`. Hard-coded colours were replaced by `MaterialTheme.colorScheme`. Poppins is bundled as TTF in `res/font`, which is offline-safe. |
| Leftover legacy refs | 🟡 Cosmetic only | `Type.kt:9` imports `com.wandervox.ui.R`. This is fine because the module namespace is still `com.wandervox.ui`. The `androidTest` template still asserts `com.wandervox.ui.test`. |
| Domain package standardization | ✅ **Resolved** | All of `:core:domain` is under `com.wandervox.core.domain.{model,repository}`. There are zero `com.wandervox.domain.*` references, and the template `MyClass.kt` was removed. `:core:domain` is still pure `java-library` with only `coroutines-core`. |
| Module namespaces | 🟡 Inconsistent, low priority | `:core:data` uses `com.wandervox.core.data`, but `:core:database` uses `com.wandervox.database`, `:core:network` uses `com.wandervox.network` and `:core:ui` uses `com.wandervox.ui`. **Recommendation:** set `:core:database` to `com.wandervox.core.database` *before* writing Room code, while it's still empty. Leave the others alone. |
| Lint | ✅ Unblocked (workaround) | All 10 Android modules disable `NullSafeMutableLiveData`, `FrequentlyChangingValue` and `RememberInComposition`, because of a Lint API mismatch with AGP 8.7.3. Remove these once AGP ≥ 8.9. |
| Location permission | 🟡 Declared, never requested | `app/AndroidManifest.xml` declares `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. **No code requests them at runtime.** Settings only reads the status and opens the system app-details page, so users have to grant location manually in system settings. The manifest comment ("requested at runtime from Settings") overstates this. |

---

## 3. Feature Readiness Matrix

| Feature | Works | Missing / Mocked |
|---|---|---|
| **Auth** (`:features:auth`, `:core:data/auth`) | Firebase email/password sign-in and sign-up; validation (email regex, password policy on register only); `currentUser` as a `callbackFlow`; Firebase errors mapped to Spanish messages (with no user enumeration); session restore through `SessionViewModel` → Home or Login; logout from Settings. 11 VM tests. | No password reset, email verification or Google sign-in. `User` only has `uid` and `email`, with no display name, which **Comunidad will need for the author label**. Error strings are hard-coded. |
| **Translator** (`:features:translator`, `:core:network`) | Offline Vosk STT (bundled ES and EN models extracted to `filesDir`); ML Kit offline translation; runtime `RECORD_AUDIO` flow; shake to swap languages; copy; save sheet with category and notes. UDF with `StateFlow`. 21 VM tests and 8 shake tests. | **Save to log goes to `InMemoryLogRepository`**, so data is lost when the process dies. **`userId` is never set**, so `getUserEntries(uid)` would never return these entries. **`coordinates = null`** is hard-coded (`TranslatorScreen.kt:169`). There is a category mismatch: the VM default is `"General"`, but `LogCategory` (5 values) has no General. Several VM messages are hard-coded English strings, while the rest of the app is in Spanish. |
| **Settings** (`:features:settings`) | Account card with email; logout with confirmation and error state; live permission status for mic and location (re-checked on resume); Terms screen; app version. 11 VM tests. | **Visual only:** the "Modo offline forzado" switch is held only in memory and nothing reads it. The "Sincronización de Bitácora" row is disabled with a "Próximamente" badge. The permission rows open system settings instead of requesting the permission. |
| **Travel Log** (`:features:log`, `:core:database`) | Nothing. `:features:log` has only template tests. `:core:database` has no source files and no Room dependency. The tab shows `UnderDevelopmentScreen`. | Everything: Room entity, DAO and DB; persistent repository; Firestore; UI (list, detail, tabs, photos). The **`LogRepository` contract already exists** and is good enough to build against: `getUserEntries`, `getCommunityEntries`, `saveEntry`, `toggleLike`, `deleteEntry`. |
| **Map** (`:features:map`) | Nothing. Template tests only. The tab shows `UnderDevelopmentScreen`. | Everything. `osmdroid-android` 6.1.18 is in the catalog but **not depended on by any module**. There is no location provider anywhere in the codebase. |

**Dependencies not in the catalog yet:** Room, Firestore, Cloudinary, WorkManager (optional) and play-services-location (optional). `appcompat` and `material` (views) are in the catalog but unused. Delete them, because they conflict with the "Compose only" invariant.

---

## 4. Task Breakdown for the Sync

### 4.1 Module ownership (no overlapping modules)

| Module / area | Owner | Notes |
|---|---|---|
| `:core:database` | **A** | Room |
| `:core:data` | **A** | Offline-first `LogRepository` impl and DI binding swap. Also replaces `InMemoryLogRepository`. |
| `:features:map` | **A** | OSMDroid |
| Location provider (domain interface + impl) | **A** | Hardware, which is Dev A scope per `CLAUDE.md`. Also wires `coordinates` into the Translator, which A owns. |
| `:features:log` | **B** | UI: "Mis viajes" / "Comunidad" tabs, detail, photo picking |
| `:core:network` → **new** package `com.wandervox.core.network.remote` | **B** | Firestore and Cloudinary remote data source. A's existing Vosk/ML Kit code in this module is finished, so B only adds new files in this package. |
| `:core:domain` | **Shared, PR-reviewed by both** | Contract only. Freeze it after the changes in §4.2. |
| `gradle/libs.versions.toml` | **Shared, one PR** | Add Room, Firestore, Cloudinary and OSMDroid usage in a single upfront PR to avoid merge conflicts. |
| `:app` (`MainScaffold`) | Each owner wires their own tab | Use the existing slot pattern (`translatorContent`): add `logContent` and `mapContent` lambdas. Each is a one-line change, so do them in separate PRs. |

> **Why Firestore goes in `:core:network` and not `:core:data`:** it keeps the two tracks in separate modules, and `:core:data` already depends on `:core:network`. The trade-off is that FirebaseAuth stays in `:core:data` (`FirebaseModule`). That's acceptable for now. Optionally move it later so all Firebase code sits together.

### 4.2 Contracts to agree on in the meeting (`:core:domain`)

These are small and should land as **one PR, merged before either track starts**:

1. **`TravelLogEntry`** — add sync and authorship metadata:
   ```kotlin
   enum class SyncStatus { PENDING, SYNCED, FAILED }
   data class TravelLogEntry(
       /* existing fields… */
       val authorName: String? = null,      // Comunidad label (B needs it, A persists it)
       val updatedAt: Long = timestamp,     // conflict resolution: last-write-wins
       val syncStatus: SyncStatus = SyncStatus.PENDING,
   )
   ```
   Keep `localPhotoPath` (which A persists) and `remotePhotoUrl` (which B fills after the Cloudinary upload) as they are now.
2. **`LogCategory` moves to the domain** as a plain enum with storage keys (`FOOD`, `TRANSPORT`, `LODGING`, `SHOPPING`, `EMERGENCY` plus **`GENERAL`**). Labels stay as string resources in the UI. Both the Translator and the Log UI use it, and filters in Log need it.
3. **`LogRepository`** — add:
   ```kotlin
   fun getEntry(entryId: String): Flow<TravelLogEntry?>             // detail screen (B)
   fun getEntriesWithLocation(userId: String): Flow<List<TravelLogEntry>> // map (A); or filter in VM
   suspend fun syncPending(): Result<Unit>                            // manual "Sincronizar" in Settings
   ```
4. **`userId` is stamped by the repository**, not by callers. `OfflineFirstLogRepository.saveEntry` reads the uid from `AuthRepository.currentUser.first()`, and fails with `Result.failure` if nobody is signed in. This fixes the Translator bug without touching any ViewModel.
5. **`LocationRepository`** (new, owned by A): `suspend fun getCurrentLocation(): Result<Coordinates>`.

### 4.3 Remote data source contract (B implements, A consumes)

Declared and implemented by B in `:core:network/remote`, and bound via `@Binds`:

```kotlin
interface LogRemoteDataSource {
    suspend fun upsert(entry: TravelLogEntry): Result<Unit>          // Firestore doc id == entry.id
    suspend fun delete(entryId: String): Result<Unit>
    fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>> // pull to Room (reinstall / new device)
    fun observePublicEntries(): Flow<List<TravelLogEntry>>             // Comunidad (remote-only + Firestore cache)
    suspend fun toggleLike(entryId: String, userId: String): Result<Unit> // arrayUnion / arrayRemove
}
interface ImageUploader {
    suspend fun upload(localPath: String): Result<String>  // returns remotePhotoUrl
}
```

**Data flow:**

```
Translator / Log UI ──saveEntry──▶ OfflineFirstLogRepository (A, :core:data)
                                     1. Room insert, syncStatus=PENDING   ← source of truth for "Mis viajes"
                                     2. ImageUploader.upload() if photo   (B)
                                     3. LogRemoteDataSource.upsert()      (B)
                                     4. Room update syncStatus=SYNCED / FAILED
"Mis viajes"  ◀── Room Flow (A)
"Comunidad"   ◀── LogRemoteDataSource.observePublicEntries() (B), passed through by A's repository
Map           ◀── Room Flow filtered to coordinates != null (A)
```

- **Firestore's offline persistence already queues writes while offline**, so `upsert` is safe without connectivity. The only thing that really needs a retry is the Cloudinary upload. `syncPending()` re-runs steps 2–4 for `PENDING`/`FAILED` rows. Treat WorkManager as a stretch goal, not a requirement.
- **Firestore document schema** (B owns it, but both should agree on it): `logs/{entryId}`, with fields mirroring `TravelLogEntry` minus `localPhotoPath` and `syncStatus`. Write security rules so `userId == request.auth.uid` is required for writes, and public reads are allowed only where `isPublic == true`.
- **Cloudinary:** use an **unsigned upload preset only**. Do not put the API secret in the APK.

### 4.4 Track 1 — Dev A (Lautaro)

| # | Task | Done when |
|---|---|---|
| A1 | Fix `:core:database` namespace; add Room (KSP) and pin a version compatible with Kotlin 2.0.21 / KSP 2.0.21-1.0.28 | Module compiles |
| A2 | `TravelLogEntity` (coordinates flattened to `lat`/`lng`, `likes` stored via a TypeConverter or a separate table, plus `syncStatus` and `updatedAt`), `TravelLogDao`, `WanderVoxDatabase`, Hilt `DatabaseModule`, and mappers between entity and domain | DAO tests with in-memory Room (Robolectric or androidTest) |
| A3 | `OfflineFirstLogRepository` in `:core:data` that implements §4.2 and §4.3 against a **fake `LogRemoteDataSource`** until B delivers. Swap the binding in `DataModule` and delete `InMemoryLogRepository` | Translator saves survive an app restart; unit tests cover the sync state machine |
| A4 | `LocationRepository` impl (start with `LocationManager` to avoid a new library, or use play-services-location and justify it in DEV_LOG); runtime location permission request; pass `coordinates` from the Translator save sheet | Saved entries have coordinates when permission is granted, and still save without them when it isn't |
| A5 | `:features:map`: OSMDroid `MapView` inside `AndroidView`; set `Configuration.userAgentValue` (required by the OSM tile policy); enable the tile cache for offline use; markers from `getUserEntries` that have coordinates; tapping a marker shows a preview card | Map tab replaces the placeholder |
| A6 | DEV_LOG: justify Room, OSMDroid and the location approach | — |

### 4.5 Track 2 — Dev B (Lucas)

| # | Task | Done when |
|---|---|---|
| B1 | `:features:log` scaffolding: `LogViewModel` with `LogUiState`, plus "Mis viajes" / "Comunidad" tabs (`PrimaryTabRow`), built against **`InMemoryLogRepository` until A3 lands**, so B isn't blocked | Log tab replaces the placeholder |
| B2 | Entry list and detail: category chips/filter, likes, delete with confirmation, a "pendiente de sincronizar" badge from `syncStatus`, empty and offline states | VM tests |
| B3 | Image handling: Photo Picker (`PickVisualMedia`, no storage permission needed), copy the image into `filesDir` and write the path to `localPhotoPath`, thumbnail display (pick and justify an image loader such as Coil) | Photo survives app restart and works offline |
| B4 | `LogRemoteDataSource` with Firestore (`firebase-firestore`, offline persistence on), mapping to and from the Firestore document, and security rules | Integration test against the emulator, or manual verification |
| B5 | `ImageUploader` with Cloudinary (unsigned preset) | `remotePhotoUrl` filled after sync |
| B6 | Enable the Settings "Sincronización de Bitácora" row to call `syncPending()`, and wire the "Modo offline forzado" switch to something real, or remove it | — |
| B7 | DEV_LOG: justify Firestore, Cloudinary and the image loader | — |

### 4.6 Handoff points & order

```
Day 0  ── Joint PR: §4.2 domain contract + catalog deps PR ──────────────┐
          A: A1→A2→A3 (fake remote)              B: B1→B2 (in-memory repo)
          A: A4, A5                              B: B3, B4, B5
Handoff 1 ─ A3 merged  → B switches nothing (same LogRepository; DI swap is A's)
Handoff 2 ─ B4/B5 merged → A replaces fake remote binding with B's impl in A3
Handoff 3 ─ Map marker tap → open Log detail: nav route "log/{entryId}" owned by B, called by A
```

**Risks to raise in the meeting:**
- **Domain churn after the freeze.** Any further change to `TravelLogEntry` touches A's entity and mappers *and* B's Firestore mapping. Agree that it requires a short heads-up and a PR that both people review.
- **Comunidad needs `authorName`, which neither `User` nor Firebase signup provides today.** Decide whether to derive it from the email prefix or add a display-name field at registration (Auth is B's feature).
- **AGP 8.7.3 is ageing** (see the lint workarounds). Don't upgrade it mid-sprint. Plan it as one dedicated PR after Log and Map are merged.
- **Repo size:** the Vosk models (~78 MiB) are committed as plain files. Leave them as is for now, but don't add more binaries (for example map tiles) to git.

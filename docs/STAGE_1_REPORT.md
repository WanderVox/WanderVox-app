# Stage 1 Report: Room Database Foundation (`:core:database`)

**Track:** Dev A · **Branch:** `feat/room-implementation` · **Date:** 2026-10-19

## 1. Summary

`:core:database` used to be an empty module. It now contains the Room persistence layer for Travel Log
entries: an entity, a reactive DAO, type converters, the `WanderVoxDatabase` class, a Hilt module and
entity↔domain mappers. Nothing uses it yet. Stage 2 wires it into `OfflineFirstLogRepository`.

## 2. Files

| File | Purpose |
|---|---|
| `core/database/build.gradle.kts` | Applies `androidx.room` plugin; adds `room-runtime`, `room-ktx`, `ksp(room-compiler)`; schema dir + `room.generateKotlin` |
| `entity/TravelLogEntity.kt` | `travel_logs` table, indices on `userId`, `isPublic`, `syncStatus` |
| `converter/Converters.kt` | `List<String>` likes, `LogCategory`, `SyncStatus` |
| `dao/TravelLogDao.kt` | Reactive queries + suspend writes |
| `WanderVoxDatabase.kt` | `@Database(version = 1, exportSchema = true)` |
| `di/DatabaseModule.kt` | `@InstallIn(SingletonComponent)`, `@Singleton` DB + DAO |
| `mapper/TravelLogMapper.kt` | `TravelLogEntity.asDomain()` / `TravelLogEntry.asEntity()` |
| `schemas/com.wandervox.core.database.WanderVoxDatabase/1.json` | Generated schema, to be committed |
| `src/test/.../ConvertersTest.kt`, `TravelLogMapperTest.kt` | 11 JVM unit tests |

All source paths are under `core/database/src/main/java/com/wandervox/core/database/`.

## 3. Entity design

`TravelLogEntity` has one column for each `TravelLogEntry` field. There are two differences:

- **Coordinates are flattened** into `latitude: Double?` and `longitude: Double?`. The mapper
  rebuilds `Coordinates` only when **both** values are non-null. If only one is set, the entry is
  treated as having no location, so a marker is never drawn at a half-null position.
- **`id: String` is the primary key.** It is not auto-generated. IDs are created on the client (UUIDs), so the
  same key works in Room and as the Firestore document ID. Upserts are then idempotent across
  both stores, which `syncPending()` relies on.

Indices match the DAO's `WHERE` clauses: `userId` (My Log, Map), `isPublic` (Community) and
`syncStatus` (retry queue).

## 4. DAO

| Method | Kind | Notes |
|---|---|---|
| `observeUserEntries(userId)` | `Flow<List<…>>` | `ORDER BY timestamp DESC` |
| `observePublicEntries()` | `Flow<List<…>>` | `isPublic = 1`, newest first |
| `getEntryById(id)` | `Flow<Entity?>` | Emits `null` when the entry is missing or deleted, as `LogRepository.getEntry` requires |
| `findById(id)` | `suspend` | **Added:** one-shot read for `toggleLike` (read-modify-write) |
| `getEntriesBySyncStatus(statuses)` | `suspend` | **Added:** the `PENDING`/`FAILED` queue for `syncPending()`, oldest first |
| `upsert(entity)` / `upsertAll(list)` | `suspend` | `@Upsert` (Room 2.5+). `upsertAll` is there for pulling remote data later |
| `delete(id)` | `suspend` | Deletes by key, matching `LogRepository.deleteEntry(entryId)` |
| `updateSyncStatus(id, status)` | `suspend` | Leaves `updatedAt` unchanged on purpose. A sync-state change is not a content edit, so it must not affect last-write-wins |

`findById` and `getEntriesBySyncStatus` were not in the Stage 1 brief. I added them now because
Stage 2's `toggleLike` and `syncPending` need them, and keeping every SQL query in this module means
`:core:data` contains no SQL.

## 5. Architectural decisions

1. **Likes are stored with the ASCII Unit Separator (`\u001F`), not JSON.** Likes are Firebase Auth UIDs,
   which are alphanumeric, so the control character can't appear inside one. This keeps the converter
   free of dependencies: no Gson, Moshi or kotlinx-serialization. It is also testable on the plain JVM.
   `org.json` is stubbed in local unit tests. An empty list is stored as `""` and reads back as `emptyList()`.
2. **Explicit enum converters with fallbacks.** Room 2.6 has built-in enum support, but it throws on
   names it doesn't recognize. Our converters store `enum.name`, which is the same value Firestore uses
   (DEV_LOG §10.3). Unknown names fall back to `LogCategory.GENERAL`, and to `SyncStatus.PENDING` so
   the entry gets retried rather than lost. This covers a downgrade, or a row written by a newer schema.
3. **The mappers live in `:core:database`, not `:core:data`.** The entity is internal to this module
   and the module already depends on `:core:domain`. The mapping sits next to the schema, so a field
   change touches one module. This follows the Now in Android `asExternalModel()` pattern.
4. **`@Provides` in `DatabaseModule`.** CLAUDE.md prefers `@Binds`, but `@Binds` needs a concrete
   class to bind, and Room generates the DB/DAO implementations. `@Provides` is the only option here.
   The DB is `@Singleton`. The DAO is unscoped because Room already caches it inside the database.
5. **Schema export is on** (`room { schemaDirectory("$projectDir/schemas") }`). The v1 JSON should be
   committed so that a future `version = 2` can use `AutoMigration` or a tested `Migration`.
   There is no `fallbackToDestructiveMigration()`, because silently wiping an offline-first store would
   lose unsynced entries.
6. **`room.generateKotlin = true`.** Room 2.6 + KSP1 generates Kotlin instead of Java. This
   matches the toolchain (Kotlin 2.0.21, KSP 2.0.21-1.0.28, no kapt), as decided in DEV_LOG §10.1.
7. **Domain purity holds.** Only `:core:database` imports `androidx.room`, and `:core:domain` is unchanged.

## 6. Build & test results

Command: `./gradlew assembleDebug testDebugUnitTest`

```
BUILD SUCCESSFUL in 1m 40s
400 actionable tasks: 57 executed, 343 up-to-date
```

- **Unit tests:** 49/49 pass, 0 failures, 0 errors. That is 38 existing tests plus 11 new ones in
  `:core:database`:
  - `ConvertersTest` (7): likes round-trip for many, empty and single values; category and sync-status
    round-trips over all values; fallbacks for unknown values.
  - `TravelLogMapperTest` (4): round-trip keeps every field; coordinates are flattened; null coordinates;
    a half-null pair maps to no location.
- **Kotlin warnings:** none from `:core:database` (checked with a forced recompile).
- **Room schema:** `core/database/schemas/com.wandervox.core.database.WanderVoxDatabase/1.json` was generated.
- **Hilt graph:** `:app:hiltJavaCompileDebug` passes with `DatabaseModule` on the classpath through
  `:core:data → :core:database`.

## 7. Not verified / known gaps

- **No DAO tests yet.** Queries are checked at compile time by Room's processor, but they haven't
  been run against SQLite. An in-memory DAO test needs Robolectric or an instrumented `androidTest`
  (`room-testing`). Neither is set up yet. I suggest adding one alongside Stage 2.
- **Not run on a device or emulator.** `lintDebug` was not run.

## 8. Blockers for Stage 2

- **`ImageUploader` and `LogRemoteDataSource` are not in the codebase.** I searched every local and remote
  branch (`git log --all -S LogRemoteDataSource`), and they only appear in `DEV_LOG.md`.
  `:core:network` has no `remote` package. Stage 2 needs Dev B's branch to be pushed or merged first,
  or else the agreed interfaces stubbed here.
- **Branch name:** the brief says `feat/room-persistence-and-map`, but that branch doesn't exist. This
  work is on the current branch, `feat/room-implementation`.

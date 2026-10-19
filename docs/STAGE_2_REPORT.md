# Stage 2 Report: Offline-First Repository & `userId` Fix (`:core:data`)

**Track:** Dev A · **Branch:** `feat/room-implementation` · **Date:** 2026-10-19

## 1. Summary

`LogRepository` is now backed by Room through `OfflineFirstLogRepository`. Writes go to Room first,
which makes the local copy the source of truth. A background sync then uploads the photo (Cloudinary)
and the document (Firestore). Dev B's real implementations are not merged yet, so their contracts are
stubbed in `:core:network` behind placeholder bindings. `InMemoryLogRepository` has been deleted.
The Translator's `userId` bug is fixed: every saved entry now carries the signed-in user's `uid`.

## 2. Files

| File | Change |
|---|---|
| `core/network/.../remote/log/LogRemoteDataSource.kt` | **New.** Contract stub, as given in the brief, plus KDoc |
| `core/network/.../remote/image/ImageUploader.kt` | **New.** Contract stub, as given in the brief |
| `core/network/.../remote/placeholder/UnavailableRemote.kt` | **New, temporary.** Implementations where every call fails |
| `core/network/.../remote/placeholder/RemotePlaceholderModule.kt` | **New, temporary.** `@Binds` for the two placeholders |
| `core/data/.../repository/OfflineFirstLogRepository.kt` | **New.** The repository |
| `core/data/.../di/CoroutineScopeModule.kt` | **New.** `@ApplicationScope CoroutineScope` |
| `core/data/.../util/ResultExtensions.kt` | **New.** `runSuspendCatching` (same helper as in `:core:network`, where it is `internal`) |
| `core/data/.../di/DataModule.kt` | `@Binds` now points to `OfflineFirstLogRepository` |
| `core/data/.../repository/InMemoryLogRepository.kt` | **Deleted** |
| `core/database/.../dao/TravelLogDao.kt` | **Added** `updateSyncResult(...)`, a compare-and-set update (§4.3) |
| `core/data/build.gradle.kts` | `testImplementation(kotlinx-coroutines-test)` |
| `core/data/src/test/.../repository/Fakes.kt`, `OfflineFirstLogRepositoryTest.kt` | **New.** 25 tests |

## 3. Behaviour

| Method | Implementation |
|---|---|
| `getUserEntries(userId)` | Room `observeUserEntries`, mapped to the domain model |
| `getEntry(id)` | Room `getEntryById`. Emits `null` when the entry is missing |
| `getCommunityEntries()` | `LogRemoteDataSource.observePublicEntries()`. **If the remote flow errors, it falls back to the public entries in Room** (CLAUDE.md invariant 5) |
| `saveEntry(entry)` | Stamps the user, writes to Room as `PENDING`, **returns**, then syncs in the background (§4.1) |
| `syncPending()` | Retries every `PENDING`/`FAILED` row one after another. If any still fail, returns `Result.failure(SyncIncompleteException(failed, total))` |
| `toggleLike(id, uid)` | Optimistic update in Room when the entry is there, then `setLiked`. Rolled back if `setLiked` fails |
| `deleteEntry(id)` | Fails if the entry doesn't exist. Otherwise deletes from Room, then deletes remotely in the background |

### `userId` fix
`saveEntry` reads `authRepository.currentUser.first()`. When someone is signed in, the entry is copied with
`userId = uid` and `authorName = email ?: "WanderVox traveler"`. When no one is signed in, the entry keeps
the values it arrived with. Entries saved by the Translator therefore show up in `getUserEntries(uid)`,
which is covered by the test `saveEntry_entryIsVisibleInUserLog`.

## 4. Architectural decisions

### 4.1 Sync runs on an application scope, not inside `saveEntry`
The brief describes saving to Room, uploading, upserting, then marking the result. Those steps keep that
order. The difference is that `saveEntry` **returns once the Room write commits**. The rest runs in
`appScope.launch { … }`.

**Why:** with Firestore offline persistence, `set().await()` only completes when the server
acknowledges the write. If `saveEntry` waited for it, the Translator's save spinner would hang for the
whole time the device is offline, which defeats offline-first. The app scope (`SupervisorJob` +
`Dispatchers.Default`, `@Singleton`) also keeps the upload going if the user leaves the screen.
`TranslatorViewModel` needed no changes.

### 4.2 Each sync has a 30 s timeout
`withTimeoutOrNull(30_000)` wraps the upload and the upsert. When it fires, the row is marked `FAILED`
and `syncPending()` retries it later. Cancelling `await()` does **not** cancel the write Firestore has
queued, so the document can still arrive later while the row says `FAILED`. The next retry re-upserts
it, which is harmless because upserts are idempotent on `id`.
`withTimeoutOrNull` sits *outside* `runSuspendCatching` on purpose. The timeout is a
`CancellationException`, and the helper rethrows those so structured concurrency keeps working.

### 4.3 Compare-and-set on `updatedAt`
Without this, the following race loses data. The user saves, the upload starts, the user edits the
entry, the first upload finishes, and it writes `SYNCED` over the newer edit. The fix is
`updateSyncResult(id, updatedAt, status, remotePhotoUrl)`, which only updates the row if `updatedAt`
still matches the version that was uploaded. `saveEntry` makes `updatedAt` strictly increase for each
entry (`max(now, previous + 1)`), so every edit is detected even when two saves land in the same
millisecond.

### 4.4 No double uploads
An entry ID is added to a `ConcurrentHashMap` key set while it syncs, so `saveEntry` and `syncPending`
never upload the same entry at the same time. If an edit arrives while its entry is syncing, the edit's
own sync is skipped. When the first sync then finds that its compare-and-set changed 0 rows, it reads
the row again and syncs the newer version. Covered by `editDuringSync_isNotOverwritten_andSyncsNewestVersion`.

### 4.5 The photo URL is kept on partial failure
When the image upload succeeds but the upsert fails, the row is marked `FAILED` **with**
`remotePhotoUrl` set. The retry then skips Cloudinary and doesn't upload a duplicate.

### 4.6 Likes are not queued
`toggleLike` changes `likes` in Room without touching `updatedAt` or `syncStatus`, because a like is
not a content edit. If `setLiked` fails, the local change is rolled back and the error is returned,
since there is no offline queue for likes yet. Community entries written by other users are not in
Room, so their current likes are read from `observePublicEntries().first()`.

### 4.7 Placeholder remote bindings
Hilt fails to compile when an interface has no binding, so `RemotePlaceholderModule` binds
implementations where every call fails (`RemoteUnavailableException`). Until Dev B's branch lands,
the app behaves like this:
- every entry stays `FAILED` in Room. Nothing is lost, and everything uploads on the first
  `syncPending()` after the real bindings are in place.
- Community shows the user's own public entries through the fallback in §3, which matches what
  `InMemoryLogRepository` did before.
- likes fail and are rolled back.

## 5. Contract requirements for Dev B

These are written in the KDoc of `LogRemoteDataSource`. They need Dev B's agreement before the
two branches merge:

1. **`upsert` must not write `likes`.** Use `SetOptions.merge()` and leave out the `likes` field, or
   map the field explicitly. A queued local row may hold a stale `likes` list, and a full overwrite
   would delete likes from other users. `likes` should only change through `setLiked`
   (`FieldValue.arrayUnion` / `arrayRemove`).
2. `upsert` is idempotent on `entry.id`, which is used as the Firestore document ID.
3. `upsert` receives `syncStatus = SYNCED`. The remote mapper can drop that field.
4. **Merge step:** delete the `remote/placeholder/` package and add Dev B's `@Binds`. If both modules
   are present, Hilt reports a duplicate binding.

## 6. Build & test results

Command: `./gradlew clean assembleDebug testDebugUnitTest`

```
BUILD SUCCESSFUL in 1m 1s
410 actionable tasks: 374 executed, 36 up-to-date
```

- **Unit tests:** 74/74 pass, with 0 failures and 0 errors. That is 49 existing tests plus 25 new ones in
  `OfflineFirstLogRepositoryTest`:
  - userId fix (5): stamps uid and email; default name when the user has no email; signed-out keeps the
    owner; entry is visible in the user log; blank ID gets a generated one.
  - sync (12): returns before the sync with `PENDING`; marks `SYNCED`; photo upload then remote URL;
    skips an upload already done; upload failure → `FAILED` with no upsert; exception → `FAILED`;
    hang → timeout → `FAILED`; URL kept so the retry doesn't upload again; edit during sync;
    `updatedAt` bump; `syncPending` picks `PENDING`/`FAILED` and skips `SYNCED`; `syncPending` reports failures.
  - reads (3): remote Community feed; fallback to local when the remote errors; `getEntry` null.
  - likes & delete (5): optimistic like; rollback; remote likes for entries not in Room; delete
    local and remote; deleting a missing entry fails.
- **Kotlin warnings:** none in the clean build.
- **Hilt graph:** `:app:hiltJavaCompileDebug` passes with the DAO, the placeholders and `@ApplicationScope`.
- **Room schema:** no change. `1.json` is unchanged because the new DAO method is a query, not a schema change.

The tests use hand-written fakes, including a `FakeTravelLogDao` that copies the semantics of the
Room queries. Neither mockk nor Turbine was added.

## 7. Known gaps / follow-ups

| Gap | Impact | Suggested fix |
|---|---|---|
| **Deletes have no tombstone** | If the remote delete fails (for example while offline), the Firestore document is orphaned and stays visible in Community | Add a `PENDING_DELETE` state, or a tombstone table. Either one changes the frozen `SyncStatus`, so it needs a PR reviewed by both developers |
| **Nothing calls `syncPending()` automatically** | `FAILED` rows only retry when something calls it (Settings) | WorkManager periodic job with `NetworkType.CONNECTED`, or a call on app start |
| **No pull from remote into Room** | A second device won't see the user's entries in My Log; `getEntry` returns null for Community entries by other users | Merge `observeUserEntries` from the remote into Room using last-write-wins on `updatedAt` |
| **`authorName` is the email address** | **Privacy:** public Community posts show the author's email | Use a display name from the auth profile, or the part of the email before `@` |
| Missing local photo file | The upload fails on every retry, and the row stays `FAILED` | Detect `FileNotFoundException` and clear `localPhotoPath` |
| No DAO integration test | The SQL is only checked at compile time | Robolectric or `androidTest` with `room-testing` |
| Not run on a device | — | Manual test once Dev B's branch lands |

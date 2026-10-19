# Final Polish Report: Sync & Map Wiring (Track A)

**Branch:** `feat/sync-n-polish` (it already existed; the brief named it `feat/sync-and-polish`) · **Base:** `main` @ `dceac13` · **Date:** 2026-10-19

## Result

`./gradlew clean assembleDebug testDebugUnitTest`: **BUILD SUCCESSFUL**, **136/136 tests pass**
(0 skipped, 0 failed), 0 Kotlin warnings. The baseline was 113. Each stage was built and tested
before the next one started: 120 → 132 → 136 → 136.

| Module | Tests | | Module | Tests |
|---|---|---|---|---|
| core/data | 54 (+22) | | features/auth | 9 |
| features/log | 18 | | features/map | 7 |
| features/translator | 17 | | features/settings | 4 |
| core/database | 13 (+1) | | core/ui | 3 |
| core/network | 10 | | app | 1 |

Nothing has been run on a device.

## Stage 1: Tombstone deletes

- `SyncStatus.PENDING_DELETE` was added. **This changes the frozen domain (DEV_LOG §10.3), so the PR
  needs Dev B's review.** The one exhaustive `when` in B's `LogEntryCard` now groups
  `PENDING_DELETE` with `PENDING`. That branch is never displayed.
- `deleteEntry` turns the row into a tombstone (`PENDING_DELETE`, `updatedAt` bumped) and tries the
  remote delete in the background. The row is only physically removed once Firestore confirms
  (`deleteTombstone`, a compare-and-set on `updatedAt`). `syncPending()` retries tombstones.
- **Tombstones are hidden at the DAO level.** `observeUserEntries`, `observePublicEntries` and
  `getEntryById` filter out `PENDING_DELETE`, so the Log, the Map and the detail lookups need no
  changes. `deleteEntry` and `toggleLike` on a tombstone return "not found".
- **Delete during upload:** deletes use the same in-flight guard and compare-and-set as uploads. If an
  entry is deleted while its upload is running, the finished upload notices the row changed and runs
  the remote delete afterwards. The final remote state is "deleted" (tested:
  `upsert:e1` → `delete:e1`).
- **No Room migration:** the `syncStatus` column stores enum names as TEXT, and `schemas/…/1.json` is
  unchanged. The SQL literals (`'PENDING_DELETE'`, `'SYNCED'`) are constants on `TravelLogEntity`,
  and `ConvertersTest` checks that they match the stored enum names.

## Stage 2: Bidirectional sync

- `OfflineFirstLogRepository.startUserSync(uid)` collects `remote.observeUserEntries(uid)` and
  merges every emission into Room:
  - **Not in Room yet:** inserted as `SYNCED` with `localPhotoPath = null` (`insertIfAbsent`, so a row
    created locally at the same moment wins).
  - **Local row is `PENDING`/`FAILED`/`PENDING_DELETE`:** never touched.
  - **Remote is newer (`updatedAt`):** `updateFromRemote`, an atomic compare-and-set that applies only
    if the row is still `SYNCED` and has the `updatedAt` that was compared. It **never writes
    `localPhotoPath` or `syncStatus`**, so a cached photo is kept and a local edit made during the
    merge is not overwritten.
  - **Same `updatedAt` but different `likes` or `remotePhotoUrl`:** refreshed too. Other users' likes
    go through `setLiked`, which doesn't bump `updatedAt`, so a strict "newer" check would never
    show them.
  - **Remote deletions are not propagated.** On a first launch with no network, a listener served from
    an empty cache emits `[]`, and treating that as "everything was deleted" would wipe local data.
  - Ids whose remote delete was confirmed in this session are skipped. Otherwise a snapshot taken
    before the delete could put the row back.
- **Beyond the brief, so `startUserSync` is actually used:** `LogSyncCoordinator`, started in
  `WanderVoxApp.onCreate()`. For every signed-in user it runs `syncPending()` (in parallel) and
  `startUserSync(uid)`. Sign-out or switching user cancels both (`collectLatest`). This also closes
  the audit gap where nothing ever called `syncPending()`. It doesn't run on a schedule or when
  connectivity returns; that would still need WorkManager.

## Stage 3: Upload resiliency

- The single 30 s limit is split in two: **120 s for the Cloudinary upload** and 30 s for the
  Firestore write. The write limit is only a backstop, because B's data source already gives up after
  its own 15 s wait for server confirmation. Both go through one helper, `withRemoteTimeout`, which
  turns an exception or a timeout into `Result.failure`.
- **Missing photo file** (the uploader reports `FileNotFoundException`): `localPhotoPath` is set to
  null, nothing is marked `FAILED` because of the photo, and the entry is still upserted without it.
  The final status comes from that upsert. Success gives `SYNCED`. Failure gives `FAILED` with the
  path already cleared, so a retry doesn't try the photo again. This replaces the old endless loop.

## Stage 4: Map marker click

- `MapScreen(onMarkerClick: ((entryId: String) -> Unit)? = null)` → `MapContent` → `OsmMapView`. Each
  `Marker` gets `setOnMarkerClickListener { onMarkerClick(entry.id); true }`, and returning `true`
  consumes the tap, so the default bubble doesn't open.
- **The callback is nullable on purpose.** The Log detail route doesn't exist yet. If the tap were
  always consumed, markers would do nothing. With no handler (today's `MainScaffold`), OSMDroid's
  bubble still works. To connect it: `MapScreen(onMarkerClick = { id -> navController.navigate(…) })`.
- `MapUiState` is unchanged. A click is an event, not state, so it travels as a callback parameter.
- No automated UI test. `OsmMapView` would need an instrumented test.

## Test additions (+23)

- **Tombstones (7):** hidden immediately; removed once confirmed; offline then `syncPending`;
  `syncPending` reports a failure that is still failing; delete during upload; deleting twice fails;
  liking a tombstone fails.
- **Pull (8):** fresh install inserts; newer remote keeps `localPhotoPath`; older remote ignored;
  `PENDING`/`FAILED`/`PENDING_DELETE` never overwritten; new likes at the same version; later remote
  changes applied; no resurrection; listener error returns without throwing.
- **Coordinator (4):** nothing while signed out; sign-in pulls and retries; sign-out stops; switching
  user.
- **Upload (4):** a 90 s upload succeeds; over 120 s → `FAILED` with no upsert; missing file → `SYNCED`
  without a photo; missing file plus a failed upsert → the retry skips the photo.
- **Database (1):** the SQL status constants match the enum names. One older test (delete removes
  immediately) was replaced by the tombstone tests.

## Open items

| Item | Owner |
|---|---|
| Review the `SyncStatus` change and the one-line `LogEntryCard` edit (frozen contract) | B |
| Log detail route, then pass `onMarkerClick` in `MainScaffold` | B, then A |
| Propagate remote deletions safely (needs `isFromCache` from the data source) | A + B |
| Periodic or on-reconnect `syncPending()` (WorkManager) | A |
| Room DAO tests against SQLite (Robolectric/instrumented); the new SQL is checked only at compile time | A |
| Check on a device: fresh-install download, offline delete, then reconnect | A |

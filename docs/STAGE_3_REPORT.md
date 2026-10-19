# Stage 3 Report: Location Provider & Translator Geotagging

**Track:** Dev A · **Branch:** `feat/room-implementation` · **Date:** 2026-10-19

## 1. Summary

`LocationRepository` (frozen in DEV_LOG §10.3) now has a platform implementation,
`LocationRepositoryImpl`. It is built on `LocationManager` + `LocationManagerCompat`, so no Google Play
services are needed. The Translator asks for location permission when the user confirms the save sheet,
and the ViewModel attaches the current coordinates to the entry before calling `LogRepository.saveEntry`.
Location is **best effort**: if the permission is denied or there is no fix, the entry is still saved,
just without coordinates.

## 2. Files

| File | Change |
|---|---|
| `core/data/.../location/LocationRepositoryImpl.kt` | **New.** Implementation, `LocationPermissionException`, `LocationUnavailableException` |
| `core/data/.../di/DataModule.kt` | `@Binds LocationRepositoryImpl → LocationRepository` (unscoped) |
| `core/data/build.gradle.kts` | Added `androidx.core.ktx` (`LocationManagerCompat`, `ContextCompat`) |
| `features/translator/.../TranslatorUiEvent.kt` | `OnSaveToLog` **drops `coordinates`** (§4.2) |
| `features/translator/.../TranslatorViewModel.kt` | Injects `LocationRepository`; geotags inside the save coroutine |
| `features/translator/.../TranslatorScreen.kt` | `RequestMultiplePermissions` launcher, pending-save state, `hasLocationPermission()` |
| `core/data/src/test/.../location/LocationProviderSelectionTest.kt` | **New.** 6 tests |
| `features/translator/src/test/.../TranslatorViewModelTest.kt` | New constructor argument, a fake location repo, and 1 new test |
| `app/src/main/AndroidManifest.xml` | **No change.** `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` were already declared on Day 0 |

## 3. `LocationRepositoryImpl`

```
permission? ── none ──▶ failure(LocationPermissionException)
     │
last-known fix (freshest across enabled providers) ≤ 2 min old? ──▶ return it
     │ no
pick provider: fused > network > gps (gps only with FINE); passive never
     │
LocationManagerCompat.getCurrentLocation(…)  ⏱ 10 s
     │
fresh fix ?: last-known fix of any age ?: failure(LocationUnavailableException)
```

- **Why `LocationManager` and not `play-services-location`:** this follows the brief. It adds no
  dependency and works on devices without GMS. `LocationManagerCompat.getCurrentLocation` uses the
  platform API on 30+ and falls back to `requestSingleUpdate` on 27–29, so one code path covers every
  supported API level.
- **Last-known first:** the save should be fast. A fix younger than 2 minutes is accurate enough to
  geotag a phrase, and it avoids waiting for GPS.
- **Provider order:** `fused` (API 31+) and `network` give a quick fix indoors. A cold-start `gps` fix
  can take longer than the timeout. `gps` is only picked with FINE permission, because on Android 12+
  the user can grant approximate location only.
- **Cancellation:** `suspendCancellableCoroutine` + `CancellationSignal`. When the timeout fires or the
  ViewModel is cleared, the platform request is cancelled, so no listener leaks.
- **Warning suppression:** `@SuppressLint("MissingPermission")` is applied only to the two private
  helpers, which are called after the explicit permission check. A `SecurityException` thrown by a
  single provider is caught and that provider is skipped.
- **`FUSED_PROVIDER` as a literal (`"fused"`):** the constant was added in API 31. The literal avoids an
  `InlinedApi` lint warning, and the value is identical.
- Results are wrapped in `Result<Coordinates>` through `runSuspendCatching`, which rethrows
  `CancellationException`.

## 4. Translator wiring

### 4.1 Permission flow (`TranslatorRoute`)
1. The user confirms the save sheet.
2. If FINE **or** COARSE is already granted, `OnSaveToLog` is dispatched right away.
3. Otherwise the confirmed `(category, notes)` pair is stored in `rememberSaveable` and
   `RequestMultiplePermissions(FINE, COARSE)` is launched. The callback dispatches the save **whatever
   the result is**, then clears the pending pair.

The state is saveable because the system permission dialog can outlive a configuration change. `Pair`,
the enum and `String` are all `Serializable`, so no custom `Saver` is needed. After the user has denied
twice, Android stops showing the dialog and returns "denied" straight away, so repeat saves never nag
the user.

### 4.2 Why the ViewModel fetches the location, and the event lost `coordinates`
The brief says to attach coordinates "before passing it to `LogRepository.saveEntry`". The ViewModel
does this inside the save coroutine:
`locationRepository.getCurrentLocation().getOrNull()` → `entry.copy(coordinates = …)`.
Calling a repository from a Composable would break UDF, so the UI only owns the permission dialog.
That made `OnSaveToLog.coordinates` dead. It was always `null`, so I removed it rather than leave two
sources of truth. The entry text is captured **before** the coroutine suspends on the location
request, so a new recognition result that arrives during the wait cannot change what gets saved.

## 5. Build & test results

Command: `./gradlew assembleDebug testDebugUnitTest`

```
BUILD SUCCESSFUL in 21s
399 actionable tasks: 47 executed, 352 up-to-date
```

- **Unit tests:** 81/81 pass, with 0 failures and 0 errors. That is the 74 earlier tests plus 7 new ones:
  - `LocationProviderSelectionTest` (6): fused is preferred; network before GPS; GPS with FINE;
    GPS skipped with COARSE only; passive is never chosen; nothing enabled → `null`.
  - `TranslatorViewModelTest`: `save without location still persists the entry` (new). The existing
    save test now gets its coordinates from the fake `LocationRepository`.
- **Kotlin warnings:** none.

## 6. Not verified / known gaps

- **`LocationRepositoryImpl` itself has no unit test.** It depends on `LocationManager`, which is
  stubbed on the JVM. Testing it needs Robolectric's `ShadowLocationManager` or a device. Only the pure
  provider-selection logic is covered.
- **Not run on a device or emulator.** The permission dialog, the 10 s timeout and the API 27–29
  fallback path have not been checked by hand.
- **No feedback when a save has no location.** The snackbar says "Guardado en tu bitácora" either way.
  A "saved without location" hint would need a new `UiState` flag and a string.
- **Worst-case save latency is about 10 s.** This happens when there is no recent fix and only a slow
  provider is available. The sheet closes immediately and the success snackbar waits for the fix. Lower
  `CURRENT_FIX_TIMEOUT_MILLIS` if that feels slow in testing.
- **Location services off:** the save goes ahead without coordinates, and the user isn't prompted to turn
  them on. A `LocationSettings` prompt would need Play services.

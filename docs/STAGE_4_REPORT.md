# Stage 4 Report: Map Feature (`:features:map`)

**Track:** Dev A · **Branch:** `feat/room-implementation` · **Date:** 2026-10-19

## 1. Summary

The Map tab used to show `UnderDevelopmentScreen`. It now shows a real OpenStreetMap map, built with
OSMDroid inside a Compose `AndroidView`, with one marker for each of the signed-in user's geotagged
entries. Markers come from Room through `LogRepository.getUserEntries(uid)`, so the map is offline-first:
entries show up with no connection, and previously viewed tiles are served from the on-disk cache.

## 2. Files

| File | Change |
|---|---|
| `features/map/build.gradle.kts` | Adds `osmdroid-android` (6.1.18, from the catalog), `lifecycle-viewmodel-ktx`, `lifecycle-runtime-compose`, test `kotlinx-coroutines-test` |
| `features/map/src/main/AndroidManifest.xml` | Adds `INTERNET` and `ACCESS_NETWORK_STATE` (§4.4) |
| `.../presentation/MapUiState.kt` | **New.** `MapUiState(isLoading, markers)`, `MapMarker` |
| `.../presentation/MapViewModel.kt` | **New.** `currentUser.flatMapLatest { getUserEntries(uid) }` → markers |
| `.../presentation/MapScreen.kt` | **New.** `MapScreen` (stateful) + `MapContent` (stateless), loading bar, empty-state card |
| `.../presentation/osm/OsmMapView.kt` | **New.** `AndroidView` wrapper with lifecycle forwarding, marker rendering and one-time framing |
| `.../presentation/osm/OsmdroidConfig.kt` | **New.** User agent and offline tile cache configuration |
| `features/map/src/main/res/values/strings.xml` | **New.** Empty state and accessibility strings (Spanish, like the rest of the UI) |
| `app/.../main/MainScaffold.kt` | Map route → `MapScreen()` |
| `app/.../main/MainTab.kt` | `Map.inDevelopment = false`, which removes the "Próximamente" label |
| `features/map/src/test/.../MapViewModelTest.kt` | **New.** 6 tests |

Source paths are under `features/map/src/main/java/com/wandervox/features/map/`. The package follows
the Translator's convention (`com.wandervox.features.<feature>.presentation`).

## 3. Data flow

```
AuthRepository.currentUser ──flatMapLatest──▶ LogRepository.getUserEntries(uid)   (Room Flow)
        │ null → flowOf(emptyList())                     │
        ▼                                                ▼
                 mapNotNull { coordinates?.let(::MapMarker) }
                                  │
          stateIn(WhileSubscribed(5 s), MapUiState(isLoading = true))
                                  │
             collectAsStateWithLifecycle() ─▶ MapContent ─▶ OsmMapView
```

- `flatMapLatest` switches automatically when the user signs out or a different user signs in.
- Entries without coordinates (saved before Stage 3, or with permission denied) are dropped by `mapNotNull`.
- A newly saved geotagged entry appears straight away, because the Room `Flow` emits again.
- This matches the Translator's ViewModel pattern: an immutable `UiState` behind `StateFlow`, with
  `WhileSubscribed(5_000)` to survive configuration changes (CLAUDE.md invariant 3).

## 4. Architectural decisions

### 4.1 `AndroidView` wrapping `MapView`
OSMDroid has no Compose API, so `OsmMapView` is the interop boundary:
- **The `MapView` is created once per composition** (`remember`). `factory` returns it, and `update`
  re-renders markers whenever `markers` changes.
- **Lifecycle:** a `LifecycleEventObserver` forwards `ON_RESUME`/`ON_PAUSE` to `mapView.onResume()`/
  `onPause()`, which start and stop tile loading and the cache writer. `onDispose` removes the observer
  and calls `mapView.onDetach()`, which releases the tile provider threads. This runs when the user
  leaves the tab, since bottom navigation disposes inactive destinations. It uses the non-deprecated
  `androidx.lifecycle.compose.LocalLifecycleOwner`.
- **Marker rendering** removes only the overlays that are `Marker`s, and closes any open info windows
  first. The `CopyrightOverlay` stays.
- **Framing happens once.** On the first non-empty data the map centers on the single marker (zoom 15)
  or fits all of them (`zoomToBoundingBox` with 96 px padding). The `hasFramedMarkers` flag is
  `rememberSaveable`, so later emissions, such as a new entry saved from another tab, don't undo the
  user's panning. `zoomToBoundingBox` needs the view size, so framing waits for
  `addOnFirstLayoutListener` if layout hasn't happened yet. Duplicate locations are de-duplicated so a
  zero-size bounding box can't zoom to maximum.

### 4.2 User agent: `context.packageName`, not `BuildConfig.APPLICATION_ID`
The brief suggested enabling `buildConfig` in the map module and using `BuildConfig.APPLICATION_ID`.
AGP 8 **does not generate `APPLICATION_ID` for library modules**. A library's `BuildConfig` only has
`LIBRARY_PACKAGE_NAME`, which would be `com.wandervox.map`, the module namespace, not the app. At
runtime, `Context.packageName` *is* the installed application ID (`com.wandervox.app`, confirmed in the
merged manifest), which is what the OSM tile usage policy asks for. This also avoids turning on
`buildConfig` in the module.

### 4.3 Offline tile cache
OSMDroid writes every downloaded tile to its SQLite tile archive (`SqlTileWriter`) and serves cached
tiles, expired ones included, when the network is unavailable. `OsmdroidConfig` sets it up as follows:
- **Location:** `filesDir/osmdroid/tiles`. OSMDroid's default location is shared external storage,
  which needs `WRITE_EXTERNAL_STORAGE` on API 27–28 (our minSdk is 27). `filesDir` needs no permission,
  and unlike `cacheDir` the system doesn't purge it when storage runs low, which matters for an offline
  travel app.
- **Size:** at most 200 MB, trimmed to 150 MB.
- **Load order:** `Configuration.load()` runs with private `SharedPreferences` before the paths and user
  agent are overridden. Everything happens once, before the first `MapView` is built.

What this covers: areas the user has already viewed work offline. Bulk download of a region before a
trip (`CacheManager`) is **not** included (§6).

### 4.4 Permissions
The OSMDroid 6.1.18 AAR declares **no** permissions (I checked its manifest). `INTERNET` was only in
the merged manifest by accident, through Firebase. `:features:map` now declares `INTERNET` and
`ACCESS_NETWORK_STATE` itself, so the module doesn't depend on another library's manifest. The map
doesn't need the location permission, because it plots saved entries, not the device's live position.

### 4.5 OSM attribution
The OSM tile usage policy requires visible "© OpenStreetMap contributors" attribution, so a
`CopyrightOverlay` is always added.

## 5. Build & test results

Command: `./gradlew assembleDebug testDebugUnitTest`

```
BUILD SUCCESSFUL in 22s
403 actionable tasks: 51 executed, 352 up-to-date
```

- **Unit tests:** 87/87 pass, with 0 failures and 0 errors. That is 81 from Stage 3 plus 6 in `MapViewModelTest`:
  starts loading; only geotagged entries become markers, with fields mapped; only the signed-in user's
  entries; signed out → no markers and not loading; switching user switches markers; a new entry shows
  up live.
- **Kotlin warnings:** none.
- **Merged manifest:** `ACCESS_FINE/COARSE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE` and `RECORD_AUDIO` are present.

## 6. Not verified / known gaps

- **Not run on a device.** Tile rendering, the lifecycle handling, framing and the offline cache have
  not been checked by hand. `OsmMapView` has no automated test, which would need an instrumented
  Compose test.
- **Tapping a marker shows OSMDroid's default info bubble** (phrase and translation), not the entry
  detail screen. That screen is Track B's Log feature. Once its route exists, add a
  `Marker.setOnMarkerClickListener` that navigates with `MapMarker.id`, which is already in the model.
- **No region pre-download.** Only viewed tiles are cached. A "download this area" action
  (`CacheManager.downloadAreaAsync`) would be the next step.
- **The info bubble uses OSMDroid's own XML layout.** That layout is internal to the library and we
  don't write it. Replacing it with Compose would mean a custom `InfoWindow`.
- **Entries from other users and other devices** don't appear. The map shows Room only, and Room isn't
  populated from Firestore yet (see Stage 2 §7).

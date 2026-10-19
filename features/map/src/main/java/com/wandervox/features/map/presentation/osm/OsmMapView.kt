package com.wandervox.features.map.presentation.osm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wandervox.features.map.presentation.MapMarker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * Compose wrapper around OSMDroid's [MapView] (a classic View; there is no Compose-native OSM map).
 * Forwards the host lifecycle to the view and frames the markers once, on the first data that has
 * any, so later updates never undo the user's own panning and zooming.
 */
@Composable
internal fun OsmMapView(
    markers: List<MapMarker>,
    onMarkerClick: ((entryId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        OsmdroidConfig.ensureInitialized(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = MIN_ZOOM
            controller.setZoom(WORLD_ZOOM)
            // Required by the OSM tile usage policy: visible "© OpenStreetMap contributors".
            overlays.add(CopyrightOverlay(context))
        }
    }

    DisposableEffect(lifecycle, mapView) {
        // Resumes/pauses tile loading and the cache writer with the screen.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    var hasFramedMarkers by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(markers) {
        if (!hasFramedMarkers && markers.isNotEmpty()) {
            mapView.frame(markers)
            hasFramedMarkers = true
        }
    }

    AndroidView(
        factory = { mapView },
        // Re-runs after every recomposition, so markers always carry the latest click callback.
        update = { view -> view.renderMarkers(markers, onMarkerClick) },
        modifier = modifier,
    )
}

private fun MapView.renderMarkers(markers: List<MapMarker>, onMarkerClick: ((entryId: String) -> Unit)?) {
    InfoWindow.closeAllInfoWindowsOn(this)
    overlays.removeAll { it is Marker }
    markers.forEach { entry ->
        overlays.add(
            Marker(this).apply {
                id = entry.id
                position = GeoPoint(entry.latitude, entry.longitude)
                title = entry.title
                snippet = entry.snippet
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                if (onMarkerClick != null) {
                    // Returning true consumes the tap, so OSMDroid's default info bubble is not shown.
                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(entry.id)
                        true
                    }
                }
            }
        )
    }
    invalidate()
}

/** Centers on a single location, or fits all of them; waits for the first layout if needed. */
private fun MapView.frame(markers: List<MapMarker>) {
    val points = markers.map { GeoPoint(it.latitude, it.longitude) }.distinct()
    val applyFrame = {
        if (points.size == 1) {
            controller.setZoom(SINGLE_MARKER_ZOOM)
            controller.setCenter(points.single())
        } else {
            zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), false, FRAME_PADDING_PX)
        }
    }
    // zoomToBoundingBox needs the view size, which is 0 until the first layout pass.
    if (isLayoutOccurred) applyFrame() else addOnFirstLayoutListener { _, _, _, _, _ -> applyFrame() }
}

private const val MIN_ZOOM = 3.0
private const val WORLD_ZOOM = 3.0
private const val SINGLE_MARKER_ZOOM = 15.0
private const val FRAME_PADDING_PX = 96

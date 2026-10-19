package com.wandervox.features.map.presentation.osm

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Global OSMDroid configuration. Must run before the first `MapView` is created.
 *
 * - User agent: the OSM tile usage policy bans the default/library user agent and requires one that
 *   identifies the app. `Context.packageName` is the installed application id; library modules no
 *   longer get `BuildConfig.APPLICATION_ID` (AGP 8), so this is the reliable source.
 * - Offline cache: tiles are cached in a SQLite archive under app-private `filesDir`, which needs no
 *   storage permission on any API level (OSMDroid's default is shared external storage) and is not
 *   purged by the system like `cacheDir`. Cached tiles are served when there is no connection.
 */
internal object OsmdroidConfig {

    private var initialized = false

    /** Main thread only (called from composition), so no synchronization is needed. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        Configuration.getInstance().apply {
            load(appContext, appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
            userAgentValue = appContext.packageName
            val basePath = File(appContext.filesDir, BASE_DIRECTORY)
            osmdroidBasePath = basePath
            osmdroidTileCache = File(basePath, TILE_DIRECTORY)
            tileFileSystemCacheMaxBytes = MAX_CACHE_BYTES
            tileFileSystemCacheTrimBytes = TRIM_CACHE_BYTES
        }
        initialized = true
    }

    private const val PREFERENCES_NAME = "osmdroid"
    private const val BASE_DIRECTORY = "osmdroid"
    private const val TILE_DIRECTORY = "tiles"
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    private const val TRIM_CACHE_BYTES = 150L * 1024 * 1024
}

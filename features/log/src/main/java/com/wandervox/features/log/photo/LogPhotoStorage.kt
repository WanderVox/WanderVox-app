package com.wandervox.features.log.photo

/**
 * Keeps Travel Log photos inside the app sandbox so they survive the user deleting them from the
 * gallery and stay available offline.
 */
interface LogPhotoStorage {
    /**
     * Copies the picked image behind [sourceUri] (a `content://` URI from the Photo Picker, as a string
     * so callers stay free of `android.net.Uri`) into internal storage.
     *
     * @return the absolute path of the copy, to be stored in `TravelLogEntry.localPhotoPath`.
     */
    suspend fun importPhoto(sourceUri: String, entryId: String): Result<String>

    /** Deletes a copy made by [importPhoto]. Paths outside the photo directory are ignored. */
    suspend fun deletePhoto(path: String): Result<Unit>
}

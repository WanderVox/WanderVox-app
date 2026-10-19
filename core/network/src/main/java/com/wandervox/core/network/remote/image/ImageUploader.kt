package com.wandervox.core.network.remote.image

/** Uploads a Travel Log photo so other devices and the Community feed can show it. */
interface ImageUploader {
    /**
     * Uploads the file at [localPath] (a copy inside the app sandbox).
     *
     * @return the public HTTPS URL of the uploaded image, for `TravelLogEntry.remotePhotoUrl`.
     */
    suspend fun upload(localPath: String): Result<String>
}

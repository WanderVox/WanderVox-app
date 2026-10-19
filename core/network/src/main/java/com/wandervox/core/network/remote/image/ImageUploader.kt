package com.wandervox.core.network.remote.image

/**
 * Uploads a local image and returns its public URL. Contract stub agreed with Track B; the Cloudinary
 * implementation lives on B's branch.
 */
interface ImageUploader {
    suspend fun upload(localPath: String): Result<String>
}

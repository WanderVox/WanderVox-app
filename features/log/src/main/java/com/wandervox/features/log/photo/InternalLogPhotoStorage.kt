package com.wandervox.features.log.photo

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** [LogPhotoStorage] backed by `filesDir/log_photos/`: private to the app, no storage permission. */
@Singleton
class InternalLogPhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : LogPhotoStorage {

    private val photoDirectory: File
        get() = File(context.filesDir, PHOTO_DIRECTORY)

    override suspend fun importPhoto(sourceUri: String, entryId: String): Result<String> =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val extension = context.contentResolver.getType(uri)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: DEFAULT_EXTENSION
            // A fresh name per import: replacing a photo never overwrites a file Coil may have cached.
            val target = File(photoDirectory.apply { mkdirs() }, "${entryId}_${UUID.randomUUID()}.$extension")
            runCatchingIo {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Cannot open $sourceUri")
                input.use { source -> target.outputStream().use { source.copyTo(it) } }
                target.absolutePath
            }.onFailure { target.delete() }
        }

    override suspend fun deletePhoto(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingIo {
            val file = File(path)
            if (file.parentFile?.canonicalPath == photoDirectory.canonicalPath) file.delete()
            Unit
        }
    }

    /** Like [runCatching], but never swallows coroutine cancellation. */
    private inline fun <T> runCatchingIo(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val PHOTO_DIRECTORY = "log_photos"
        const val DEFAULT_EXTENSION = "jpg"
    }
}

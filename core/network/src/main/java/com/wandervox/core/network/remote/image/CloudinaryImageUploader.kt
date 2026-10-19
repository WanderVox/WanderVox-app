package com.wandervox.core.network.remote.image

import com.wandervox.core.network.di.IoDispatcher
import com.wandervox.core.network.util.runSuspendCatching
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cloudinary connection settings. Only the cloud name and the name of an **unsigned** upload preset
 * ship in the APK; the API secret never does. Both come from `local.properties` through BuildConfig.
 */
data class CloudinaryConfig(
    val cloudName: String,
    val uploadPreset: String,
    val apiBaseUrl: String = "https://api.cloudinary.com",
) {
    val isConfigured: Boolean get() = cloudName.isNotBlank() && uploadPreset.isNotBlank()
}

class CloudinaryUploadException(message: String, val httpCode: Int? = null) : IOException(message)

/**
 * [ImageUploader] using Cloudinary's unsigned upload API: one multipart
 * `POST {apiBaseUrl}/v1_1/{cloudName}/image/upload` with `file` and `upload_preset`. Folder, size
 * limits and allowed formats are set on the preset in the Cloudinary console.
 */
@Singleton
class CloudinaryImageUploader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val config: CloudinaryConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ImageUploader {

    override suspend fun upload(localPath: String): Result<String> = withContext(ioDispatcher) {
        runSuspendCatching {
            if (!config.isConfigured) {
                throw CloudinaryUploadException("Cloudinary is not configured (cloud name / upload preset missing)")
            }
            val file = File(localPath)
            if (!file.isFile) throw FileNotFoundException("Photo not found: $localPath")

            val mediaType = (URLConnection.guessContentTypeFromName(file.name) ?: DEFAULT_MEDIA_TYPE).toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(FIELD_UPLOAD_PRESET, config.uploadPreset)
                .addFormDataPart(FIELD_FILE, file.name, file.asRequestBody(mediaType))
                .build()
            val request = Request.Builder()
                .url("${config.apiBaseUrl}/v1_1/${config.cloudName}/image/upload")
                .post(body)
                .build()

            okHttpClient.newCall(request).await().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw CloudinaryUploadException(
                        "Cloudinary upload failed (${response.code}): ${payload.errorMessage() ?: response.message}",
                        httpCode = response.code,
                    )
                }
                payload.secureUrl()
            }
        }
    }

    private fun String.secureUrl(): String {
        val url = try {
            JSONObject(this).optString(FIELD_SECURE_URL)
        } catch (e: JSONException) {
            throw CloudinaryUploadException("Unreadable Cloudinary response: ${e.message}")
        }
        if (!url.startsWith("https://")) throw CloudinaryUploadException("Cloudinary response has no secure_url")
        return url
    }

    /** Cloudinary errors look like `{"error":{"message":"..."}}`. */
    private fun String.errorMessage(): String? =
        try {
            JSONObject(this).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            null
        }

    /** Runs the call on OkHttp's dispatcher and cancels it if the coroutine is cancelled. */
    @OptIn(ExperimentalCoroutinesApi::class) // resume(value, onCancellation) closes a late response.
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            // If the coroutine was cancelled meanwhile, close the response nobody will read.
            override fun onResponse(call: Call, response: Response) =
                continuation.resume(response) { _ -> response.close() }
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
        })
    }

    private companion object {
        const val FIELD_FILE = "file"
        const val FIELD_UPLOAD_PRESET = "upload_preset"
        const val FIELD_SECURE_URL = "secure_url"
        const val DEFAULT_MEDIA_TYPE = "application/octet-stream"
    }
}

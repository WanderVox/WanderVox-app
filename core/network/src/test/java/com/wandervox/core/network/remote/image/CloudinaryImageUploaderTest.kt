package com.wandervox.core.network.remote.image

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
class CloudinaryImageUploaderTest {

    private val photo: File = File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    private var capturedRequest: Request? = null
    private var capturedBody: String? = null

    @After
    fun tearDown() {
        photo.delete()
    }

    @Test
    fun `successful upload returns the secure url`() = runTest {
        // Cloudinary escapes slashes in its JSON.
        val json = """{"secure_url":"https:\/\/res.cloudinary.com\/demo\/image\/upload\/v1\/a.jpg"}"""
        val uploader = uploader(respondWith(200, json))

        val result = uploader.upload(photo.absolutePath)

        assertEquals("https://res.cloudinary.com/demo/image/upload/v1/a.jpg", result.getOrThrow())
        val request = capturedRequest!!
        assertEquals("POST", request.method)
        assertEquals("https://api.cloudinary.com/v1_1/demo-cloud/image/upload", request.url.toString())
        val body = capturedBody!!
        assertTrue(body.contains("name=\"upload_preset\""))
        assertTrue(body.contains("wandervox_unsigned"))
        assertTrue(body.contains("name=\"file\"; filename=\"${photo.name}\""))
        // Unsigned upload: no credentials of any kind leave the device.
        assertTrue(!body.contains("api_key") && !body.contains("signature"))
    }

    @Test
    fun `http error surfaces cloudinary's message and code`() = runTest {
        val uploader = uploader(respondWith(400, """{"error":{"message":"Upload preset not found"}}"""))

        val error = uploader.upload(photo.absolutePath).exceptionOrNull() as CloudinaryUploadException

        assertEquals(400, error.httpCode)
        assertTrue(error.message!!.contains("Upload preset not found"))
    }

    @Test
    fun `response without https secure url is a failure`() = runTest {
        val uploader = uploader(respondWith(200, """{"url":"http://insecure"}"""))

        assertTrue(uploader.upload(photo.absolutePath).exceptionOrNull() is CloudinaryUploadException)
    }

    @Test
    fun `missing configuration fails without any request`() = runTest {
        val uploader = uploader(respondWith(200, "{}"), CloudinaryConfig(cloudName = "", uploadPreset = ""))

        assertTrue(uploader.upload(photo.absolutePath).exceptionOrNull() is CloudinaryUploadException)
        assertNull(capturedRequest)
    }

    @Test
    fun `missing file fails without any request`() = runTest {
        val uploader = uploader(respondWith(200, "{}"))

        assertTrue(uploader.upload("/does/not/exist.jpg").exceptionOrNull() is FileNotFoundException)
        assertNull(capturedRequest)
    }

    private fun TestScope.uploader(
        interceptor: Interceptor,
        config: CloudinaryConfig = CloudinaryConfig(cloudName = "demo-cloud", uploadPreset = "wandervox_unsigned"),
    ) = CloudinaryImageUploader(
        okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
        config = config,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    /** Answers every call locally and records the request, so no network is used. */
    private fun respondWith(code: Int, json: String) = Interceptor { chain ->
        val request = chain.request()
        capturedRequest = request
        capturedBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

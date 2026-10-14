package com.wandervox.core.network.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.wandervox.core.network.di.IoDispatcher
import com.wandervox.core.network.model.LanguagePair
import com.wandervox.core.network.model.SupportedLanguage
import com.wandervox.core.network.model.UnsupportedLanguageException
import com.wandervox.core.network.speech.VoskModelManager
import com.wandervox.core.network.translation.MlKitTranslatorClient
import com.wandervox.core.network.util.runSuspendCatching
import com.wandervox.core.domain.repository.TranslatorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Streams microphone audio through Vosk and translates the transcript on-device with ML Kit.
 *
 * Threading model: all mutable state below is confined to the main thread (Vosk posts its
 * callbacks there too), and every lifecycle operation is serialized through [sessionMutex].
 * Blocking work (model loading, stopping the audio thread) is moved to [ioDispatcher].
 */
@Singleton
class TranslatorRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voskModelManager: VoskModelManager,
    private val translatorClient: MlKitTranslatorClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TranslatorRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionMutex = Mutex()

    private val _recognizedText = MutableStateFlow("")
    override val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _translatedText = MutableStateFlow("")
    override val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var languagePair: LanguagePair? = null
    private var activeModel: Model? = null
    private var activeModelLanguage: SupportedLanguage? = null
    private var activeSession: ListeningSession? = null
    private var translationJob: Job? = null

    // Incremented to make callbacks still queued from an older session ignore themselves.
    private var sessionGeneration = 0L

    override suspend fun initializeModels(
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        sessionMutex.withLock {
            runSuspendCatching {
                val source = SupportedLanguage.fromCode(sourceLanguage)
                    ?: throw UnsupportedLanguageException(sourceLanguage)
                val target = SupportedLanguage.fromCode(targetLanguage)
                    ?: throw UnsupportedLanguageException(targetLanguage)
                require(source != target) { "Source and target languages must differ" }

                stopSessionLocked()

                // Both acoustic models are extracted up front so swapLanguages() works offline.
                // Asset extraction (disk) and language pack verification (network) run in parallel.
                coroutineScope {
                    val sourceModel = async { voskModelManager.prepareModel(source).getOrThrow() }
                    val targetModel = async { voskModelManager.prepareModel(target).getOrThrow() }
                    val translationPacks = async { translatorClient.prepare(source, target).getOrThrow() }
                    sourceModel.await()
                    targetModel.await()
                    translationPacks.await()
                }

                loadModelLocked(source)
                languagePair = LanguagePair(source, target)
                invalidateSessionCallbacks()
                clearTranscripts()
            }
        }
    }

    override fun startListening() {
        repositoryScope.launch {
            sessionMutex.withLock { startSessionLocked() }
        }
    }

    override fun stopListening() {
        repositoryScope.launch {
            sessionMutex.withLock { stopSessionLocked() }
        }
    }

    override fun swapLanguages() {
        repositoryScope.launch {
            sessionMutex.withLock {
                val currentPair = languagePair ?: run {
                    Log.w(TAG, "swapLanguages() called before initializeModels()")
                    return@withLock
                }
                runSuspendCatching {
                    stopSessionLocked()
                    val swappedPair = currentPair.swapped()
                    // On failure the previous model stays loaded and the pair is left unchanged.
                    loadModelLocked(swappedPair.source)
                    languagePair = swappedPair
                    invalidateSessionCallbacks()
                    clearTranscripts()
                }.onFailure { Log.e(TAG, "Failed to swap languages", it) }
            }
        }
    }

    override fun release() {
        repositoryScope.launch {
            sessionMutex.withLock {
                stopSessionLocked()
                invalidateSessionCallbacks()
                activeModel?.close()
                activeModel = null
                activeModelLanguage = null
                languagePair = null
                translatorClient.closeAll()
                clearTranscripts()
            }
        }
    }

    // Must be called with sessionMutex held.
    private suspend fun startSessionLocked() {
        if (activeSession != null) return
        val pair = languagePair
        val model = activeModel
        if (pair == null || model == null) {
            Log.w(TAG, "startListening() called before initializeModels() succeeded")
            return
        }
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "startListening() called without RECORD_AUDIO permission")
            return
        }

        // Building the decoder and opening AudioRecord can take a noticeable moment.
        val session = withContext(ioDispatcher) {
            var recognizer: Recognizer? = null
            try {
                recognizer = Recognizer(model, SAMPLE_RATE_HZ)
                ListeningSession(recognizer, SpeechService(recognizer, SAMPLE_RATE_HZ))
            } catch (e: IOException) {
                recognizer?.close()
                Log.e(TAG, "Could not open the microphone", e)
                null
            }
        } ?: return

        invalidateSessionCallbacks()
        clearTranscripts()
        session.speechService.startListening(SessionListener(sessionGeneration, pair))
        activeSession = session
        _isListening.value = true
    }

    // Must be called with sessionMutex held.
    private suspend fun stopSessionLocked() {
        val session = activeSession ?: return
        activeSession = null
        _isListening.value = false
        // stop() joins Vosk's audio thread, so keep it off the main thread. NonCancellable ensures
        // the microphone and native recognizer are always released.
        withContext(ioDispatcher + NonCancellable) {
            session.speechService.stop()
            session.speechService.shutdown()
            session.recognizer.close()
        }
    }

    // Must be called with sessionMutex held.
    private suspend fun loadModelLocked(language: SupportedLanguage) {
        if (activeModelLanguage == language && activeModel != null) return
        val modelDir = voskModelManager.prepareModel(language).getOrThrow()

        // Loading is native and not interruptible; if we're cancelled meanwhile, free the result.
        var loadedModel: Model? = null
        try {
            withContext(ioDispatcher) { loadedModel = Model(modelDir.absolutePath) }
        } catch (e: CancellationException) {
            loadedModel?.close()
            throw e
        }

        // Only one model stays in memory: small Vosk models need ~300 MB of RAM each.
        activeModel?.close()
        activeModel = loadedModel
        activeModelLanguage = language
    }

    private fun invalidateSessionCallbacks() {
        sessionGeneration++
        translationJob?.cancel()
    }

    private fun clearTranscripts() {
        _recognizedText.value = ""
        _translatedText.value = ""
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun scheduleTranslation(text: String, pair: LanguagePair, debounce: Boolean) {
        translationJob?.cancel()
        if (text.isBlank()) {
            _translatedText.value = ""
            return
        }
        translationJob = repositoryScope.launch {
            // Partial hypotheses arrive every few hundred ms; only translate once speech settles.
            if (debounce) delay(PARTIAL_TRANSLATION_DEBOUNCE_MS)
            translatorClient.translate(text, pair.source, pair.target)
                .onSuccess { _translatedText.value = it }
                .onFailure { Log.w(TAG, "Translation failed", it) }
        }
    }

    /** Receives Vosk callbacks on the main thread for a single listening session. */
    private inner class SessionListener(
        private val generation: Long,
        private val pair: LanguagePair,
    ) : RecognitionListener {

        private val committedText = StringBuilder()

        private val isCurrent: Boolean get() = generation == sessionGeneration

        override fun onPartialResult(hypothesis: String?) {
            if (!isCurrent) return
            publish(partialText = hypothesis.extractField(PARTIAL_FIELD), debounce = true)
        }

        override fun onResult(hypothesis: String?) {
            if (!isCurrent) return
            commit(hypothesis.extractField(TEXT_FIELD))
        }

        // Delivered after stop(); still accepted unless a newer session/swap/release happened.
        override fun onFinalResult(hypothesis: String?) {
            if (!isCurrent) return
            commit(hypothesis.extractField(TEXT_FIELD))
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Speech recognition error", exception)
            endSessionIfCurrent()
        }

        override fun onTimeout() {
            endSessionIfCurrent()
        }

        private fun commit(segment: String) {
            if (segment.isNotBlank()) {
                if (committedText.isNotEmpty()) committedText.append(' ')
                committedText.append(segment)
            }
            publish(partialText = "", debounce = false)
        }

        private fun publish(partialText: String, debounce: Boolean) {
            val fullText = listOf(committedText.toString(), partialText)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            if (fullText == _recognizedText.value) return
            _recognizedText.value = fullText
            scheduleTranslation(fullText, pair, debounce)
        }

        private fun endSessionIfCurrent() {
            repositoryScope.launch {
                sessionMutex.withLock {
                    if (generation == sessionGeneration) stopSessionLocked()
                }
            }
        }
    }

    private class ListeningSession(
        val recognizer: Recognizer,
        val speechService: SpeechService,
    )

    private fun String?.extractField(field: String): String {
        if (isNullOrBlank()) return ""
        return try {
            JSONObject(this).optString(field).trim()
        } catch (e: JSONException) {
            Log.w(TAG, "Malformed Vosk result: $this", e)
            ""
        }
    }

    private companion object {
        const val TAG = "TranslatorRepository"
        const val SAMPLE_RATE_HZ = 16_000f
        const val PARTIAL_TRANSLATION_DEBOUNCE_MS = 300L
        const val PARTIAL_FIELD = "partial"
        const val TEXT_FIELD = "text"
    }
}

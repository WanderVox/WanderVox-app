package com.wandervox.core.network.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.wandervox.core.network.model.LanguagePackUnavailableException
import com.wandervox.core.network.model.SupportedLanguage
import com.wandervox.core.network.util.awaitResult
import com.wandervox.core.network.util.runSuspendCatching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * On-device translation backed by ML Kit. Language packs are downloaded once (the only step that
 * needs connectivity); every translation afterwards runs fully offline.
 */
@Singleton
class MlKitTranslatorClient @Inject constructor() {

    private val remoteModelManager = RemoteModelManager.getInstance()
    private val translatorsMutex = Mutex()

    // Guarded by translatorsMutex.
    private val translators = mutableMapOf<LanguageDirection, Translator>()

    suspend fun isLanguagePackReady(language: SupportedLanguage): Boolean =
        remoteModelManager.isModelDownloaded(remoteModel(language)).awaitResult()

    /**
     * Verifies (downloading if missing) the packs for both languages, then opens translators for
     * both directions so [translate] keeps working after a language swap without further setup.
     */
    suspend fun prepare(first: SupportedLanguage, second: SupportedLanguage): Result<Unit> =
        runSuspendCatching {
            listOf(first, second).forEach { ensureLanguagePack(it) }
            translatorsMutex.withLock {
                translatorFor(LanguageDirection(first, second))
                translatorFor(LanguageDirection(second, first))
            }
        }

    suspend fun translate(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage,
    ): Result<String> = runSuspendCatching {
        if (text.isBlank()) return@runSuspendCatching ""
        val translator = translatorsMutex.withLock { translatorFor(LanguageDirection(source, target)) }
        translator.translate(text).awaitResult()
    }

    suspend fun closeAll() {
        translatorsMutex.withLock {
            translators.values.forEach(Translator::close)
            translators.clear()
        }
    }

    private suspend fun ensureLanguagePack(language: SupportedLanguage) {
        if (isLanguagePackReady(language)) return
        try {
            // No Wi-Fi requirement: a traveller may only have mobile data when first opening the app.
            remoteModelManager.download(remoteModel(language), DownloadConditions.Builder().build())
                .awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LanguagePackUnavailableException(language, e)
        }
    }

    // Must be called with translatorsMutex held.
    private fun translatorFor(direction: LanguageDirection): Translator =
        translators.getOrPut(direction) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(direction.source.mlKitCode)
                    .setTargetLanguage(direction.target.mlKitCode)
                    .build()
            )
        }

    private fun remoteModel(language: SupportedLanguage): TranslateRemoteModel =
        TranslateRemoteModel.Builder(language.mlKitCode).build()

    private data class LanguageDirection(val source: SupportedLanguage, val target: SupportedLanguage)
}

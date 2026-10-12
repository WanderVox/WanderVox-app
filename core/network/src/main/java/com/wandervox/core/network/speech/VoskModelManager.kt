package com.wandervox.core.network.speech

import android.content.Context
import com.wandervox.core.network.di.IoDispatcher
import com.wandervox.core.network.model.SupportedLanguage
import com.wandervox.core.network.model.VoskModelState
import com.wandervox.core.network.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Copies the bundled Vosk acoustic models from APK assets into `filesDir/models/<model-name>`,
 * because Vosk's native loader needs a real filesystem path.
 *
 * Extraction goes into a staging directory that is renamed into place only once complete, so a
 * process death mid-copy never leaves a half-written model that looks valid.
 */
@Singleton
class VoskModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val modelsRootDir = File(context.filesDir, MODELS_DIR_NAME)
    private val extractionMutex = Mutex()

    private val _modelStates = MutableStateFlow<Map<SupportedLanguage, VoskModelState>>(
        SupportedLanguage.entries.associateWith { VoskModelState.Missing }
    )
    val modelStates: StateFlow<Map<SupportedLanguage, VoskModelState>> = _modelStates.asStateFlow()

    /** Cheap file check; call from a background thread if invoked frequently. */
    fun isModelReady(language: SupportedLanguage): Boolean =
        File(modelDirectory(language), COMPLETION_MARKER).isFile

    /** Returns the extracted model directory, extracting it on first use. Safe to call repeatedly. */
    suspend fun prepareModel(language: SupportedLanguage): Result<File> = withContext(ioDispatcher) {
        extractionMutex.withLock {
            runSuspendCatching {
                val targetDir = modelDirectory(language)
                if (!isModelReady(language)) {
                    updateState(language, VoskModelState.Extracting)
                    try {
                        extractAtomically(language.voskModelAssetName, targetDir)
                    } catch (e: CancellationException) {
                        updateState(language, VoskModelState.Missing)
                        throw e
                    }
                }
                targetDir
            }
                .onSuccess { updateState(language, VoskModelState.Ready(it)) }
                .onFailure { updateState(language, VoskModelState.Failed(it)) }
        }
    }

    private fun modelDirectory(language: SupportedLanguage): File =
        File(modelsRootDir, language.voskModelAssetName)

    private suspend fun extractAtomically(assetName: String, targetDir: File) {
        val assetPath = "$ASSETS_MODELS_DIR/$assetName"
        if (context.assets.list(assetPath).isNullOrEmpty()) {
            throw FileNotFoundException("Vosk model not found in assets: $assetPath")
        }

        modelsRootDir.mkdirs()
        val stagingDir = File(modelsRootDir, "$assetName$STAGING_SUFFIX")
        stagingDir.deleteRecursively()

        copyAssetTree(assetPath, stagingDir)
        // Written last: its presence means every model file was copied.
        File(stagingDir, COMPLETION_MARKER).writeText(assetName)

        targetDir.deleteRecursively()
        if (!stagingDir.renameTo(targetDir)) {
            throw IOException("Could not move extracted model into ${targetDir.absolutePath}")
        }
    }

    private suspend fun copyAssetTree(assetPath: String, destination: File) {
        currentCoroutineContext().ensureActive()
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
            }
        } else {
            destination.mkdirs()
            children.forEach { child -> copyAssetTree("$assetPath/$child", File(destination, child)) }
        }
    }

    private fun updateState(language: SupportedLanguage, state: VoskModelState) {
        _modelStates.update { it + (language to state) }
    }

    private companion object {
        const val MODELS_DIR_NAME = "models"
        const val ASSETS_MODELS_DIR = "models"
        const val STAGING_SUFFIX = ".staging"
        const val COMPLETION_MARKER = ".extraction_complete"
        const val COPY_BUFFER_SIZE = 64 * 1024
    }
}

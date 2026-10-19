package com.wandervox.core.data.repository

import com.wandervox.core.data.di.ApplicationScope
import com.wandervox.core.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the travel log sync for the signed-in user for as long as the process lives: on every sign-in
 * it retries unsynced local work ([OfflineFirstLogRepository.syncPending]) and mirrors the user's remote
 * entries into Room ([OfflineFirstLogRepository.startUserSync]). Signing out or switching user cancels
 * and restarts both. Start it once, from `Application.onCreate`.
 */
@Singleton
class LogSyncCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    // The concrete type: same @Singleton instance as the LogRepository binding, with the sync API.
    private val repository: OfflineFirstLogRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = appScope.launch {
            authRepository.currentUser
                .map { it?.uid }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid == null) return@collectLatest
                    coroutineScope {
                        // In parallel: offline, each pending upload waits for its timeout, and the pull
                        // (served from Firestore's cache) should not wait behind them.
                        launch { repository.syncPending() }
                        repository.startUserSync(uid)
                    }
                }
        }
    }
}

package com.wandervox.core.domain.model

/** Upload state of a [TravelLogEntry]; the local copy is always the source of truth. */
enum class SyncStatus {
    /** Saved locally, not yet confirmed by the remote store. */
    PENDING,
    SYNCED,
    /** The last upload attempt failed; retried by `LogRepository.syncPending()`. */
    FAILED
}

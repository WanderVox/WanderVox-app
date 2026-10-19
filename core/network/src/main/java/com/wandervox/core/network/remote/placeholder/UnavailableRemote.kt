package com.wandervox.core.network.remote.placeholder

import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/*
 * TEMPORARY: keeps the Hilt graph complete until Track B's Firestore/Cloudinary implementations are
 * merged. Every call fails, so entries stay PENDING/FAILED in Room and get uploaded by
 * LogRepository.syncPending() once the real bindings exist.
 * Delete this whole `placeholder` package (and RemotePlaceholderModule) when B's branch lands.
 */

class RemoteUnavailableException : IllegalStateException("Remote travel log store is not available in this build")

class UnavailableLogRemoteDataSource @Inject constructor() : LogRemoteDataSource {
    override suspend fun upsert(entry: TravelLogEntry): Result<Unit> = Result.failure(RemoteUnavailableException())
    override suspend fun delete(entryId: String): Result<Unit> = Result.failure(RemoteUnavailableException())
    override fun observeUserEntries(userId: String): Flow<List<TravelLogEntry>> = flow { throw RemoteUnavailableException() }
    override fun observePublicEntries(): Flow<List<TravelLogEntry>> = flow { throw RemoteUnavailableException() }
    override suspend fun setLiked(entryId: String, userId: String, liked: Boolean): Result<Unit> =
        Result.failure(RemoteUnavailableException())
}

class UnavailableImageUploader @Inject constructor() : ImageUploader {
    override suspend fun upload(localPath: String): Result<String> = Result.failure(RemoteUnavailableException())
}

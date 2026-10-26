package com.wandervox.core.data.repository

import com.wandervox.core.database.mapper.asDomain
import com.wandervox.core.database.mapper.asEntity
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstLogRepositoryTest {

    private val dao = FakeTravelLogDao()
    private val remote = FakeLogRemoteDataSource()
    private val uploader = FakeImageUploader()
    private val user = User(uid = "uid-1", email = "lautaro@example.com")

    private fun TestScope.repository(currentUser: User? = user) =
        OfflineFirstLogRepository(dao, remote, uploader, FakeAuthRepository(currentUser), appScope = this)

    /** Public, so the sync tests exercise the upload pipeline; private entries never reach the remote. */
    private val translatorEntry = TravelLogEntry(id = "e1", originalText = "Hola", translatedText = "Hello", isPublic = true)
    private val privateEntry = translatorEntry.copy(isPublic = false)

    // --- userId fix ---

    @Test
    fun saveEntry_stampsSignedInUser() = runTest {
        repository().saveEntry(translatorEntry).getOrThrow()

        val row = dao.row("e1")!!
        assertEquals("uid-1", row.userId)
        assertEquals("lautaro@example.com", row.authorName)
    }

    @Test
    fun saveEntry_userWithoutEmail_getsDefaultAuthorName() = runTest {
        repository(User(uid = "uid-1", email = null)).saveEntry(translatorEntry).getOrThrow()

        assertEquals("WanderVox traveler", dao.row("e1")!!.authorName)
    }

    @Test
    fun saveEntry_signedOut_keepsEntryOwner() = runTest {
        repository(currentUser = null).saveEntry(translatorEntry.copy(userId = "given")).getOrThrow()

        assertEquals("given", dao.row("e1")!!.userId)
    }

    @Test
    fun saveEntry_entryIsVisibleInUserLog() = runTest {
        val repo = repository()
        repo.saveEntry(translatorEntry).getOrThrow()

        assertEquals(listOf("e1"), repo.getUserEntries("uid-1").first().map { it.id })
    }

    @Test
    fun saveEntry_blankId_generatesOne() = runTest {
        repository().saveEntry(translatorEntry.copy(id = "")).getOrThrow()

        assertTrue(dao.rows.value.keys.single().isNotBlank())
    }

    // --- sync ---

    @Test
    fun saveEntry_returnsBeforeSync_withPendingRow() = runTest {
        remote.onUpsert = { awaitCancellation() }

        repository().saveEntry(translatorEntry).getOrThrow()

        assertEquals(SyncStatus.PENDING, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun saveEntry_remoteSucceeds_marksSynced() = runTest {
        repository().saveEntry(translatorEntry).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
        assertEquals("uid-1", remote.upserted.single().userId)
    }

    @Test
    fun saveEntry_withPhoto_uploadsThenSendsRemoteUrl() = runTest {
        repository().saveEntry(translatorEntry.copy(localPhotoPath = "/files/p.jpg")).getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf("/files/p.jpg"), uploader.uploads)
        assertEquals("https://cdn.example/photo.jpg", remote.upserted.single().remotePhotoUrl)
        assertEquals("https://cdn.example/photo.jpg", dao.row("e1")!!.remotePhotoUrl)
    }

    @Test
    fun saveEntry_photoAlreadyUploaded_skipsUpload() = runTest {
        repository().saveEntry(translatorEntry.copy(localPhotoPath = "/p.jpg", remotePhotoUrl = "https://x")).getOrThrow()
        advanceUntilIdle()

        assertTrue(uploader.uploads.isEmpty())
    }

    @Test
    fun saveEntry_uploadFails_marksFailed_andSkipsUpsert() = runTest {
        uploader.result = Result.failure(IllegalStateException("no network"))

        repository().saveEntry(translatorEntry.copy(localPhotoPath = "/p.jpg")).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
        assertTrue(remote.upserted.isEmpty())
    }

    @Test
    fun saveEntry_remoteThrows_marksFailed() = runTest {
        remote.onUpsert = { throw IllegalStateException("boom") }

        repository().saveEntry(translatorEntry).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun saveEntry_remoteHangs_timesOutAsFailed() = runTest {
        remote.onUpsert = { awaitCancellation() }

        repository().saveEntry(translatorEntry).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun upsertFailsAfterUpload_keepsPhotoUrl_soRetryDoesNotReupload() = runTest {
        remote.onUpsert = { Result.failure(IllegalStateException("offline")) }
        val repo = repository()
        repo.saveEntry(translatorEntry.copy(localPhotoPath = "/p.jpg")).getOrThrow()
        advanceUntilIdle()
        assertEquals("https://cdn.example/photo.jpg", dao.row("e1")!!.remotePhotoUrl)

        remote.onUpsert = { Result.success(Unit) }
        repo.syncPending().getOrThrow()

        assertEquals(1, uploader.uploads.size)
        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun editDuringSync_isNotOverwritten_andSyncsNewestVersion() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.onUpsert = { gate.await(); Result.success(Unit) }
        val repo = repository()

        repo.saveEntry(translatorEntry).getOrThrow()
        runCurrent() // first upload is now waiting on the gate
        repo.saveEntry(translatorEntry.copy(notes = "edited")).getOrThrow()
        gate.complete(Unit)
        advanceUntilIdle()

        val row = dao.row("e1")!!
        assertEquals("edited", row.notes)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals("edited", remote.upserted.last().notes)
    }

    @Test
    fun saveEntry_editBumpsUpdatedAt() = runTest {
        val repo = repository()
        repo.saveEntry(translatorEntry).getOrThrow()
        val first = dao.row("e1")!!.updatedAt
        repo.saveEntry(translatorEntry.copy(notes = "x")).getOrThrow()

        assertNotEquals(first, dao.row("e1")!!.updatedAt)
    }

    @Test
    fun syncPending_retriesPendingAndFailed_skipsSynced() = runTest {
        dao.upsert(translatorEntry.copy(id = "p", syncStatus = SyncStatus.PENDING).asEntity())
        dao.upsert(translatorEntry.copy(id = "f", syncStatus = SyncStatus.FAILED).asEntity())
        dao.upsert(translatorEntry.copy(id = "s", syncStatus = SyncStatus.SYNCED).asEntity())

        repository().syncPending().getOrThrow()

        assertEquals(setOf("p", "f"), remote.upserted.map { it.id }.toSet())
        assertTrue(dao.rows.value.values.all { it.syncStatus == SyncStatus.SYNCED })
    }

    @Test
    fun syncPending_reportsFailures() = runTest {
        dao.upsert(translatorEntry.copy(syncStatus = SyncStatus.FAILED).asEntity())
        remote.onUpsert = { Result.failure(IllegalStateException("offline")) }

        val result = repository().syncPending()

        assertTrue(result.exceptionOrNull() is SyncIncompleteException)
        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
    }

    // --- upload on demand ---

    @Test
    fun saveEntry_private_staysLocal_withoutAnyNetworkCall() = runTest {
        repository().saveEntry(privateEntry.copy(localPhotoPath = "/files/p.jpg")).getOrThrow()
        advanceUntilIdle()

        assertTrue(remote.operations.isEmpty())
        assertTrue(uploader.uploads.isEmpty())
        // SYNCED: nothing is owed to the server, so no "pending" badge and nothing for syncPending().
        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun editingANeverSharedEntry_staysLocal() = runTest {
        val repo = repository()
        repo.saveEntry(privateEntry).getOrThrow()
        repo.saveEntry(privateEntry.copy(notes = "editada")).getOrThrow()
        advanceUntilIdle()

        assertTrue(remote.operations.isEmpty())
        assertEquals("editada", dao.row("e1")!!.notes)
    }

    @Test
    fun syncPending_skipsPrivateLocalEntries() = runTest {
        val repo = repository()
        repo.saveEntry(privateEntry).getOrThrow()

        repo.syncPending().getOrThrow()
        advanceUntilIdle()

        assertTrue(remote.operations.isEmpty())
    }

    @Test
    fun sharing_uploadsPhotoThenWritesPublicDocument() = runTest {
        val repo = repository()
        repo.saveEntry(privateEntry.copy(localPhotoPath = "/files/p.jpg")).getOrThrow()
        advanceUntilIdle()

        repo.saveEntry(privateEntry.copy(localPhotoPath = "/files/p.jpg", isPublic = true)).getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf("/files/p.jpg"), uploader.uploads)
        val document = remote.upserted.single()
        assertTrue(document.isPublic)
        assertEquals("https://cdn.example/photo.jpg", document.remotePhotoUrl)
        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun makingPrivate_deletesRemoteDocument_keepsRowAndPhotoUrl() = runTest {
        val repo = repository()
        repo.saveEntry(translatorEntry.copy(localPhotoPath = "/files/p.jpg")).getOrThrow()
        advanceUntilIdle()

        repo.saveEntry(dao.row("e1")!!.asDomain().copy(isPublic = false)).getOrThrow()
        advanceUntilIdle()

        assertEquals(listOf("upsert:e1", "delete:e1"), remote.operations)
        val row = dao.row("e1")!!
        assertFalse(row.isPublic)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals("https://cdn.example/photo.jpg", row.remotePhotoUrl)
    }

    @Test
    fun sharingAgain_reusesUploadedPhoto() = runTest {
        val repo = repository()
        repo.saveEntry(translatorEntry.copy(localPhotoPath = "/files/p.jpg")).getOrThrow()
        advanceUntilIdle()
        repo.saveEntry(dao.row("e1")!!.asDomain().copy(isPublic = false)).getOrThrow()
        advanceUntilIdle()

        repo.saveEntry(dao.row("e1")!!.asDomain().copy(isPublic = true)).getOrThrow()
        advanceUntilIdle()

        assertEquals(1, uploader.uploads.size)
        assertEquals(listOf("upsert:e1", "delete:e1", "upsert:e1"), remote.operations)
    }

    @Test
    fun unpublishOffline_marksFailed_thenSyncPendingFinishesIt() = runTest {
        val repo = repository()
        repo.saveEntry(translatorEntry).getOrThrow()
        advanceUntilIdle()
        remote.deleteResult = Result.failure(IllegalStateException("offline"))

        repo.saveEntry(translatorEntry.copy(isPublic = false)).getOrThrow()
        advanceUntilIdle()
        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)

        remote.deleteResult = Result.success(Unit)
        repo.syncPending().getOrThrow()
        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
        assertEquals(listOf("e1"), remote.deleted)
    }

    @Test
    fun visibility_isWrittenToTheUploadedDocument() = runTest {
        repository().saveEntry(translatorEntry).getOrThrow()
        advanceUntilIdle()

        assertTrue(remote.upserted.single().isPublic)
        assertTrue(dao.row("e1")!!.isPublic)
    }

    @Test
    fun communityFeed_showsEntryOnceShared_andDropsItWhenPrivate() = runTest {
        // Stands in for Firestore: upserts land in the store, the public query is a live filter over it.
        val store = MutableStateFlow<Map<String, TravelLogEntry>>(emptyMap())
        remote.onUpsert = { entry -> store.update { it + (entry.id to entry) }; Result.success(Unit) }
        remote.onDeleted = { id -> store.update { it - id } }
        remote.publicEntries = store.map { docs -> docs.values.filter { it.isPublic } }
        val repo = repository()
        val feed = mutableListOf<List<String>>()
        // Unconfined: advanceUntilIdle() does not drive backgroundScope, so collect eagerly instead.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.getCommunityEntries().collect { entries -> feed += entries.map { it.id } }
        }

        repo.saveEntry(privateEntry).getOrThrow()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), feed.last())

        repo.saveEntry(privateEntry.copy(isPublic = true)).getOrThrow()
        advanceUntilIdle()
        assertEquals(listOf("e1"), feed.last())

        repo.saveEntry(privateEntry).getOrThrow()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), feed.last())
    }

    @Test
    fun lifecycle_privateWithPhoto_isLocalUntilShared_thenUploadedAndInCommunity() = runTest {
        val store = MutableStateFlow<Map<String, TravelLogEntry>>(emptyMap())
        remote.onUpsert = { entry -> store.update { it + (entry.id to entry) }; Result.success(Unit) }
        remote.publicEntries = store.map { docs -> docs.values.filter { it.isPublic } }
        val repo = repository()
        val feed = mutableListOf<List<TravelLogEntry>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.getCommunityEntries().collect { feed += it } }

        // 1. Saved from the Translator with a photo copied to filesDir: local only.
        repo.saveEntry(privateEntry.copy(localPhotoPath = "/files/log_photos/e1.jpg")).getOrThrow()
        advanceUntilIdle()
        assertTrue(uploader.uploads.isEmpty())
        assertTrue(remote.operations.isEmpty())
        assertTrue(feed.last().isEmpty())

        // 2. Shared from its card: photo to Cloudinary, then the public document to Firestore.
        repo.saveEntry(dao.row("e1")!!.asDomain().copy(isPublic = true)).getOrThrow()
        advanceUntilIdle()
        assertEquals(listOf("/files/log_photos/e1.jpg"), uploader.uploads)
        val document = store.value.getValue("e1")
        assertTrue(document.isPublic)
        assertEquals("https://cdn.example/photo.jpg", document.remotePhotoUrl)

        // 3. The Community feed shows it, with the Cloudinary URL other devices load the photo from.
        assertEquals("https://cdn.example/photo.jpg", feed.last().single().remotePhotoUrl)
        val row = dao.row("e1")!!
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertEquals("/files/log_photos/e1.jpg", row.localPhotoPath)
    }

    // --- reads ---

    @Test
    fun getCommunityEntries_usesRemoteFeed() = runTest {
        remote.publicEntries = flowOf(listOf(translatorEntry.copy(id = "remote")))

        assertEquals(listOf("remote"), repository().getCommunityEntries().first().map { it.id })
    }

    @Test
    fun getCommunityEntries_remoteError_fallsBackToLocalPublicEntries() = runTest {
        remote.failObserving()
        dao.upsert(translatorEntry.copy(id = "pub", isPublic = true).asEntity())
        dao.upsert(translatorEntry.copy(id = "priv", isPublic = false).asEntity())

        assertEquals(listOf("pub"), repository().getCommunityEntries().first().map { it.id })
    }

    @Test
    fun getEntry_emitsNullWhenMissing() = runTest {
        assertNull(repository().getEntry("nope").first())
    }

    // --- likes & delete ---

    @Test
    fun toggleLike_localEntry_addsLikeAndCallsRemote() = runTest {
        dao.upsert(translatorEntry.asEntity())

        repository().toggleLike("e1", "uid-2").getOrThrow()

        assertEquals(listOf("uid-2"), dao.row("e1")!!.likes)
        assertEquals(Triple("e1", "uid-2", true), remote.likeCalls.single())
    }

    @Test
    fun toggleLike_remoteFails_rollsBackLocalLike() = runTest {
        dao.upsert(translatorEntry.copy(likes = listOf("uid-2")).asEntity())
        remote.likeResult = Result.failure(IllegalStateException("offline"))

        val result = repository().toggleLike("e1", "uid-2")

        assertTrue(result.isFailure)
        assertEquals(listOf("uid-2"), dao.row("e1")!!.likes)
    }

    @Test
    fun toggleLike_communityEntryNotInRoom_usesRemoteLikes() = runTest {
        remote.publicEntries = flowOf(listOf(translatorEntry.copy(id = "other", likes = listOf("uid-2"))))

        repository().toggleLike("other", "uid-2").getOrThrow()

        assertEquals(Triple("other", "uid-2", false), remote.likeCalls.single())
    }

    // --- upload resiliency ---

    @Test
    fun slowUpload_withinTwoMinutes_succeeds() = runTest {
        uploader.delayMillis = 90_000L // would have hit the old 30 s budget

        repository().saveEntry(translatorEntry.copy(localPhotoPath = "/p.jpg")).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
        assertEquals("https://cdn.example/photo.jpg", dao.row("e1")!!.remotePhotoUrl)
    }

    @Test
    fun uploadOverTwoMinutes_timesOutAsFailed_withoutUpsert() = runTest {
        uploader.delayMillis = 121_000L

        repository().saveEntry(translatorEntry.copy(localPhotoPath = "/p.jpg")).getOrThrow()
        advanceUntilIdle()

        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
        assertTrue(remote.upserted.isEmpty())
    }

    @Test
    fun missingPhotoFile_dropsPath_andSyncsEntryWithoutPhoto() = runTest {
        uploader.result = Result.failure(FileNotFoundException("/gone.jpg"))
        val repo = repository()

        repo.saveEntry(translatorEntry.copy(localPhotoPath = "/gone.jpg")).getOrThrow()
        advanceUntilIdle()

        val row = dao.row("e1")!!
        assertNull(row.localPhotoPath)
        assertNull(row.remotePhotoUrl)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertNull(remote.upserted.single().localPhotoPath)
    }

    @Test
    fun missingPhotoFile_upsertFails_retryDoesNotTryThePhotoAgain() = runTest {
        uploader.result = Result.failure(FileNotFoundException("/gone.jpg"))
        remote.onUpsert = { Result.failure(IllegalStateException("offline")) }
        val repo = repository()
        repo.saveEntry(translatorEntry.copy(localPhotoPath = "/gone.jpg")).getOrThrow()
        advanceUntilIdle()
        assertEquals(SyncStatus.FAILED, dao.row("e1")!!.syncStatus)
        assertNull(dao.row("e1")!!.localPhotoPath)

        remote.onUpsert = { Result.success(Unit) }
        repo.syncPending().getOrThrow()

        assertEquals(1, uploader.uploads.size)
        assertEquals(SyncStatus.SYNCED, dao.row("e1")!!.syncStatus)
    }

    // --- remote pull (startUserSync) ---

    private val remoteEntry = TravelLogEntry(
        id = "r1", userId = "uid-1", originalText = "remote", updatedAt = 100L,
        remotePhotoUrl = "https://cdn/r1.jpg", syncStatus = SyncStatus.SYNCED
    )

    private fun TestScope.startPull(repo: OfflineFirstLogRepository) {
        backgroundScope.launch { repo.startUserSync("uid-1") }
        runCurrent()
    }

    @Test
    fun pull_freshInstall_insertsRemoteEntriesAsSynced() = runTest {
        remote.userEntries.value = listOf(remoteEntry.copy(localPhotoPath = "/other/device.jpg"))
        val repo = repository()

        startPull(repo)

        val row = dao.row("r1")!!
        assertEquals("remote", row.originalText)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
        assertNull(row.localPhotoPath)
        assertEquals(listOf("r1"), repo.getUserEntries("uid-1").first().map { it.id })
    }

    @Test
    fun pull_remoteNewer_updatesRow_preservingLocalPhotoPath() = runTest {
        dao.upsert(remoteEntry.copy(originalText = "old", updatedAt = 50L, localPhotoPath = "/files/r1.jpg").asEntity())
        remote.userEntries.value = listOf(remoteEntry)

        startPull(repository())

        val row = dao.row("r1")!!
        assertEquals("remote", row.originalText)
        assertEquals(100L, row.updatedAt)
        assertEquals("/files/r1.jpg", row.localPhotoPath)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }

    @Test
    fun pull_remoteOlder_isIgnored() = runTest {
        dao.upsert(remoteEntry.copy(originalText = "local newer", updatedAt = 200L).asEntity())
        remote.userEntries.value = listOf(remoteEntry)

        startPull(repository())

        assertEquals("local newer", dao.row("r1")!!.originalText)
    }

    @Test
    fun pull_neverOverwritesUnsyncedLocalRows() = runTest {
        val unsynced = listOf(SyncStatus.PENDING, SyncStatus.FAILED, SyncStatus.PENDING_DELETE)
        unsynced.forEachIndexed { i, status ->
            dao.upsert(remoteEntry.copy(id = "u$i", originalText = "local", updatedAt = 1L, syncStatus = status).asEntity())
        }
        remote.userEntries.value = unsynced.indices.map { i -> remoteEntry.copy(id = "u$i", updatedAt = 999L) }

        startPull(repository())

        unsynced.forEachIndexed { i, status ->
            val row = dao.row("u$i")!!
            assertEquals("local", row.originalText)
            assertEquals(status, row.syncStatus)
        }
    }

    @Test
    fun pull_sameVersionWithNewLikes_refreshesLikes() = runTest {
        dao.upsert(remoteEntry.asEntity())
        remote.userEntries.value = listOf(remoteEntry.copy(likes = listOf("uid-9")))

        startPull(repository())

        assertEquals(listOf("uid-9"), dao.row("r1")!!.likes)
    }

    @Test
    fun pull_appliesLaterRemoteChanges() = runTest {
        remote.userEntries.value = listOf(remoteEntry)
        startPull(repository())

        remote.userEntries.value = listOf(remoteEntry.copy(notes = "edited on tablet", updatedAt = 300L))
        runCurrent()

        assertEquals("edited on tablet", dao.row("r1")!!.notes)
    }

    @Test
    fun pull_doesNotResurrectEntryDeletedThisSession() = runTest {
        dao.upsert(remoteEntry.asEntity())
        val repo = repository()
        repo.deleteEntry("r1").getOrThrow()
        advanceUntilIdle()
        assertNull(dao.row("r1"))

        remote.userEntries.value = listOf(remoteEntry) // stale snapshot taken before the delete
        startPull(repo)

        assertNull(dao.row("r1"))
    }

    @Test
    fun pull_remoteListenerError_returnsWithoutThrowing() = runTest {
        val failingRemote = object : LogRemoteDataSource by remote {
            override fun observeUserEntries(userId: String) = flow<List<TravelLogEntry>> { throw IllegalStateException("denied") }
        }
        val repo = OfflineFirstLogRepository(dao, failingRemote, uploader, FakeAuthRepository(user), appScope = this)

        repo.startUserSync("uid-1") // completes instead of throwing
        assertTrue(dao.rows.value.isEmpty())
    }

    // --- tombstone deletes ---

    @Test
    fun deleteEntry_hidesImmediately_asTombstone_untilRemoteConfirms() = runTest {
        dao.upsert(translatorEntry.copy(userId = "uid-1", syncStatus = SyncStatus.SYNCED).asEntity())
        val repo = repository()
        val before = dao.row("e1")!!.updatedAt

        repo.deleteEntry("e1").getOrThrow()

        val tombstone = dao.row("e1")!!
        assertEquals(SyncStatus.PENDING_DELETE, tombstone.syncStatus)
        assertTrue(tombstone.updatedAt > before)
        assertNull(repo.getEntry("e1").first())
        assertTrue(repo.getUserEntries("uid-1").first().isEmpty())
    }

    @Test
    fun deleteEntry_remoteConfirms_removesRow() = runTest {
        dao.upsert(translatorEntry.asEntity())

        repository().deleteEntry("e1").getOrThrow()
        advanceUntilIdle()

        assertNull(dao.row("e1"))
        assertEquals(listOf("e1"), remote.deleted)
    }

    @Test
    fun deleteEntry_offline_keepsTombstone_thenSyncPendingFinishesIt() = runTest {
        dao.upsert(translatorEntry.asEntity())
        remote.deleteResult = Result.failure(IllegalStateException("offline"))
        val repo = repository()
        repo.deleteEntry("e1").getOrThrow()
        advanceUntilIdle()
        assertEquals(SyncStatus.PENDING_DELETE, dao.row("e1")!!.syncStatus)

        remote.deleteResult = Result.success(Unit)
        repo.syncPending().getOrThrow()

        assertNull(dao.row("e1"))
    }

    @Test
    fun syncPending_tombstoneStillFailing_reportsFailure_andKeepsRow() = runTest {
        dao.upsert(translatorEntry.copy(syncStatus = SyncStatus.PENDING_DELETE).asEntity())
        remote.deleteResult = Result.failure(IllegalStateException("offline"))

        assertTrue(repository().syncPending().exceptionOrNull() is SyncIncompleteException)
        assertEquals(SyncStatus.PENDING_DELETE, dao.row("e1")!!.syncStatus)
    }

    @Test
    fun deleteDuringUpload_remoteEndsDeleted_notResurrected() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.onUpsert = { gate.await(); Result.success(Unit) }
        val repo = repository()

        repo.saveEntry(translatorEntry).getOrThrow()
        runCurrent() // upload is waiting on the gate
        repo.deleteEntry("e1").getOrThrow()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("upsert:e1", "delete:e1"), remote.operations)
        assertNull(dao.row("e1"))
    }

    @Test
    fun deleteEntry_alreadyTombstoned_fails() = runTest {
        dao.upsert(translatorEntry.copy(syncStatus = SyncStatus.PENDING_DELETE).asEntity())

        assertTrue(repository().deleteEntry("e1").isFailure)
    }

    @Test
    fun toggleLike_onTombstone_fails() = runTest {
        dao.upsert(translatorEntry.copy(syncStatus = SyncStatus.PENDING_DELETE).asEntity())

        assertTrue(repository().toggleLike("e1", "uid-2").isFailure)
        assertTrue(remote.likeCalls.isEmpty())
    }

    @Test
    fun deleteEntry_missing_fails() = runTest {
        assertFalse(repository().deleteEntry("nope").isSuccess)
    }
}

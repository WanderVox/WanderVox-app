package com.wandervox.core.data.repository

import com.wandervox.core.database.mapper.asEntity
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstLogRepositoryTest {

    private val dao = FakeTravelLogDao()
    private val remote = FakeLogRemoteDataSource()
    private val uploader = FakeImageUploader()
    private val user = User(uid = "uid-1", email = "lautaro@example.com")

    private fun TestScope.repository(currentUser: User? = user) =
        OfflineFirstLogRepository(dao, remote, uploader, FakeAuthRepository(currentUser), appScope = this)

    private val translatorEntry = TravelLogEntry(id = "e1", originalText = "Hola", translatedText = "Hello")

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

    @Test
    fun deleteEntry_removesLocallyAndRemotely() = runTest {
        dao.upsert(translatorEntry.asEntity())

        repository().deleteEntry("e1").getOrThrow()
        advanceUntilIdle()

        assertNull(dao.row("e1"))
        assertEquals(listOf("e1"), remote.deleted)
    }

    @Test
    fun deleteEntry_missing_fails() = runTest {
        assertFalse(repository().deleteEntry("nope").isSuccess)
    }
}

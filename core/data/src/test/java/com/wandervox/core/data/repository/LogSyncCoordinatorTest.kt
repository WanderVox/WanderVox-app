package com.wandervox.core.data.repository

import com.wandervox.core.database.mapper.asEntity
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogSyncCoordinatorTest {

    private val dao = FakeTravelLogDao()
    private val remote = FakeLogRemoteDataSource()
    private val auth = FakeAuthRepository(user = null)

    private fun TestScope.startCoordinator() {
        val repository = OfflineFirstLogRepository(dao, remote, FakeImageUploader(), auth, appScope = backgroundScope)
        LogSyncCoordinator(auth, repository, appScope = backgroundScope).start()
        runCurrent()
    }

    @Test
    fun signedOut_doesNothing() = runTest {
        remote.userEntries.value = listOf(TravelLogEntry(id = "r1", userId = "uid-1"))

        startCoordinator()

        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun signIn_pullsUserEntries_andRetriesPendingWork() = runTest {
        dao.upsert(TravelLogEntry(id = "local", userId = "uid-1", syncStatus = SyncStatus.FAILED).asEntity())
        remote.userEntries.value = listOf(TravelLogEntry(id = "r1", userId = "uid-1", syncStatus = SyncStatus.SYNCED))
        startCoordinator()

        auth.currentUser.value = User(uid = "uid-1", email = null)
        runCurrent()

        assertEquals(SyncStatus.SYNCED, dao.row("r1")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, dao.row("local")!!.syncStatus)
        assertEquals(listOf("local"), remote.upserted.map { it.id })
    }

    @Test
    fun signOut_stopsPulling() = runTest {
        auth.currentUser.value = User(uid = "uid-1", email = null)
        startCoordinator()

        auth.currentUser.value = null
        runCurrent()
        remote.userEntries.value = listOf(TravelLogEntry(id = "late", userId = "uid-1"))
        runCurrent()

        assertNull(dao.row("late"))
    }

    @Test
    fun switchingUser_pullsTheNewUsersEntries() = runTest {
        remote.userEntries.value = listOf(
            TravelLogEntry(id = "a", userId = "uid-1"),
            TravelLogEntry(id = "b", userId = "uid-2"),
        )
        auth.currentUser.value = User(uid = "uid-1", email = null)
        startCoordinator()

        auth.currentUser.value = User(uid = "uid-2", email = null)
        runCurrent()

        assertEquals(setOf("a", "b"), dao.rows.value.keys)
    }
}

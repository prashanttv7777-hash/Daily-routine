package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DailyLifeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveTask_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = DailyLifeRepository(firestore, auth)
        val uniqueId = "task_${UUID.randomUUID()}"

        val newTask = DailyTask(
            id = uniqueId,
            title = "Write Architecture Spec",
            description = "Design Daily Routine schema",
            category = "Work",
            date = "2026-11-03",
            startTime = "09:00",
            endTime = "10:30",
            completed = false,
            priority = "High",
            recurrence = "Daily",
            recurrenceDays = "1,2,3,4,5",
            reminderEnabled = true,
            reminderMinutesBefore = 15L,
            isImportantDate = true
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createTask(newTask) }
        assertTrue("Create task should succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val createdId = createResult.getOrThrow()
        assertEquals(uniqueId, createdId)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(aliceUid).first { list -> list.any { it.id == uniqueId } }
        }
        assertTrue(observed.any { it.id == uniqueId && it.title == "Write Architecture Spec" })

        val toggleResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.toggleTaskCompleted(uniqueId, true)
        }
        assertTrue(toggleResult.isSuccess)
    }

    @Test
    fun getTaskById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = DailyLifeRepository(firestore, auth)
        val uniqueId = "task_${UUID.randomUUID()}"

        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask(
                DailyTask(
                    id = uniqueId,
                    title = "Alice Private Task",
                    description = "Confidential notes",
                    category = "Personal",
                    date = "2026-11-03",
                    startTime = "14:00",
                    endTime = "15:00"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = DailyLifeRepository(firestore, auth)
        val bobResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getTaskById(aliceUid, taskId)
        }
        assertTrue("Cross-user access must fail", bobResult.isFailure)
        val firestoreEx = bobResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = DailyLifeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks(aliceUid).first()
            }
            fail("Expected exception for unauthenticated user")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    @Test
    fun createRoutineAndBackup_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = DailyLifeRepository(firestore, auth)

        val routineResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createRoutine(
                RoutineItem(
                    id = "routine_${UUID.randomUUID()}",
                    title = "Morning Meditation",
                    description = "15 mins mindfulness",
                    timeSlot = "Morning",
                    startTime = "06:30",
                    endTime = "06:45",
                    category = "Health",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 10L
                )
            )
        }
        assertTrue(routineResult.isSuccess)

        val backupResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createBackupSnapshot(
                label = "Automated Test Snapshot",
                tasks = emptyList(),
                routines = emptyList()
            )
        }
        assertTrue(backupResult.isSuccess)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@mydailylife.test"
        const val BOB_EMAIL = "bob@mydailylife.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

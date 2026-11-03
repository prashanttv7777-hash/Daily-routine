package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CloudBackup
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class DailyLifeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Convenience secondary constructor resolving the named database ID from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- TASKS ---

    fun observeTasks(userId: String = requireUserId()): Flow<List<DailyTask>> = flow {
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(DailyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.sortedWith(compareBy<DailyTask> { it.date }.thenBy { it.startTime })
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getUserTasks(userId: String = requireUserId()): Result<List<DailyTask>> {
        val path = "users/$userId/tasks"
        return try {
            val snapshot = db.collection("users").document(userId).collection("tasks")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull {
                it.toObject(DailyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getTaskById(ownerUserId: String, taskId: String): Result<DailyTask?> {
        val path = "users/$ownerUserId/tasks/$taskId"
        return try {
            val doc = db.collection("users").document(ownerUserId)
                .collection("tasks").document(taskId)
                .get()
                .await()
            Result.success(doc.toObject(DailyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createTask(task: DailyTask): Result<String> {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("tasks")
        val docRef = if (task.id.isNotBlank()) tasksRef.document(task.id) else tasksRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to task.title.trim().take(150).ifEmpty { "Untitled Task" },
            "description" to task.description.trim().take(1000),
            "category" to task.category.trim().take(50).ifEmpty { "Work" },
            "date" to task.date.trim().take(10),
            "startTime" to task.startTime.trim().take(5),
            "endTime" to task.endTime.trim().take(5),
            "completed" to task.completed,
            "priority" to task.priority,
            "recurrence" to task.recurrence,
            "recurrenceDays" to task.recurrenceDays.take(50),
            "reminderEnabled" to task.reminderEnabled,
            "reminderMinutesBefore" to task.reminderMinutesBefore.coerceIn(0L, 1440L),
            "isImportantDate" to task.isImportantDate,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTask(task: DailyTask): Result<Unit> {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("tasks").document(task.id)
        val updates = mapOf(
            "title" to task.title.trim().take(150).ifEmpty { "Untitled Task" },
            "description" to task.description.trim().take(1000),
            "category" to task.category.trim().take(50).ifEmpty { "Work" },
            "date" to task.date.trim().take(10),
            "startTime" to task.startTime.trim().take(5),
            "endTime" to task.endTime.trim().take(5),
            "completed" to task.completed,
            "priority" to task.priority,
            "recurrence" to task.recurrence,
            "recurrenceDays" to task.recurrenceDays.take(50),
            "reminderEnabled" to task.reminderEnabled,
            "reminderMinutesBefore" to task.reminderMinutesBefore.coerceIn(0L, 1440L),
            "isImportantDate" to task.isImportantDate,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        val updates = mapOf(
            "completed" to completed,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ROUTINES ---

    fun observeRoutines(userId: String = requireUserId()): Flow<List<RoutineItem>> = flow {
        val path = "users/$userId/routines"
        emitAll(
            db.collection("users").document(userId).collection("routines")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(RoutineItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.sortedBy { it.startTime }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createRoutine(routine: RoutineItem): Result<String> {
        val uid = requireUserId()
        val routinesRef = db.collection("users").document(uid).collection("routines")
        val docRef = if (routine.id.isNotBlank()) routinesRef.document(routine.id) else routinesRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to routine.title.trim().take(150).ifEmpty { "Daily Routine" },
            "description" to routine.description.trim().take(500),
            "timeSlot" to routine.timeSlot,
            "startTime" to routine.startTime.trim().take(5),
            "endTime" to routine.endTime.trim().take(5),
            "category" to routine.category.trim().take(50).ifEmpty { "Health" },
            "frequency" to routine.frequency,
            "daysOfWeek" to routine.daysOfWeek.take(30),
            "active" to routine.active,
            "reminderMinutesBefore" to routine.reminderMinutesBefore.coerceIn(0L, 1440L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateRoutine(routine: RoutineItem): Result<Unit> {
        val uid = requireUserId()
        require(routine.id.isNotBlank()) { "Routine ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("routines").document(routine.id)
        val updates = mapOf(
            "title" to routine.title.trim().take(150).ifEmpty { "Daily Routine" },
            "description" to routine.description.trim().take(500),
            "timeSlot" to routine.timeSlot,
            "startTime" to routine.startTime.trim().take(5),
            "endTime" to routine.endTime.trim().take(5),
            "category" to routine.category.trim().take(50).ifEmpty { "Health" },
            "frequency" to routine.frequency,
            "daysOfWeek" to routine.daysOfWeek.take(30),
            "active" to routine.active,
            "reminderMinutesBefore" to routine.reminderMinutesBefore.coerceIn(0L, 1440L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleRoutineActive(routineId: String, active: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("routines").document(routineId)
        val updates = mapOf(
            "active" to active,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(routineId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("routines").document(routineId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- CLOUD BACKUPS & RESTORE ---

    fun observeBackups(userId: String = requireUserId()): Flow<List<CloudBackup>> = flow {
        val path = "users/$userId/backups"
        emitAll(
            db.collection("users").document(userId).collection("backups")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CloudBackup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createBackupSnapshot(
        label: String,
        tasks: List<DailyTask>,
        routines: List<RoutineItem>
    ): Result<String> {
        val uid = requireUserId()
        val payloadJson = serializeBackupPayload(tasks, routines)
        val backupsRef = db.collection("users").document(uid).collection("backups")
        val docRef = backupsRef.document()
        val payload = mapOf(
            "userId" to uid,
            "label" to label.trim().take(100).ifEmpty { "Daily Life Backup" },
            "taskCount" to tasks.size.toLong(),
            "routineCount" to routines.size.toLong(),
            "payloadJson" to payloadJson.take(500000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackupJson(payloadJson: String): Result<Pair<Int, Int>> {
        requireUserId()
        return try {
            val root = JSONObject(payloadJson)
            val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
            val routinesArray = root.optJSONArray("routines") ?: JSONArray()
            var restoredTasks = 0
            var restoredRoutines = 0

            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val task = DailyTask(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", "Restored Task"),
                    description = obj.optString("description", ""),
                    category = obj.optString("category", "Work"),
                    date = obj.optString("date", "2026-11-03"),
                    startTime = obj.optString("startTime", "09:00"),
                    endTime = obj.optString("endTime", "10:00"),
                    completed = obj.optBoolean("completed", false),
                    priority = obj.optString("priority", "Medium"),
                    recurrence = obj.optString("recurrence", "None"),
                    recurrenceDays = obj.optString("recurrenceDays", ""),
                    reminderEnabled = obj.optBoolean("reminderEnabled", true),
                    reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 15L),
                    isImportantDate = obj.optBoolean("isImportantDate", false)
                )
                if (createTask(task).isSuccess) {
                    restoredTasks++
                }
            }

            for (i in 0 until routinesArray.length()) {
                val obj = routinesArray.getJSONObject(i)
                val routine = RoutineItem(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", "Restored Routine"),
                    description = obj.optString("description", ""),
                    timeSlot = obj.optString("timeSlot", "Morning"),
                    startTime = obj.optString("startTime", "07:00"),
                    endTime = obj.optString("endTime", "07:30"),
                    category = obj.optString("category", "Health"),
                    frequency = obj.optString("frequency", "Daily"),
                    daysOfWeek = obj.optString("daysOfWeek", "1,2,3,4,5,6,7"),
                    active = obj.optBoolean("active", true),
                    reminderMinutesBefore = obj.optLong("reminderMinutesBefore", 10L)
                )
                if (createRoutine(routine).isSuccess) {
                    restoredRoutines++
                }
            }
            Result.success(restoredTasks to restoredRoutines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("backups").document(backupId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun serializeBackupPayload(tasks: List<DailyTask>, routines: List<RoutineItem>): String {
        val tasksArr = JSONArray()
        tasks.forEach { task ->
            tasksArr.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("category", task.category)
                    put("date", task.date)
                    put("startTime", task.startTime)
                    put("endTime", task.endTime)
                    put("completed", task.completed)
                    put("priority", task.priority)
                    put("recurrence", task.recurrence)
                    put("recurrenceDays", task.recurrenceDays)
                    put("reminderEnabled", task.reminderEnabled)
                    put("reminderMinutesBefore", task.reminderMinutesBefore)
                    put("isImportantDate", task.isImportantDate)
                }
            )
        }
        val routinesArr = JSONArray()
        routines.forEach { r ->
            routinesArr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("description", r.description)
                    put("timeSlot", r.timeSlot)
                    put("startTime", r.startTime)
                    put("endTime", r.endTime)
                    put("category", r.category)
                    put("frequency", r.frequency)
                    put("daysOfWeek", r.daysOfWeek)
                    put("active", r.active)
                    put("reminderMinutesBefore", r.reminderMinutesBefore)
                }
            )
        }
        return JSONObject().apply {
            put("tasks", tasksArr)
            put("routines", routinesArr)
        }.toString()
    }
}

package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CloudBackup
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.preferences.UserPreferences
import com.example.data.repository.DailyLifeRepository
import com.example.notifications.TaskReminderScheduler
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DailyStats(
    val date: String,
    val totalTasks: Int,
    val completedTasks: Int,
    val pendingTasks: Int,
    val completionPercent: Int,
    val productivityScore: Int,
    val productivityLabel: String
)

data class DayChartBar(
    val label: String,
    val dateStr: String,
    val total: Int,
    val completed: Int,
    val rate: Float
)

class DailyLifeViewModel(
    private val repository: DailyLifeRepository,
    private val preferencesRepository: ThemePreferencesRepository,
    private val reminderScheduler: TaskReminderScheduler,
    private val currentUserId: String
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UserPreferences()
        )

    val tasksState: StateFlow<UiState<List<DailyTask>>> = repository.observeTasks(currentUserId)
        .map<List<DailyTask>, UiState<List<DailyTask>>> { tasks ->
            tasks.forEach { reminderScheduler.scheduleTaskReminder(it) }
            UiState.Success(tasks)
        }
        .catch { error ->
            Log.w(TAG, "Error observing tasks", error)
            emit(UiState.Error(error.message ?: "Failed to observe tasks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val routinesState: StateFlow<UiState<List<RoutineItem>>> = repository.observeRoutines(currentUserId)
        .map<List<RoutineItem>, UiState<List<RoutineItem>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing routines", error)
            emit(UiState.Error(error.message ?: "Failed to observe routines"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val backupsState: StateFlow<UiState<List<CloudBackup>>> = repository.observeBackups(currentUserId)
        .map<List<CloudBackup>, UiState<List<CloudBackup>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing backups", error)
            emit(UiState.Error(error.message ?: "Failed to observe backups"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun selectDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDefaultReminderMinutes(minutes: Long) {
        viewModelScope.launch {
            preferencesRepository.setDefaultReminderMinutes(minutes)
            _statusBannerMessage.value = "Default reminder set to $minutes minutes before task start"
        }
    }

    fun saveTask(task: DailyTask, isEditing: Boolean) {
        viewModelScope.launch {
            val result = if (isEditing && task.id.isNotBlank()) {
                repository.updateTask(task)
            } else {
                repository.createTask(task).map { }
            }
            if (result.isSuccess) {
                _statusBannerMessage.value = if (isEditing) "Task updated" else "Task added to schedule"
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Could not save task"
            }
        }
    }

    fun toggleTaskCompleted(task: DailyTask) {
        viewModelScope.launch {
            val newStatus = !task.completed
            val result = repository.toggleTaskCompleted(task.id, newStatus)
            if (result.isFailure) {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to update task"
            }
        }
    }

    fun deleteTask(task: DailyTask) {
        viewModelScope.launch {
            reminderScheduler.cancelTaskReminder(task.id)
            val result = repository.deleteTask(task.id)
            if (result.isSuccess) {
                _statusBannerMessage.value = "Deleted \"${task.title}\""
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete task"
            }
        }
    }

    fun saveRoutine(routine: RoutineItem, isEditing: Boolean) {
        viewModelScope.launch {
            val result = if (isEditing && routine.id.isNotBlank()) {
                repository.updateRoutine(routine)
            } else {
                repository.createRoutine(routine).map { }
            }
            if (result.isSuccess) {
                _statusBannerMessage.value = if (isEditing) "Routine updated" else "Daily routine saved"
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save routine"
            }
        }
    }

    fun toggleRoutineActive(routine: RoutineItem) {
        viewModelScope.launch {
            repository.toggleRoutineActive(routine.id, !routine.active)
        }
    }

    fun deleteRoutine(routine: RoutineItem) {
        viewModelScope.launch {
            repository.deleteRoutine(routine.id)
            _statusBannerMessage.value = "Routine removed"
        }
    }

    fun applyActiveRoutinesToDate(targetDateStr: String, routines: List<RoutineItem>, existingTasks: List<DailyTask>) {
        viewModelScope.launch {
            val date = try {
                LocalDate.parse(targetDateStr)
            } catch (e: Exception) {
                LocalDate.now()
            }
            val isoDay = date.dayOfWeek.value.toString()
            val applicable = routines.filter { routine ->
                routine.active && (
                    routine.frequency == "Daily" ||
                        (routine.frequency == "Weekdays" && date.dayOfWeek.value in 1..5) ||
                        (routine.frequency == "Weekends" && date.dayOfWeek.value in 6..7) ||
                        routine.daysOfWeek.split(",").map { it.trim() }.contains(isoDay)
                    )
            }

            var addedCount = 0
            for (routine in applicable) {
                val alreadyExists = existingTasks.any {
                    it.date == targetDateStr &&
                        it.title.equals(routine.title, ignoreCase = true) &&
                        it.startTime == routine.startTime
                }
                if (!alreadyExists) {
                    val task = DailyTask(
                        title = routine.title,
                        description = routine.description,
                        category = routine.category,
                        date = targetDateStr,
                        startTime = routine.startTime,
                        endTime = routine.endTime,
                        completed = false,
                        priority = "Medium",
                        recurrence = if (routine.frequency == "Daily") "Daily" else "Weekly",
                        recurrenceDays = routine.daysOfWeek,
                        reminderEnabled = true,
                        reminderMinutesBefore = routine.reminderMinutesBefore,
                        isImportantDate = false
                    )
                    if (repository.createTask(task).isSuccess) {
                        addedCount++
                    }
                }
            }
            _statusBannerMessage.value = if (addedCount > 0) {
                "Added $addedCount routine task${if (addedCount == 1) "" else "s"} to $targetDateStr"
            } else {
                "All active routines are already scheduled for $targetDateStr"
            }
        }
    }

    fun seedStarterRoutinePack(targetDateStr: String) {
        viewModelScope.launch {
            val starterRoutines = listOf(
                RoutineItem(
                    title = "Morning Hydration & Mobility",
                    description = "Drink 500ml water and 10 minutes of full-body stretching",
                    timeSlot = "Morning",
                    startTime = "07:00",
                    endTime = "07:30",
                    category = "Health",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 10L
                ),
                RoutineItem(
                    title = "Deep Focus Priority Block",
                    description = "Tackle the #1 highest-impact deliverable with zero distractions",
                    timeSlot = "Morning",
                    startTime = "09:00",
                    endTime = "11:00",
                    category = "Work",
                    frequency = "Weekdays",
                    daysOfWeek = "1,2,3,4,5",
                    active = true,
                    reminderMinutesBefore = 15L
                ),
                RoutineItem(
                    title = "Midday Walk & Mindful Lunch",
                    description = "Step away from screens, get sunlight, and recharge",
                    timeSlot = "Afternoon",
                    startTime = "12:30",
                    endTime = "13:30",
                    category = "Health",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 10L
                ),
                RoutineItem(
                    title = "Skill Building & Study Session",
                    description = "Read technical docs, practice coding, or review flashcards",
                    timeSlot = "Afternoon",
                    startTime = "16:00",
                    endTime = "17:00",
                    category = "Study",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 15L
                ),
                RoutineItem(
                    title = "Strength & Cardio Workout",
                    description = "45-minute training session for energy and longevity",
                    timeSlot = "Evening",
                    startTime = "18:00",
                    endTime = "18:45",
                    category = "Fitness",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 15L
                ),
                RoutineItem(
                    title = "Next-Day Planning & Wind Down",
                    description = "Review today's wins, plan tomorrow's top 3 tasks, and read",
                    timeSlot = "Night",
                    startTime = "21:30",
                    endTime = "22:00",
                    category = "Personal",
                    frequency = "Daily",
                    daysOfWeek = "1,2,3,4,5,6,7",
                    active = true,
                    reminderMinutesBefore = 10L
                )
            )

            starterRoutines.forEach { repository.createRoutine(it) }

            val starterTasks = listOf(
                DailyTask(
                    title = "Morning Hydration & Mobility",
                    description = "Start the day energized with water and light stretching",
                    category = "Health",
                    date = targetDateStr,
                    startTime = "07:00",
                    endTime = "07:30",
                    completed = true,
                    priority = "Medium",
                    recurrence = "Daily",
                    recurrenceDays = "1,2,3,4,5,6,7",
                    reminderEnabled = true,
                    reminderMinutesBefore = 10L,
                    isImportantDate = false
                ),
                DailyTask(
                    title = "Complete Daily Priority Deliverable",
                    description = "Focus block on core project milestone",
                    category = "Work",
                    date = targetDateStr,
                    startTime = "09:00",
                    endTime = "11:00",
                    completed = false,
                    priority = "High",
                    recurrence = "Daily",
                    recurrenceDays = "1,2,3,4,5",
                    reminderEnabled = true,
                    reminderMinutesBefore = 15L,
                    isImportantDate = true
                ),
                DailyTask(
                    title = "Skill Building & Study Session",
                    description = "45 minutes of focused learning and notes review",
                    category = "Study",
                    date = targetDateStr,
                    startTime = "16:00",
                    endTime = "17:00",
                    completed = false,
                    priority = "Medium",
                    recurrence = "Daily",
                    recurrenceDays = "1,2,3,4,5,6,7",
                    reminderEnabled = true,
                    reminderMinutesBefore = 15L,
                    isImportantDate = false
                ),
                DailyTask(
                    title = "Evening Fitness & Stretch",
                    description = "Cardio + mobility routine",
                    category = "Fitness",
                    date = targetDateStr,
                    startTime = "18:00",
                    endTime = "18:45",
                    completed = false,
                    priority = "High",
                    recurrence = "Daily",
                    recurrenceDays = "1,2,3,4,5,6,7",
                    reminderEnabled = true,
                    reminderMinutesBefore = 15L,
                    isImportantDate = false
                ),
                DailyTask(
                    title = "Next-Day Planning & Gratitude Journal",
                    description = "Reflect on progress and organize tomorrow's timetable",
                    category = "Personal",
                    date = targetDateStr,
                    startTime = "21:30",
                    endTime = "22:00",
                    completed = false,
                    priority = "Low",
                    recurrence = "Daily",
                    recurrenceDays = "1,2,3,4,5,6,7",
                    reminderEnabled = true,
                    reminderMinutesBefore = 10L,
                    isImportantDate = false
                )
            )
            starterTasks.forEach { repository.createTask(it) }
            _statusBannerMessage.value = "Starter daily routines and timetable created!"
        }
    }

    fun sendPendingTasksNotification(tasksForToday: List<DailyTask>) {
        val pending = tasksForToday.filter { !it.completed }
        val sent = reminderScheduler.notifyPendingTasksNow(pending)
        _statusBannerMessage.value = if (sent) {
            "Reminder notification sent (${pending.size} pending task${if (pending.size == 1) "" else "s"})"
        } else {
            "Please allow notification permission to receive task alerts"
        }
    }

    fun createCloudBackup(label: String, tasks: List<DailyTask>, routines: List<RoutineItem>) {
        viewModelScope.launch {
            val result = repository.createBackupSnapshot(label, tasks, routines)
            if (result.isSuccess) {
                _statusBannerMessage.value = "Cloud backup \"$label\" saved!"
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Backup failed"
            }
        }
    }

    fun restoreCloudBackup(backup: CloudBackup) {
        viewModelScope.launch {
            val result = repository.restoreFromBackupJson(backup.payloadJson)
            if (result.isSuccess) {
                val (tasksRestored, routinesRestored) = result.getOrThrow()
                _statusBannerMessage.value = "Restored $tasksRestored tasks & $routinesRestored routines!"
            } else {
                _statusBannerMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Restore failed"
            }
        }
    }

    fun importBackupFromJson(json: String) {
        viewModelScope.launch {
            val result = repository.restoreFromBackupJson(json)
            if (result.isSuccess) {
                val (tasksRestored, routinesRestored) = result.getOrThrow()
                _statusBannerMessage.value = "Imported $tasksRestored tasks & $routinesRestored routines!"
            } else {
                _statusBannerMessage.value = "Invalid backup JSON format"
            }
        }
    }

    fun deleteBackup(backup: CloudBackup) {
        viewModelScope.launch {
            repository.deleteBackup(backup.id)
            _statusBannerMessage.value = "Backup snapshot deleted"
        }
    }

    fun exportBackupJson(tasks: List<DailyTask>, routines: List<RoutineItem>): String {
        return repository.serializeBackupPayload(tasks, routines)
    }

    companion object {
        private const val TAG = "DailyLifeVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun isTaskScheduledForDate(task: DailyTask, targetDateStr: String): Boolean {
            if (task.date == targetDateStr) return true
            val targetDate = try {
                LocalDate.parse(targetDateStr)
            } catch (e: Exception) {
                return false
            }
            val startDate = try {
                LocalDate.parse(task.date)
            } catch (e: Exception) {
                return false
            }
            if (targetDate.isBefore(startDate)) return false

            return when (task.recurrence) {
                "Daily" -> true
                "Weekly" -> {
                    val isoDay = targetDate.dayOfWeek.value.toString()
                    val days = task.recurrenceDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (days.isEmpty()) {
                        targetDate.dayOfWeek == startDate.dayOfWeek
                    } else {
                        days.contains(isoDay)
                    }
                }
                else -> false
            }
        }

        fun calculateDailyStats(tasks: List<DailyTask>, dateStr: String, activeRoutineCount: Int): DailyStats {
            val dayTasks = tasks.filter { isTaskScheduledForDate(it, dateStr) }
            val total = dayTasks.size
            val completed = dayTasks.count { it.completed }
            val pending = (total - completed).coerceAtLeast(0)
            val completionPercent = if (total > 0) ((completed * 100f) / total).toInt() else 0

            val highPriorityTasks = dayTasks.filter { it.priority == "High" }
            val highPriorityBonus = if (highPriorityTasks.isNotEmpty()) {
                val highDone = highPriorityTasks.count { it.completed }
                ((highDone.toFloat() / highPriorityTasks.size) * 10f).toInt()
            } else {
                0
            }
            val routineBonus = if (activeRoutineCount > 0 && completed > 0) 5 else 0
            val rawScore = if (total == 0) 0 else (completionPercent + highPriorityBonus + routineBonus).coerceIn(0, 100)

            val label = when {
                total == 0 -> "Ready to Plan"
                rawScore >= 90 -> "Peak Performance"
                rawScore >= 75 -> "Strong Momentum"
                rawScore >= 50 -> "Steady Progress"
                rawScore > 0 -> "Building Flow"
                else -> "Day Started"
            }

            return DailyStats(
                date = dateStr,
                totalTasks = total,
                completedTasks = completed,
                pendingTasks = pending,
                completionPercent = completionPercent,
                productivityScore = rawScore,
                productivityLabel = label
            )
        }

        fun buildWeeklyChartBars(tasks: List<DailyTask>, referenceDate: LocalDate = LocalDate.now()): List<DayChartBar> {
            val startOfWeek = referenceDate.minusDays(6)
            return (0..6).map { offset ->
                val d = startOfWeek.plusDays(offset.toLong())
                val dStr = d.toString()
                val dayTasks = tasks.filter { isTaskScheduledForDate(it, dStr) }
                val total = dayTasks.size
                val done = dayTasks.count { it.completed }
                val rate = if (total > 0) done.toFloat() / total else 0f
                DayChartBar(
                    label = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    dateStr = dStr,
                    total = total,
                    completed = done,
                    rate = rate
                )
            }
        }

        fun buildMonthlyWeekBars(tasks: List<DailyTask>, referenceDate: LocalDate = LocalDate.now()): List<DayChartBar> {
            val firstOfMonth = referenceDate.withDayOfMonth(1)
            val daysInMonth = referenceDate.lengthOfMonth()
            val buckets = listOf(
                1..7,
                8..14,
                15..21,
                22..daysInMonth
            )
            return buckets.mapIndexed { idx, range ->
                var total = 0
                var done = 0
                for (day in range) {
                    val dStr = firstOfMonth.withDayOfMonth(day).toString()
                    val dayTasks = tasks.filter { isTaskScheduledForDate(it, dStr) }
                    total += dayTasks.size
                    done += dayTasks.count { it.completed }
                }
                val rate = if (total > 0) done.toFloat() / total else 0f
                DayChartBar(
                    label = "Wk ${idx + 1}",
                    dateStr = "${range.first}-${range.last}",
                    total = total,
                    completed = done,
                    rate = rate
                )
            }
        }

        fun formatReadableDate(dateStr: String): String {
            return try {
                val date = LocalDate.parse(dateStr)
                date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault()))
            } catch (e: Exception) {
                dateStr
            }
        }

        fun getTimeSlotForTime(timeStr: String): String {
            val hour = timeStr.take(2).toIntOrNull() ?: 9
            return when (hour) {
                in 4..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..20 -> "Evening"
                else -> "Night"
            }
        }

        val DAY_CHIPS = listOf(
            DayOfWeek.MONDAY to "M",
            DayOfWeek.TUESDAY to "T",
            DayOfWeek.WEDNESDAY to "W",
            DayOfWeek.THURSDAY to "T",
            DayOfWeek.FRIDAY to "F",
            DayOfWeek.SATURDAY to "S",
            DayOfWeek.SUNDAY to "S"
        )
    }
}

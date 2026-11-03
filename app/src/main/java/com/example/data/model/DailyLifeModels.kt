package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class DailyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Work",
    val date: String = "", // YYYY-MM-DD
    val startTime: String = "09:00", // HH:mm
    val endTime: String = "10:00", // HH:mm
    val completed: Boolean = false,
    val priority: String = "Medium", // High, Medium, Low
    val recurrence: String = "None", // None, Daily, Weekly
    val recurrenceDays: String = "", // Comma-separated 1..7 (Mon..Sun)
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Long = 15L,
    val isImportantDate: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoutineItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val timeSlot: String = "Morning", // Morning, Afternoon, Evening, Night
    val startTime: String = "07:00",
    val endTime: String = "07:30",
    val category: String = "Health",
    val frequency: String = "Daily", // Daily, Weekdays, Weekends, Weekly
    val daysOfWeek: String = "1,2,3,4,5,6,7",
    val active: Boolean = true,
    val reminderMinutesBefore: Long = 10L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBackup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val taskCount: Long = 0L,
    val routineCount: Long = 0L,
    val payloadJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class TaskCategory(val label: String, val colorHex: Long) {
    WORK("Work", 0xFF3B82F6),
    HEALTH("Health", 0xFF10B981),
    STUDY("Study", 0xFF8B5CF6),
    PERSONAL("Personal", 0xFFF59E0B),
    FITNESS("Fitness", 0xFFEC4899),
    FINANCE("Finance", 0xFF14B8A6),
    FAMILY("Family", 0xFFF97316);

    companion object {
        fun fromLabel(label: String): TaskCategory =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: WORK
    }
}

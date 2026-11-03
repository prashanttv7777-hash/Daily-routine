package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.example.data.model.TaskCategory
import com.example.ui.viewmodel.DailyLifeViewModel
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
    initialTask: DailyTask?,
    defaultDate: String,
    defaultReminderMinutes: Long,
    onDismiss: () -> Unit,
    onSave: (DailyTask, Boolean) -> Unit
) {
    val context = LocalContext.current
    val isEditing = initialTask != null && initialTask.id.isNotBlank()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var category by remember { mutableStateOf(initialTask?.category ?: TaskCategory.WORK.label) }
    var date by remember { mutableStateOf(initialTask?.date?.ifBlank { defaultDate } ?: defaultDate) }
    var startTime by remember { mutableStateOf(initialTask?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initialTask?.endTime ?: "10:00") }
    var priority by remember { mutableStateOf(initialTask?.priority ?: "Medium") }
    var recurrence by remember { mutableStateOf(initialTask?.recurrence ?: "None") }
    var selectedDays by remember {
        val initialSet = initialTask?.recurrenceDays
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet() ?: setOf(1, 2, 3, 4, 5)
        mutableStateOf(initialSet)
    }
    var reminderEnabled by remember { mutableStateOf(initialTask?.reminderEnabled ?: true) }
    var reminderMinutes by remember {
        mutableLongStateOf(initialTask?.reminderMinutesBefore ?: defaultReminderMinutes)
    }
    var isImportantDate by remember { mutableStateOf(initialTask?.isImportantDate ?: false) }
    var titleError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Task" else "Create New Task",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("Task Title *") },
                placeholder = { Text("e.g., Prepare Quarterly Presentation") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Please enter a task title") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input"),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description & Notes") },
                placeholder = { Text("Add checklist details, links, or focus objectives...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_description_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Date & Time Pickers
            Text(
                text = "Date & Schedule Time",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val parsed = try {
                            LocalDate.parse(date)
                        } catch (e: Exception) {
                            LocalDate.now()
                        }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                            },
                            parsed.year,
                            parsed.monthValue - 1,
                            parsed.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = date, style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val h = startTime.take(2).toIntOrNull() ?: 9
                        val min = startTime.takeLast(2).toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                startTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            },
                            h,
                            min,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = startTime, style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val h = endTime.take(2).toIntOrNull() ?: 10
                        val min = endTime.takeLast(2).toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                endTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            },
                            h,
                            min,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = endTime, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Category Selection
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskCategory.entries.forEach { cat ->
                    val selected = category.equals(cat.label, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { category = cat.label },
                        label = { Text(cat.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(cat.colorHex))
                            )
                        }
                    )
                }
            }

            // Priority Selection
            Text(
                text = "Priority Level",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("High", "Medium", "Low").forEach { level ->
                    FilterChip(
                        selected = priority == level,
                        onClick = { priority = level },
                        label = { Text(level) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Recurrence Selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Recurrence",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("None", "Daily", "Weekly").forEach { rec ->
                    FilterChip(
                        selected = recurrence == rec,
                        onClick = { recurrence = rec },
                        label = { Text(rec) }
                    )
                }
            }

            if (recurrence == "Weekly") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DailyLifeViewModel.DAY_CHIPS.forEach { (dow, shortLabel) ->
                        val dayNum = dow.value
                        val isSelected = selectedDays.contains(dayNum)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    selectedDays = if (isSelected && selectedDays.size > 1) {
                                        selectedDays - dayNum
                                    } else {
                                        selectedDays + dayNum
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Important Date Highlight Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Highlight as Important Date",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Marks this date with a star badge on the Calendar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isImportantDate,
                    onCheckedChange = { isImportantDate = it }
                )
            }

            // Reminder Toggle & Minutes Customization
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Task Reminder Notification",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Notify $reminderMinutes minutes before task starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            if (reminderEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0L to "At start", 5L to "5m", 10L to "10m", 15L to "15m", 30L to "30m", 60L to "1 hr").forEach { (mins, label) ->
                        FilterChip(
                            selected = reminderMinutes == mins,
                            onClick = { reminderMinutes = mins },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                            return@Button
                        }
                        val updatedTask = DailyTask(
                            id = initialTask?.id ?: "",
                            userId = initialTask?.userId ?: "",
                            title = title.trim(),
                            description = description.trim(),
                            category = category,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            completed = initialTask?.completed ?: false,
                            priority = priority,
                            recurrence = recurrence,
                            recurrenceDays = if (recurrence == "Weekly") {
                                selectedDays.sorted().joinToString(",")
                            } else if (recurrence == "Daily") {
                                "1,2,3,4,5,6,7"
                            } else {
                                ""
                            },
                            reminderEnabled = reminderEnabled,
                            reminderMinutesBefore = reminderMinutes,
                            isImportantDate = isImportantDate,
                            createdAt = initialTask?.createdAt,
                            updatedAt = initialTask?.updatedAt
                        )
                        onSave(updatedTask, isEditing)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("save_task_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (isEditing) "Update Task" else "Save Task", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorSheet(
    initialRoutine: RoutineItem?,
    onDismiss: () -> Unit,
    onSave: (RoutineItem, Boolean) -> Unit
) {
    val context = LocalContext.current
    val isEditing = initialRoutine != null && initialRoutine.id.isNotBlank()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialRoutine?.title ?: "") }
    var description by remember { mutableStateOf(initialRoutine?.description ?: "") }
    var timeSlot by remember { mutableStateOf(initialRoutine?.timeSlot ?: "Morning") }
    var startTime by remember { mutableStateOf(initialRoutine?.startTime ?: "07:00") }
    var endTime by remember { mutableStateOf(initialRoutine?.endTime ?: "07:30") }
    var category by remember { mutableStateOf(initialRoutine?.category ?: TaskCategory.HEALTH.label) }
    var frequency by remember { mutableStateOf(initialRoutine?.frequency ?: "Daily") }
    var reminderMinutes by remember { mutableLongStateOf(initialRoutine?.reminderMinutesBefore ?: 10L) }
    var titleError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Daily Routine" else "New Routine Habit",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("Routine Title *") },
                placeholder = { Text("e.g., Morning Hydration & Stretch") },
                isError = titleError,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_title_input"),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Routine Details") },
                placeholder = { Text("Steps or habit cues...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Text(text = "Time of Day Slot", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Morning", "Afternoon", "Evening", "Night").forEach { slot ->
                    FilterChip(
                        selected = timeSlot == slot,
                        onClick = { timeSlot = slot },
                        label = { Text(slot) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val h = startTime.take(2).toIntOrNull() ?: 7
                        val min = startTime.takeLast(2).toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                startTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                                timeSlot = DailyLifeViewModel.getTimeSlotForTime(startTime)
                            },
                            h,
                            min,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start: $startTime")
                }

                OutlinedButton(
                    onClick = {
                        val h = endTime.take(2).toIntOrNull() ?: 7
                        val min = endTime.takeLast(2).toIntOrNull() ?: 30
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                endTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
                            },
                            h,
                            min,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("End: $endTime")
                }
            }

            Text(text = "Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category.equals(cat.label, ignoreCase = true),
                        onClick = { category = cat.label },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text(text = "Frequency", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Daily", "Weekdays", "Weekends", "Weekly").forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(freq) }
                    )
                }
            }

            Text(text = "Reminder Before Routine", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5L to "5m", 10L to "10m", 15L to "15m", 30L to "30m").forEach { (mins, label) ->
                    FilterChip(
                        selected = reminderMinutes == mins,
                        onClick = { reminderMinutes = mins },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                            return@Button
                        }
                        val daysStr = when (frequency) {
                            "Weekdays" -> "1,2,3,4,5"
                            "Weekends" -> "6,7"
                            else -> "1,2,3,4,5,6,7"
                        }
                        val item = RoutineItem(
                            id = initialRoutine?.id ?: "",
                            userId = initialRoutine?.userId ?: "",
                            title = title.trim(),
                            description = description.trim(),
                            timeSlot = timeSlot,
                            startTime = startTime,
                            endTime = endTime,
                            category = category,
                            frequency = frequency,
                            daysOfWeek = daysStr,
                            active = initialRoutine?.active ?: true,
                            reminderMinutesBefore = reminderMinutes,
                            createdAt = initialRoutine?.createdAt,
                            updatedAt = initialRoutine?.updatedAt
                        )
                        onSave(item, isEditing)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp)
                        .testTag("save_routine_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (isEditing) "Update Routine" else "Save Routine", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

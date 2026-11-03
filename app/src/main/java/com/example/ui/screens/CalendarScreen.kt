package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.ui.components.TaskItemCard
import com.example.ui.viewmodel.DailyLifeViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarViewMode(val label: String) {
    MONTHLY("Month"),
    WEEKLY("Week"),
    DAILY("Day")
}

@Composable
fun CalendarScreen(
    selectedDateStr: String,
    allTasks: List<DailyTask>,
    onSelectDate: (String) -> Unit,
    onAddTaskForDate: (String) -> Unit,
    onEditTask: (DailyTask) -> Unit,
    onToggleTask: (DailyTask) -> Unit,
    onDeleteTask: (DailyTask) -> Unit
) {
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTHLY) }
    val selectedDate = remember(selectedDateStr) {
        try {
            LocalDate.parse(selectedDateStr)
        } catch (e: Exception) {
            LocalDate.now()
        }
    }
    var displayedYearMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate))
    }

    val tasksForSelectedDate = remember(allTasks, selectedDateStr) {
        allTasks.filter { DailyLifeViewModel.isTaskScheduledForDate(it, selectedDateStr) }
            .sortedBy { it.startTime }
    }

    val importantMilestoneTasks = remember(allTasks) {
        allTasks.filter { it.isImportantDate }.sortedBy { it.date }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Schedule Calendar",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Manage daily, weekly & monthly plans",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        val today = LocalDate.now()
                        displayedYearMonth = YearMonth.from(today)
                        onSelectDate(today.toString())
                    }
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Today")
                }
            }
        }

        // View Mode Switcher (Monthly, Weekly, Daily)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalendarViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        label = { Text("${mode.label} View") },
                        modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // Calendar Grid Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Month/Week Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                when (viewMode) {
                                    CalendarViewMode.MONTHLY -> displayedYearMonth = displayedYearMonth.minusMonths(1)
                                    CalendarViewMode.WEEKLY -> onSelectDate(selectedDate.minusWeeks(1).toString())
                                    CalendarViewMode.DAILY -> onSelectDate(selectedDate.minusDays(1).toString())
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                        }

                        val headerText = when (viewMode) {
                            CalendarViewMode.MONTHLY -> "${displayedYearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayedYearMonth.year}"
                            CalendarViewMode.WEEKLY -> "Week of ${selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())}"
                            CalendarViewMode.DAILY -> DailyLifeViewModel.formatReadableDate(selectedDateStr)
                        }
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                when (viewMode) {
                                    CalendarViewMode.MONTHLY -> displayedYearMonth = displayedYearMonth.plusMonths(1)
                                    CalendarViewMode.WEEKLY -> onSelectDate(selectedDate.plusWeeks(1).toString())
                                    CalendarViewMode.DAILY -> onSelectDate(selectedDate.plusDays(1).toString())
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (viewMode) {
                        CalendarViewMode.MONTHLY -> {
                            MonthCalendarGrid(
                                yearMonth = displayedYearMonth,
                                selectedDate = selectedDate,
                                allTasks = allTasks,
                                onSelectDate = onSelectDate
                            )
                        }
                        CalendarViewMode.WEEKLY -> {
                            WeekCalendarStrip(
                                selectedDate = selectedDate,
                                allTasks = allTasks,
                                onSelectDate = onSelectDate
                            )
                        }
                        CalendarViewMode.DAILY -> {
                            Text(
                                text = "${tasksForSelectedDate.size} scheduled item${if (tasksForSelectedDate.size == 1) "" else "s"} on this day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Important Highlighted Dates Banner
        if (importantMilestoneTasks.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Highlighted Important Dates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        importantMilestoneTasks.take(4).forEach { milestone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDate(milestone.date) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = milestone.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = milestone.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Tasks Header + Add Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DailyLifeViewModel.formatReadableDate(selectedDateStr),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${tasksForSelectedDate.count { it.completed }}/${tasksForSelectedDate.size} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onAddTaskForDate(selectedDateStr) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("calendar_add_task_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add on Date")
                }
            }
        }

        if (tasksForSelectedDate.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "No tasks scheduled for $selectedDateStr. Tap \"Add on Date\" to plan ahead!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(tasksForSelectedDate, key = { it.id }) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleTask(task) },
                    onEdit = { onEditTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    allTasks: List<DailyTask>,
    onSelectDate: (String) -> Unit
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(modifier = Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Text(
                text = day,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    val firstOfMonth = yearMonth.atDay(1)
    val startOffset = firstOfMonth.dayOfWeek.value - 1
    val totalDays = yearMonth.lengthOfMonth()
    val totalCells = ((startOffset + totalDays + 6) / 7) * 7

    for (week in 0 until (totalCells / 7)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (dow in 0..6) {
                val cellIndex = week * 7 + dow
                val dayNumber = cellIndex - startOffset + 1
                if (dayNumber in 1..totalDays) {
                    val cellDate = yearMonth.atDay(dayNumber)
                    val cellDateStr = cellDate.toString()
                    val isSelected = cellDate == selectedDate
                    val isToday = cellDate == LocalDate.now()
                    val dayTasks = allTasks.filter { it.date == cellDateStr }
                    val hasImportant = dayTasks.any { it.isImportantDate }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    hasImportant -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .then(
                                if (isToday && !isSelected) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                } else Modifier
                            )
                            .clickable { onSelectDate(cellDateStr) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    hasImportant -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (dayTasks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else if (hasImportant) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.primary
                                        )
                                )
                            }
                        }
                    }
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCalendarStrip(
    selectedDate: LocalDate,
    allTasks: List<DailyTask>,
    onSelectDate: (String) -> Unit
) {
    val startOfWeek = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0..6) {
            val d = startOfWeek.plusDays(i.toLong())
            val dStr = d.toString()
            val isSelected = d == selectedDate
            val count = allTasks.count { DailyLifeViewModel.isTaskScheduledForDate(it, dStr) }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDate(dStr) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

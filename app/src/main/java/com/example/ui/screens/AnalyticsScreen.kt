package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudBackup
import com.example.data.model.DailyTask
import com.example.data.model.RoutineItem
import com.example.data.model.TaskCategory
import com.example.ui.viewmodel.DailyLifeViewModel
import com.example.ui.viewmodel.DailyStats
import com.example.ui.viewmodel.DayChartBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    todayStats: DailyStats,
    allTasks: List<DailyTask>,
    allRoutines: List<RoutineItem>,
    backups: List<CloudBackup>,
    defaultReminderMinutes: Long,
    onUpdateDefaultReminder: (Long) -> Unit,
    onSendPendingNotification: () -> Unit,
    onCreateCloudBackup: (String) -> Unit,
    onRestoreBackup: (CloudBackup) -> Unit,
    onDeleteBackup: (CloudBackup) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Unit
) {
    var reportPeriod by remember { mutableStateOf("Weekly") } // Weekly or Monthly
    var backupLabelInput by remember { mutableStateOf("") }
    var showJsonDialog by remember { mutableStateOf(false) }
    var jsonDialogMode by remember { mutableStateOf("Export") }
    var jsonText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    val weeklyBars = remember(allTasks) { DailyLifeViewModel.buildWeeklyChartBars(allTasks) }
    val monthlyBars = remember(allTasks) { DailyLifeViewModel.buildMonthlyWeekBars(allTasks) }
    val activeBars = if (reportPeriod == "Weekly") weeklyBars else monthlyBars

    val totalCompletedAllTime = allTasks.count { it.completed }
    val totalPendingAllTime = allTasks.count { !it.completed }
    val overallRate = if (allTasks.isNotEmpty()) {
        ((totalCompletedAllTime * 100f) / allTasks.size).toInt()
    } else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Progress & Performance",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Weekly & monthly reports, custom reminders, and cloud backups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Overall Summary KPI Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Daily Score",
                    value = "${todayStats.productivityScore}/100",
                    subtitle = todayStats.productivityLabel
                )
                KpiSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Completion Rate",
                    value = "$overallRate%",
                    subtitle = "$totalCompletedAllTime done • $totalPendingAllTime pending"
                )
            }
        }

        // 2. Weekly & Monthly Progress Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$reportPeriod Progress Report",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Completed vs Total scheduled tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Weekly", "Monthly").forEach { period ->
                                FilterChip(
                                    selected = reportPeriod == period,
                                    onClick = { reportPeriod = period },
                                    label = { Text(period) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    BarChartCanvas(bars = activeBars)
                }
            }
        }

        // 3. Category Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Category Performance Breakdown",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TaskCategory.entries.forEach { cat ->
                        val catTasks = allTasks.filter { it.category.equals(cat.label, ignoreCase = true) }
                        if (catTasks.isNotEmpty() || cat == TaskCategory.WORK || cat == TaskCategory.HEALTH) {
                            val done = catTasks.count { it.completed }
                            val total = catTasks.size
                            val ratio = if (total > 0) done.toFloat() / total else 0f
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(cat.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = cat.label, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Text(
                                        text = "$done/$total completed (${(ratio * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = Color(cat.colorHex),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Reminder & Notification Preferences Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Reminder & Notification Settings",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Default lead time before scheduled tasks start",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5L to "5 min", 10L to "10 min", 15L to "15 min", 30L to "30 min", 60L to "60 min").forEach { (mins, label) ->
                            FilterChip(
                                selected = defaultReminderMinutes == mins,
                                onClick = { onUpdateDefaultReminder(mins) },
                                label = { Text(label) }
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onSendPendingNotification,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Pending Tasks Summary Notification Now")
                    }
                }
            }
        }

        // 5. Data Management: Cloud Backup & Restore + JSON Export/Import
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Backup & Restore Center",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Save permanent snapshots or export/import JSON data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = backupLabelInput,
                            onValueChange = { backupLabelInput = it },
                            placeholder = { Text("Snapshot label (e.g., Weekly Backup)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_label_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                val label = backupLabelInput.ifBlank {
                                    "Snapshot ${SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date())}"
                                }
                                onCreateCloudBackup(label)
                                backupLabelInput = ""
                            },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("create_backup_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                jsonText = onExportJson()
                                jsonDialogMode = "Export"
                                showJsonDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }

                        OutlinedButton(
                            onClick = {
                                jsonText = ""
                                jsonDialogMode = "Import"
                                showJsonDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }
        }

        if (backups.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Cloud Snapshots (${backups.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(backups, key = { it.id }) { backup ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = backup.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${backup.taskCount} tasks • ${backup.routineCount} routines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            FilledTonalButton(
                                onClick = { onRestoreBackup(backup) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore")
                            }
                            IconButton(
                                onClick = { onDeleteBackup(backup) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete backup",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showJsonDialog) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = {
                Text(if (jsonDialogMode == "Export") "Export Backup JSON" else "Import Backup JSON")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (jsonDialogMode == "Export") {
                            "Copy this JSON to save a portable backup of all your tasks and daily routines."
                        } else {
                            "Paste a previously exported JSON backup below to restore your tasks and routines."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        readOnly = jsonDialogMode == "Export",
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                if (jsonDialogMode == "Export") {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(jsonText))
                            showJsonDialog = false
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy to Clipboard")
                    }
                } else {
                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                onImportJson(jsonText)
                            }
                            showJsonDialog = false
                        }
                    ) {
                        Text("Restore Now")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BarChartCanvas(bars: List<DayChartBar>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (bars.isEmpty()) return@Canvas
            val count = bars.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.48f).coerceAtMost(42.dp.toPx())
            val maxBarHeight = size.height - 16.dp.toPx()

            bars.forEachIndexed { index, bar ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val bgTop = size.height - maxBarHeight
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, bgTop),
                    size = Size(barWidth, maxBarHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                val fillRatio = bar.rate.coerceIn(0.06f, 1f)
                val fillHeight = if (bar.total == 0) 6.dp.toPx() else maxBarHeight * fillRatio
                val fillTop = size.height - fillHeight
                drawRoundRect(
                    color = if (bar.total == 0) trackColor else primaryColor,
                    topLeft = Offset(x, fillTop),
                    size = Size(barWidth, fillHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${bar.completed}/${bar.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

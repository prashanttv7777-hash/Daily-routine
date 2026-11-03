package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DailyTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Upcoming Task Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "It's time to focus on your scheduled task!"
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)

        TaskReminderScheduler.ensureChannel(context)
        TaskReminderScheduler.showImmediateNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message
        )
    }

    companion object {
        const val EXTRA_TITLE = "extra_task_title"
        const val EXTRA_MESSAGE = "extra_task_message"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}

class TaskReminderScheduler(private val context: Context) {

    init {
        ensureChannel(context)
    }

    fun scheduleTaskReminder(task: DailyTask) {
        if (!task.reminderEnabled || task.completed || task.id.isBlank()) {
            cancelTaskReminder(task.id)
            return
        }

        val triggerMillis = computeTriggerMillis(
            dateStr = task.date,
            startTimeStr = task.startTime,
            minutesBefore = task.reminderMinutesBefore
        ) ?: return

        if (triggerMillis <= System.currentTimeMillis()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TITLE, "Reminder: ${task.title}")
            putExtra(
                TaskReminderReceiver.EXTRA_MESSAGE,
                "${task.category} • Starts at ${task.startTime} (${task.reminderMinutesBefore} min reminder)"
            )
            putExtra(TaskReminderReceiver.EXTRA_NOTIFICATION_ID, task.id.hashCode())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Fallback if exact alarm is restricted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    fun cancelTaskReminder(taskId: String) {
        if (taskId.isBlank()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun notifyPendingTasksNow(pendingTasks: List<DailyTask>): Boolean {
        ensureChannel(context)
        val title = if (pendingTasks.isEmpty()) {
            "All Caught Up! 🎉"
        } else {
            "${pendingTasks.size} Pending Task${if (pendingTasks.size == 1) "" else "s"} Today"
        }
        val body = if (pendingTasks.isEmpty()) {
            "You have completed all scheduled tasks for today. Great job!"
        } else {
            val preview = pendingTasks.take(3).joinToString(", ") { "${it.startTime} ${it.title}" }
            "Next up: $preview"
        }
        return showImmediateNotification(
            context = context,
            notificationId = 9991,
            title = title,
            message = body
        )
    }

    private fun computeTriggerMillis(dateStr: String, startTimeStr: String, minutesBefore: Long): Long? {
        return try {
            val date = LocalDate.parse(dateStr)
            val time = LocalTime.parse(startTimeStr)
            val dateTime = LocalDateTime.of(date, time).minusMinutes(minutesBefore)
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_ID = "daily_life_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun showImmediateNotification(
            context: Context,
            notificationId: Int,
            title: String,
            message: String
        ): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return false
            }

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingTap = PendingIntent.getActivity(
                context,
                notificationId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingTap)
                .build()

            return try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}

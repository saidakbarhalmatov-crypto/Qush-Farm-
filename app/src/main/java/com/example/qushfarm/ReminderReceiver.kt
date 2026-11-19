package com.example.qushfarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Восстанавливаем все активные напоминания после перезагрузки телефона
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = FarmDatabase.getDatabase(context).farmDao()
                    val activeReminders = dao.getEnabledRemindersSync()
                    activeReminders.forEach { ReminderScheduler.scheduleReminder(context, it) }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val id = intent.getIntExtra("REMINDER_ID", 0)
        val type = intent.getStringExtra("REMINDER_TYPE") ?: "FEED"
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Напоминание QUSH FARM"
        val hour = intent.getIntExtra("REMINDER_HOUR", 8)
        val minute = intent.getIntExtra("REMINDER_MINUTE", 0)
        val dayOfWeek = intent.getIntExtra("REMINDER_DAY", -1).let { if (it == -1) null else it }

        showNotification(context, id, type, title)

        // Планируем следующее срабатывание (на завтра или через неделю)
        val reminder = ReminderEntity(
            id = id,
            type = type,
            title = title,
            hour = hour,
            minute = minute,
            dayOfWeek = dayOfWeek,
            isEnabled = true
        )
        ReminderScheduler.scheduleReminder(context, reminder)
    }

    private fun showNotification(context: Context, id: Int, type: String, title: String) {
        val channelId = "qush_farm_reminders"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания фермы QUSH FARM",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о кормлении, сборе яиц и уборке курятника"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val (emoji, bodyText) = when (type) {
            "FEED" -> "🌾" to "Пора покормить кур! ($title)"
            "EGGS" -> "🥚" to "Пора собрать яйца в курятнике! ($title)"
            "CLEANING" -> "🧹" to "Еженедельная уборка за курами ($title)"
            else -> "🐔" to title
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("$emoji QUSH FARM")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        notificationManager.notify(id + 1000, notification)
    }
}

object ReminderScheduler {

    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        if (!reminder.isEnabled) {
            cancelReminder(context, reminder.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TYPE", reminder.type)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_HOUR", reminder.hour)
            putExtra("REMINDER_MINUTE", reminder.minute)
            putExtra("REMINDER_DAY", reminder.dayOfWeek ?: -1)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (reminder.dayOfWeek != null) {
                set(Calendar.DAY_OF_WEEK, reminder.dayOfWeek)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            } else {
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

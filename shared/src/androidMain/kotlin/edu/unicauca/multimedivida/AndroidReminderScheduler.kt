package edu.unicauca.multimedivida.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.LocalDateTime

private val androidScheduler = AndroidReminderScheduler()

actual fun platformReminderScheduler(): ReminderScheduler = androidScheduler

fun configureAndroidReminderScheduler(context: Context) {
    androidScheduler.configure(context.applicationContext)
}

private class AndroidReminderScheduler : ReminderScheduler {
    private var appContext: Context? = null
    private val lock = Any()

    fun configure(context: Context) {
        appContext = context
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Medicamentos y citas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos de medicamentos y citas programadas en MediVida"
                }
            )
        }
    }

    override fun rebuild(repository: HealthRepository) {
        val context = checkNotNull(appContext) { "El scheduler de Android no está configurado." }
        val snapshot = repository.snapshot()
        val notificationsEnabled = snapshot.profile.notifications
        val active = if (notificationsEnabled) snapshot.records.filterNot { it.archived } else emptyList()
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val storedSnoozes = if (notificationsEnabled) prefs.all.keys.filter { it.startsWith(SNOOZE_PREFIX) } else emptyList()
        val snoozePlans = storedSnoozes.mapNotNull { key ->
            val recordId = key.removePrefix(SNOOZE_PREFIX)
            val record = active.firstOrNull { it.id == recordId } ?: run { prefs.edit().remove(key).apply(); return@mapNotNull null }
            val trigger = prefs.getLong(key, 0L)
            if (trigger <= System.currentTimeMillis()) {
                prefs.edit().remove(key).apply()
                null
            } else {
                val occurrence = prefs.getString("$key:occurrence", "") ?: ""
                ReminderPlan(
                    id = "$recordId:snooze",
                    recordId = recordId,
                    occurrence = occurrence,
                    title = "Recordatorio de medicamento",
                    message = listOf(record.title, record.detail).filter(String::isNotBlank).joinToString(" · "),
                    atEpochMillis = trigger
                )
            }
        }

        synchronized(lock) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val previousIds = prefs.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty()
            previousIds.forEach { cancelAlarm(context, alarmManager, it) }

            val plans = if (notificationsEnabled) {
                (ReminderPlanner.plans(active, LocalDateTime.now()) + snoozePlans).distinctBy { it.id }
            } else emptyList()
            plans.forEach { plan ->
                val pendingIntent = pendingIntent(context, plan)
                val triggerAt = maxOf(System.currentTimeMillis() + 500L, plan.atEpochMillis)
                // Inexact idle-safe alarms avoid requesting the special exact-alarm permission.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            prefs.edit().putStringSet(SCHEDULED_IDS, plans.map { it.id }.toSet()).apply()
        }
    }

    override fun cancel() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        synchronized(lock) {
            prefs.getStringSet(SCHEDULED_IDS, emptySet()).orEmpty().forEach { cancelAlarm(context, alarmManager, it) }
            prefs.edit().clear().apply()
        }
    }

    override fun snooze(repository: HealthRepository, recordId: String, occurrence: String) {
        if (!repository.snapshot().profile.notifications) return
        val context = checkNotNull(appContext) { "El scheduler de Android no está configurado." }
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$SNOOZE_PREFIX$recordId"
        prefs.edit().putLong(key, System.currentTimeMillis() + SNOOZE_MILLIS).putString("$key:occurrence", occurrence).apply()
    }

    override fun onReminderFired(recordId: String) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$SNOOZE_PREFIX$recordId"
        prefs.edit().remove(key).remove("$key:occurrence").apply()
        runCatching { rebuild(persistentHealthRepository()) }
    }

    private fun cancelAlarm(context: Context, manager: AlarmManager, id: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("multimedivida://reminder/${Uri.encode(id)}")
        }
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun pendingIntent(context: Context, plan: ReminderPlan): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = Uri.parse("multimedivida://reminder/${Uri.encode(plan.id)}")
            putExtra(EXTRA_RECORD_ID, plan.recordId)
            putExtra(EXTRA_OCCURRENCE, plan.occurrence)
            putExtra(EXTRA_TITLE, plan.title)
            putExtra(EXTRA_MESSAGE, plan.message)
        }
        return PendingIntent.getBroadcast(
            context,
            plan.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "medivida_reminders"
        const val ACTION_REMINDER = "edu.unicauca.multimedivida.REMINDER"
        const val ACTION_REBUILD = "edu.unicauca.multimedivida.REBUILD_REMINDERS"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        private const val PREFERENCES = "reminder_schedule"
        private const val SCHEDULED_IDS = "scheduled_plan_ids"
        private const val SNOOZE_PREFIX = "snooze:"
        private const val SNOOZE_MILLIS = 10 * 60 * 1000L
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        configureHealthDatabase(context.applicationContext)
        configureAndroidReminderScheduler(context)
        val pending = goAsync()
        Thread {
            try {
                val recordId = intent.getStringExtra(AndroidReminderScheduler.EXTRA_RECORD_ID).orEmpty()
                val occurrence = intent.getStringExtra(AndroidReminderScheduler.EXTRA_OCCURRENCE).orEmpty()
                val repository = persistentHealthRepository()
                val snapshot = repository.snapshot()
                val record = snapshot.records.firstOrNull { it.id == recordId && !it.archived }
                val isAlreadyCompleted = snapshot.completions.any { it.recordId == recordId && it.occurrence == occurrence }
                if (snapshot.profile.notifications && record != null && !isAlreadyCompleted) {
                    showNotification(
                        context,
                        intent.getStringExtra(AndroidReminderScheduler.EXTRA_TITLE) ?: "Recordatorio de MediVida",
                        intent.getStringExtra(AndroidReminderScheduler.EXTRA_MESSAGE) ?: record.title,
                        recordId.hashCode()
                    )
                }
                androidScheduler.onReminderFired(recordId)
            } catch (error: Exception) {
                android.util.Log.e("MediVidaReminder", "No se pudo procesar el recordatorio", error)
            } finally {
                pending.finish()
            }
        }.apply { name = "medivida-reminder-receiver"; isDaemon = true }.start()
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        configureHealthDatabase(context.applicationContext)
        configureAndroidReminderScheduler(context)
        val pending = goAsync()
        Thread {
            try {
                androidScheduler.rebuild(persistentHealthRepository())
            } catch (error: Exception) {
                android.util.Log.e("MediVidaReminder", "No se pudieron recuperar los recordatorios", error)
            } finally {
                pending.finish()
            }
        }.apply { name = "medivida-reminder-restore"; isDaemon = true }.start()
    }
}

private fun showNotification(context: Context, title: String, message: String, id: Int) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(AndroidReminderScheduler.CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(AndroidReminderScheduler.CHANNEL_ID, "Medicamentos y citas", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, AndroidReminderScheduler.CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION") Notification.Builder(context)
    }.setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(Notification.BigTextStyle().bigText(message))
        .setAutoCancel(true)
        .build()
    if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        manager.notify(id, notification)
    }
}

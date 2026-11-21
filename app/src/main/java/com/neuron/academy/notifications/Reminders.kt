package com.neuron.academy.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuron.academy.MainActivity
import com.neuron.academy.R
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Дружелюбные напоминания без давления. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val messages = listOf(
        "Нейрон ждёт: пара задач займёт минут пять",
        "Серия дней продолжится, если пройти одну тему сегодня",
        "Задача дня уже ждёт решения",
        "Задача дня готова. Заглянешь?",
        "Одна тема в день — 30 тем за месяц",
        "За пройденные темы дают карточки учёных и элементов",
    )

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val intent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Нейрон: Академия")
            .setContentText(messages.random())
            .setStyle(NotificationCompat.BigTextStyle())
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (_: SecurityException) {
        }
        return Result.success()
    }
}

object ReminderScheduler {
    const val CHANNEL = "neuron_reminders"
    private const val WORK = "daily_reminder"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Ежедневные дружелюбные напоминания о занятиях"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}

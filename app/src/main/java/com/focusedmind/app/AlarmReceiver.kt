package com.focusedmind.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        when (intent.action) {
            ACTION_COMPLETE -> {
                FocusedMindStore.markCompleted(context, id)
                FocusedMindStore.cancelCommitmentNotifications(context, id)
                AppEvents.storeChanged(context, id)
                return
            }
            ACTION_NOT_COMPLETED -> {
                FocusedMindStore.markNotCompleted(context, id)
                FocusedMindStore.cancelCommitmentNotifications(context, id)
                AppEvents.storeChanged(context, id)
                return
            }
            ACTION_EXPIRE -> {
                FocusedMindStore.cleanupExpired(context)
                if (id > 0) AppEvents.storeChanged(context, id)
                return
            }
        }

        if (id <= 0) return
        val commitment = FocusedMindStore.commitments(context).firstOrNull { it.id == id } ?: return
        val stage = intent.getIntExtra(EXTRA_STAGE, -1)
        if (stage != STAGE_TEN && stage != STAGE_EXACT) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager)
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_COMMITMENT_ID, id)
        }
        val open = PendingIntent.getActivity(
            context,
            stableRequest(id, REQUEST_OPEN),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val complete = PendingIntent.getBroadcast(
            context,
            stableRequest(id, REQUEST_COMPLETE),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notCompleted = PendingIntent.getBroadcast(
            context,
            stableRequest(id, REQUEST_NOT_COMPLETED),
            Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_NOT_COMPLETED
                putExtra(EXTRA_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val moment = if (stage == STAGE_TEN) "ten" else "exact"
        val message = if (!commitment.subject.isNullOrBlank()) {
            LocalizedContentRepository.randomAcademic(context, commitment.subject!!.lowercase(java.util.Locale.ROOT), moment)
        } else {
            LocalizedContentRepository.randomReminder(
                context,
                FocusCategoryCatalog.key(commitment.categoryId),
                moment
            )
        }

        val titleKey = if (stage == STAGE_TEN) "notif_prepare" else "notif_action"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${LocalizedStrings.text(context, titleKey)} • ${commitment.title}")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, LocalizedStrings.text(context, "start"), open)
            .apply {
                if (stage == STAGE_EXACT) {
                    addAction(0, LocalizedStrings.text(context, "completed"), complete)
                    addAction(0, LocalizedStrings.text(context, "not_completed"), notCompleted)
                }
            }
            .build()

        manager.notify(notificationId(id, stage), notification)
    }

    companion object {
        const val CHANNEL = "focused_mind_commitments"
        const val EXTRA_ID = "COMMITMENT_ID"
        const val EXTRA_STAGE = "STAGE"
        const val ACTION_COMPLETE = "com.focusedmind.COMPLETE"
        const val ACTION_NOT_COMPLETED = "com.focusedmind.NOT_COMPLETED"
        const val ACTION_EXPIRE = "com.focusedmind.EXPIRE"
        const val STAGE_TEN = 0
        const val STAGE_EXACT = 1
        const val STAGE_EXPIRE = 2

        private const val REQUEST_OPEN = 90
        private const val REQUEST_COMPLETE = 91
        private const val REQUEST_NOT_COMPLETED = 92

        fun createChannel(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Focused Mind commitments",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Only scheduled commitment reminders"
                    }
                )
            }
        }

        fun stableRequest(id: Long, slot: Int): Int =
            (((id xor (id ushr 32)).toInt() and 0x7fffffff) % 1_900_000_000) + slot

        fun notificationId(id: Long, stage: Int): Int =
            (((id xor (id ushr 32)).toInt() and 0x3fffffff) + stage + 1)
    }
}

object AlarmScheduler {
    fun schedule(context: Context, commitment: FocusedMindStore.Commitment) {
        cancel(context, commitment.id)
        if (commitment.timestamp <= System.currentTimeMillis()) return

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stages = listOf(
            AlarmReceiver.STAGE_TEN to commitment.timestamp - 10 * 60_000L,
            AlarmReceiver.STAGE_EXACT to commitment.timestamp,
            // Silent internal expiry only. No notification is ever generated for this stage.
            AlarmReceiver.STAGE_EXPIRE to commitment.timestamp + FocusedMindStore.RESPONSE_WINDOW_MS
        )

        for ((stage, triggerAt) in stages) {
            if (triggerAt <= System.currentTimeMillis()) continue

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_ID, commitment.id)
                putExtra(AlarmReceiver.EXTRA_STAGE, stage)
                if (stage == AlarmReceiver.STAGE_EXPIRE) action = AlarmReceiver.ACTION_EXPIRE
            }

            val pending = PendingIntent.getBroadcast(
                context,
                AlarmReceiver.stableRequest(commitment.id, stage),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                when {
                    Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms() ->
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    Build.VERSION.SDK_INT >= 23 ->
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    else ->
                        manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } catch (_: SecurityException) {
                // Exact-alarm access can be revoked between the permission check and the call.
                // Degrade to the best available alarm mechanism without crashing the app.
                runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (stage in AlarmReceiver.STAGE_TEN..AlarmReceiver.STAGE_EXPIRE) {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                if (stage == AlarmReceiver.STAGE_EXPIRE) action = AlarmReceiver.ACTION_EXPIRE
            }
            val pending = PendingIntent.getBroadcast(
                context,
                AlarmReceiver.stableRequest(id, stage),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            manager.cancel(pending)
            pending.cancel()
        }
        FocusedMindStore.cancelCommitmentNotifications(context, id)
    }
}

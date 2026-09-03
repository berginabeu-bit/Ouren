package com.focusedmind.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (intent.action !in supported) return

        FocusedMindStore.cleanupExpired(context)
        val now = System.currentTimeMillis()
        FocusedMindStore.commitments(context)
            .filter { it.timestamp > now }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}

object FocusCategoryCatalog {
    data class Category(val id: Int, val key: String, val premium: Boolean)

    val all = listOf(
        Category(1, "decisions", false),
        Category(2, "work", false),
        Category(3, "connections", false),
        Category(4, "events", true),
        Category(5, "academic", true)
    )

    fun find(id: Int): Category? = all.firstOrNull { it.id == id }
    fun key(id: Int): String = find(id)?.key ?: "work"
}

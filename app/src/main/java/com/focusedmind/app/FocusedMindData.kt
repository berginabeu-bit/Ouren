import android.app.NotificationManager
package com.focusedmind.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Offline-first persistence and business rules for Focused Mind. */
object FocusedMindStore {
    private const val PREFS = "focused_mind_store_v4"
    private const val K_COMMITMENTS = "commitments"
    private const val K_XP = "xp"
    private const val K_COMPLETED = "completed"
    private const val K_MISSED = "missed"
    private const val K_EXPIRED = "expired"
    private const val K_DATES = "completion_dates"
    private const val K_BEST = "best_streak"
    private const val K_USED = "used_phrases"
    private const val K_ONBOARDING = "onboarding_complete"
    private const val K_PREMIUM = "premium_"
    private const val K_CAT_COUNTS = "category_counts"
    private const val K_ACADEMIC_COUNT = "academic_count"
    private const val K_PROGRESS_DAY = "progress_message_day"
    private const val K_PROGRESS_LANG = "progress_message_language"
    private const val K_PROGRESS_MESSAGE = "progress_message"

    private val lock = Any()

    enum class RepeatMode { ONE_TIME, DAILY, WEEKDAYS, WEEKENDS, SPECIFIC }

    data class Commitment(
        val id: Long,
        val categoryId: Int,
        val title: String,
        val timestamp: Long,
        val subject: String? = null,
        val repeatMode: RepeatMode = RepeatMode.ONE_TIME,
        val repeatDays: Set<Int> = emptySet()
    )

    data class Reward(
        val earnedXp: Int,
        val totalXp: Long,
        val level: Level,
        val newStreak: Int,
        val leveledUp: Boolean
    )

    data class Level(val number: Int, val nameKey: String, val floor: Long, val next: Long)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun onboardingComplete(context: Context): Boolean = prefs(context).getBoolean(K_ONBOARDING, false)

    fun setOnboardingComplete(context: Context) {
        prefs(context).edit().putBoolean(K_ONBOARDING, true).apply()
    }

    private fun fromJson(json: JSONObject): Commitment {
        val mode = runCatching {
            RepeatMode.valueOf(json.optString("repeat", RepeatMode.ONE_TIME.name))
        }.getOrDefault(RepeatMode.ONE_TIME)

        val days = buildSet {
            val array = json.optJSONArray("days") ?: JSONArray()
            for (index in 0 until array.length()) {
                val day = array.optInt(index, -1)
                if (day in Calendar.SUNDAY..Calendar.SATURDAY) add(day)
            }
        }

        return Commitment(
            id = json.optLong("id", 0L),
            categoryId = json.optInt("category", 2),
            title = json.optString("title", "").trim(),
            timestamp = json.optLong("time", 0L),
            subject = json.optString("subject").trim().ifBlank { null },
            repeatMode = mode,
            repeatDays = days
        )
    }

    private fun raw(context: Context): List<Commitment> = synchronized(lock) {
        val encoded = prefs(context).getString(K_COMMITMENTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(encoded) }.getOrElse { JSONArray() }
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                runCatching { fromJson(array.getJSONObject(index)) }
                    .getOrNull()
                    ?.takeIf { it.id > 0 && it.title.isNotBlank() && it.timestamp > 0 }
                    ?.let(::add)
            }
        }
    }

    private fun save(context: Context, list: List<Commitment>) = synchronized(lock) {
        val array = JSONArray()
        list.distinctBy { it.id }.sortedBy { it.timestamp }.forEach { commitment ->
            array.put(
                JSONObject().apply {
                    put("id", commitment.id)
                    put("category", commitment.categoryId)
                    put("title", commitment.title)
                    put("time", commitment.timestamp)
                    put("subject", commitment.subject ?: "")
                    put("repeat", commitment.repeatMode.name)
                    put("days", JSONArray(commitment.repeatDays.sorted()))
                }
            )
        }
        prefs(context).edit().putString(K_COMMITMENTS, array.toString()).apply()
    }

    /** Silently closes expired occurrences. It never posts a notification. */
    fun cleanupExpired(context: Context, now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            raw(context).filter { now >= it.timestamp + RESPONSE_WINDOW_MS }.forEach {
                expireOccurrence(context, it, now, countedAsMiss = false)
            }
        }
    }

    fun commitments(context: Context): List<Commitment> {
        cleanupExpired(context)
        val now = System.currentTimeMillis()
        return raw(context)
            .filter { it.timestamp > now || now <= it.timestamp + RESPONSE_WINDOW_MS }
            .sortedBy { it.timestamp }
    }

    fun upsertCommitment(context: Context, commitment: Commitment) {
        require(commitment.id > 0) { "Commitment id must be positive" }
        require(FocusCategoryCatalog.find(commitment.categoryId) != null) { "Unknown commitment category" }
        require(commitment.title.isNotBlank()) { "Commitment title must not be blank" }
        require(commitment.timestamp >= System.currentTimeMillis() + MIN_LEAD_MS) {
            "Commitment must be at least 10 minutes in the future"
        }
        val premiumProduct = PremiumProducts.productFor(commitment.categoryId)
        if (premiumProduct != null) {
            require(PremiumAccessManager.hasAccess(context, premiumProduct)) { "Premium access is required" }
        }
        if (commitment.categoryId == 5) {
            require(commitment.subject in ACADEMIC_SUBJECTS) { "Academic discipline is required" }
        }
        if (commitment.repeatMode == RepeatMode.SPECIFIC) {
            require(commitment.repeatDays.isNotEmpty()) { "Specific-day recurrence needs at least one day" }
        }
        require(commitment.repeatDays.all { it in Calendar.SUNDAY..Calendar.SATURDAY }) {
            "Invalid recurrence day"
        }
        synchronized(lock) {
            save(context, raw(context).filterNot { it.id == commitment.id } + commitment.copy(
                repeatDays = if (commitment.repeatMode == RepeatMode.SPECIFIC) commitment.repeatDays else emptySet()
            ))
        }
    }

    fun removeCommitment(context: Context, id: Long) {
        AlarmScheduler.cancel(context, id)
        synchronized(lock) { save(context, raw(context).filterNot { it.id == id }) }
    }

    fun markCompleted(context: Context, id: Long): Reward? = synchronized(lock) {
        val occurrence = raw(context).firstOrNull { it.id == id } ?: return@synchronized null
        val now = System.currentTimeMillis()
        if (now < occurrence.timestamp || now > occurrence.timestamp + RESPONSE_WINDOW_MS) {
            if (now > occurrence.timestamp + RESPONSE_WINDOW_MS) {
                expireOccurrence(context, occurrence, now, countedAsMiss = false)
            }
            return@synchronized null
        }

        AlarmScheduler.cancel(context, id)
        cancelCommitmentNotifications(context, id)

        val beforeXp = xp(context)
        val oldLevel = levelFor(beforeXp)
        val dates = completionDates(context).toMutableSet()
        dates += dayKey(now)
        val newStreak = streakFrom(dates, now)
        val earned = 100 + minOf(200, newStreak * 10)
        val newXp = beforeXp + earned

        val categoryCounts = categoryCounts(context).toMutableMap()
        categoryCounts[occurrence.categoryId] = (categoryCounts[occurrence.categoryId] ?: 0) + 1

        prefs(context).edit()
            .putLong(K_XP, newXp)
            .putInt(K_COMPLETED, completed(context) + 1)
            .putStringSet(K_DATES, dates)
            .putInt(K_BEST, maxOf(bestStreak(context), newStreak))
            .putString(K_CAT_COUNTS, JSONObject(categoryCounts.mapValues { it.value as Any }).toString())
            .apply()

        if (!occurrence.subject.isNullOrBlank()) {
            prefs(context).edit().putInt(K_ACADEMIC_COUNT, academicSessions(context) + 1).apply()
        }

        val next = nextOccurrenceAfter(occurrence, now)
        save(context, raw(context).filterNot { it.id == occurrence.id } + listOfNotNull(next))
        next?.let { AlarmScheduler.schedule(context, it) }
        val newLevel = levelFor(newXp)
        Reward(
            earnedXp = earned,
            totalXp = newXp,
            level = newLevel,
            newStreak = newStreak,
            leveledUp = newLevel.number > oldLevel.number
        )
    }

    fun markNotCompleted(context: Context, id: Long): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val occurrence = raw(context).firstOrNull { it.id == id } ?: return@synchronized false
        if (now !in occurrence.timestamp..(occurrence.timestamp + RESPONSE_WINDOW_MS)) return@synchronized false
        expireOccurrence(context, occurrence, now, countedAsMiss = true)
        true
    }

    private fun expireOccurrence(
        context: Context,
        occurrence: Commitment,
        now: Long,
        countedAsMiss: Boolean
    ) {
        AlarmScheduler.cancel(context, occurrence.id)
        cancelCommitmentNotifications(context, occurrence.id)

        if (countedAsMiss) {
            prefs(context).edit().putInt(K_MISSED, missed(context) + 1).apply()
        } else {
            prefs(context).edit().putInt(K_EXPIRED, expired(context) + 1).apply()
        }

        val next = nextOccurrenceAfter(occurrence, now)
        save(context, raw(context).filterNot { it.id == occurrence.id } + listOfNotNull(next))
        next?.let { AlarmScheduler.schedule(context, it) }
    }

    /** Returns the next occurrence strictly after [now], even after a long app/device outage. */
    private fun nextOccurrenceAfter(occurrence: Commitment, now: Long): Commitment? {
        if (occurrence.repeatMode == RepeatMode.ONE_TIME) return null
        if (occurrence.repeatMode == RepeatMode.SPECIFIC && occurrence.repeatDays.isEmpty()) return null

        val calendar = Calendar.getInstance().apply { timeInMillis = occurrence.timestamp }
        val targetDays = when (occurrence.repeatMode) {
            RepeatMode.WEEKDAYS -> setOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            )
            RepeatMode.WEEKENDS -> setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            RepeatMode.SPECIFIC -> occurrence.repeatDays
            else -> emptySet()
        }

        do {
            when (occurrence.repeatMode) {
                RepeatMode.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
                RepeatMode.WEEKDAYS, RepeatMode.WEEKENDS, RepeatMode.SPECIFIC -> {
                    do calendar.add(Calendar.DAY_OF_YEAR, 1)
                    while (calendar.get(Calendar.DAY_OF_WEEK) !in targetDays)
                }
                RepeatMode.ONE_TIME -> return null
            }
        } while (calendar.timeInMillis <= now)

        return occurrence.copy(id = newId(), timestamp = calendar.timeInMillis)
    }

    private fun newId(): Long =
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

    fun xp(context: Context): Long = prefs(context).getLong(K_XP, 0L)
    fun completed(context: Context): Int = prefs(context).getInt(K_COMPLETED, 0)
    fun missed(context: Context): Int = prefs(context).getInt(K_MISSED, 0)
    fun expired(context: Context): Int = prefs(context).getInt(K_EXPIRED, 0)
    fun bestStreak(context: Context): Int = prefs(context).getInt(K_BEST, 0)
    fun academicSessions(context: Context): Int = prefs(context).getInt(K_ACADEMIC_COUNT, 0)

    fun pending(context: Context): Int = commitments(context).size

    fun categoryCompleted(context: Context, id: Int): Int = runCatching {
        JSONObject(prefs(context).getString(K_CAT_COUNTS, "{}") ?: "{}").optInt(id.toString(), 0)
    }.getOrDefault(0)

    fun activeDays(context: Context): Int = completionDates(context).size

    fun completionRate(context: Context): Int {
        val resolved = completed(context) + missed(context) + expired(context)
        return if (resolved == 0) 0 else completed(context) * 100 / resolved
    }

    private fun categoryCounts(context: Context): Map<Int, Int> = runCatching {
        val json = JSONObject(prefs(context).getString(K_CAT_COUNTS, "{}") ?: "{}")
        buildMap {
            json.keys().forEach { key -> put(key.toInt(), json.optInt(key, 0)) }
        }
    }.getOrDefault(emptyMap())

    private fun completionDates(context: Context): Set<String> =
        prefs(context).getStringSet(K_DATES, emptySet())?.toSet() ?: emptySet()

    fun streak(context: Context): Int = streakFrom(completionDates(context), System.currentTimeMillis())

    private fun streakFrom(dates: Set<String>, now: Long): Int {
        if (dates.isEmpty()) return 0
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        if (dayKey(now) !in dates) calendar.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        while (dayKey(calendar.timeInMillis) in dates) {
            count++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    private fun dayKey(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    fun levelFor(totalXp: Long): Level {
        var level = 1
        while (level < MAX_LEVEL && totalXp >= required(level + 1)) level++
        val nameKey = when {
            level < 100 -> "level_beginner"
            level < 400 -> "level_growing"
            level < 900 -> "level_consistent"
            level < 1500 -> "level_disciplined"
            else -> "level_focused"
        }
        return Level(
            number = level,
            nameKey = nameKey,
            floor = required(level),
            next = required((level + 1).coerceAtMost(MAX_LEVEL + 1))
        )
    }

    private fun required(level: Int): Long =
        if (level <= 1) 0L else 100L * level * level * level / 8L + 200L * level

    fun rememberPhrase(context: Context, key: String): Boolean {
        synchronized(lock) {
            val used = prefs(context).getStringSet(K_USED, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (key in used) return true
            used += key
            while (used.size > 1500) used.remove(used.first())
            prefs(context).edit().putStringSet(K_USED, used).apply()
            return false
        }
    }

    fun progressMessage(context: Context, languageTag: String, candidateProvider: () -> String): String {
        synchronized(lock) {
            val today = dayKey(System.currentTimeMillis())
            val last = prefs(context).getString(K_PROGRESS_DAY, "") ?: ""
            val storedLanguage = prefs(context).getString(K_PROGRESS_LANG, "") ?: ""
            val stored = prefs(context).getString(K_PROGRESS_MESSAGE, "") ?: ""
            val enoughTimePassed = runCatching {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val current = parser.parse(today)!!.time
                val previous = parser.parse(last)?.time ?: 0L
                (current - previous) >= 2 * 24 * 60 * 60 * 1000L
            }.getOrDefault(true)

            if (stored.isNotBlank() && storedLanguage == languageTag && !enoughTimePassed) return stored

            val message = candidateProvider().trim().ifBlank {
                LocalizedStrings.text(context, "progress_message")
            }
            prefs(context).edit()
                .putString(K_PROGRESS_DAY, today)
                .putString(K_PROGRESS_LANG, languageTag)
                .putString(K_PROGRESS_MESSAGE, message)
                .apply()
            return message
        }
    }

    fun premium(context: Context, product: String): Boolean =
        prefs(context).getBoolean(K_PREMIUM + product, false)

    fun setPremium(context: Context, product: String, value: Boolean) {
        prefs(context).edit().putBoolean(K_PREMIUM + product, value).apply()
    }

    fun cancelCommitmentNotifications(context: Context, id: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(AlarmReceiver.notificationId(id, AlarmReceiver.STAGE_TEN))
        manager.cancel(AlarmReceiver.notificationId(id, AlarmReceiver.STAGE_EXACT))
    }

    const val RESPONSE_WINDOW_MS = 5 * 60_000L
    const val MIN_LEAD_MS = 10 * 60_000L
    private const val MAX_LEVEL = 2000
    private val ACADEMIC_SUBJECTS = setOf(
        "languages", "mathematics", "physics", "chemistry", "biology", "history", "geography"
    )
}

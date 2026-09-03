package com.focusedmind.app

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

object LocalizedContentRepository {
    private fun assetName(context: Context, prefix: String): String {
        val tag = LanguageManager.effectiveLocale(context).toLanguageTag()
        val candidates = buildList {
            add("${prefix}_$tag.json")
            add("${prefix}_${tag.substringBefore('-')}.json")
            add("${prefix}_en.json")
        }.distinct()
        return candidates.firstOrNull { name ->
            runCatching { context.assets.open(name).use { true } }.getOrDefault(false)
        } ?: "${prefix}_en.json"
    }

    private fun pick(context: Context, asset: String, key: String, fallback: String): String {
        val root = runCatching {
            context.assets.open(asset).bufferedReader().use { JSONObject(it.readText()) }
        }.getOrNull() ?: return fallback
        val array = root.optJSONArray(key) ?: return fallback
        if (array.length() == 0) return fallback

        val values = (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotEmpty() }
        }
        if (values.isEmpty()) return fallback

        repeat(minOf(8, values.size)) {
            val value = values[Random.nextInt(values.size)]
            if (!FocusedMindStore.rememberPhrase(context, "$asset|$key|$value")) return value
        }
        return values[Random.nextInt(values.size)]
    }

    fun randomReminder(context: Context, category: String, moment: String): String =
        pick(
            context,
            assetName(context, "reminders"),
            "$category.$moment",
            LocalizedStrings.text(context, "default_reminder")
        )

    fun randomAcademic(context: Context, subject: String, moment: String): String =
        pick(
            context,
            assetName(context, "academic"),
            "$subject.$moment",
            LocalizedStrings.text(context, "default_reminder")
        )

    fun randomProgress(context: Context, state: String): String =
        pick(
            context,
            assetName(context, "progress"),
            state,
            LocalizedStrings.text(context, "progress_message")
        )
}

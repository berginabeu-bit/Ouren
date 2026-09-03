import kotlin.collections.*
package com.focusedmind.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    data class Language(val tag: String, val label: String)
    val supported = listOf(
        Language("system", "System default"), Language("pt-BR", "Português (Brasil)"), Language("pt-PT", "Português (Portugal)"),
        Language("en", "English"), Language("es", "Español"), Language("zh-CN", "中文（普通话）"), Language("zh-HK", "中文（粵語）"),
        Language("ar", "العربية"), Language("fr", "Français"), Language("de", "Deutsch"), Language("it", "Italiano"), Language("ja", "日本語"),
        Language("ko", "한국어"), Language("ms", "Bahasa Melayu"), Language("ru", "Русский"), Language("pl", "Polski"), Language("tr", "Türkçe")
    )
    private const val PREF = "focused_mind_language"
    fun selectedTag(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("tag", "system") ?: "system"
    fun set(context: Context, tag: String) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
    fun effectiveLocale(context: Context): Locale {
        val selected = selectedTag(context)
        if (selected != "system") {
            val locale = Locale.forLanguageTag(selected)
            if (locale.language.isNotBlank()) return locale
        }
        return LocaleList.getDefault().firstOrNull() ?: Locale.getDefault()
    }

    /** Returns a Context whose Android widgets (DatePicker/TimePicker/etc.) follow the selected language. */
    fun localizedContext(context: Context): Context {
        val locale = effectiveLocale(context)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }
}

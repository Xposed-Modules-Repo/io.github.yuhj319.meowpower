package io.github.yuhj319.meowpower

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "meow_ui"
    private const val KEY_LANG = "app_language"

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"

    fun getSavedLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM

    fun saveLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
    }

    fun wrap(context: Context): Context {
        val lang = getSavedLanguage(context)
        if (lang == LANG_SYSTEM) return context
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

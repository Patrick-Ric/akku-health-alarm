package io.github.patrickric.akkuhealthalarm

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.io.File
import java.util.Locale

/**
 * Language of the app, independent of the phone. Android 13+ keeps the choice itself (it also
 * appears in the system settings) and applies it to both processes. Before 13 the choice is a small
 * file that the user interface and the watcher read when they start, and every context is wrapped.
 */
object AppLanguage {
    /** The translated languages, as BCP 47 tags; English is the base. */
    val TAGS = listOf(
        "en", "de", "fr", "es", "it", "nl", "pt-BR", "pl", "cs", "sk",
        "hu", "ro", "sv", "da", "nb", "fi", "el", "tr", "ru", "uk",
    )

    private const val FILE = "app_language.txt"

    /** The chosen tag, or null for "like the phone". */
    fun current(context: Context): String? = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales
            ?.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
            ?.let { tag -> TAGS.firstOrNull { it.equals(tag, ignoreCase = true) } ?: TAGS.firstOrNull { it == Locale.forLanguageTag(tag).language } }
    } else {
        stored(context)
    }

    private fun stored(context: Context): String? =
        runCatching { File(context.filesDir, FILE).readText().trim() }.getOrNull()?.takeIf { it in TAGS }

    /**
     * Saves the choice. Android 13+ recreates the screen itself; before that the caller recreates
     * the activity and the watcher is restarted so its notifications use the new language.
     */
    fun set(context: Context, tag: String?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            val f = File(context.filesDir, FILE)
            if (tag == null) f.delete() else f.writeText(tag)
        }
    }

    /** Context with the chosen language for Android before 13; unchanged otherwise. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = stored(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Name of a language in that language itself, e.g. "Deutsch", "Français", "Português (Brasil)". */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}

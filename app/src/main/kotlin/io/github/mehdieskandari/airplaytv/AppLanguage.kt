package io.github.mehdieskandari.airplaytv

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** The app language is independent of the device language, including on Android 6. */
object AppLanguage {
    fun context(base: Context): Context {
        val language = base.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)[Prefs.LANGUAGE]
        val locale = Locale.forLanguageTag(language)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        })
    }
}

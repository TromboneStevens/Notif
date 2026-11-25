package com.personal.keywordalarm

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Thin wrapper around SharedPreferences so every component reads the same settings.
 */
object Prefs {
    const val KEY_KEYWORDS = "keywords"
    const val KEY_ENABLED = "monitoring_enabled"
    const val KEY_PACKAGES = "watched_packages"
    const val KEY_COOLDOWN = "cooldown_seconds"
    const val KEY_KEEP_AWAKE = "keep_screen_awake"
    const val KEY_CASE_SENSITIVE = "case_sensitive"

    // Brave by default; comma-separated so you can add more browsers later.
    private const val DEFAULT_PACKAGES = "com.brave.browser"
    private const val DEFAULT_COOLDOWN = 10

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun getKeywords(ctx: Context): List<String> =
        prefs(ctx).getString(KEY_KEYWORDS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun getKeywordsRaw(ctx: Context): String =
        prefs(ctx).getString(KEY_KEYWORDS, "").orEmpty()

    fun setKeywords(ctx: Context, raw: String) =
        prefs(ctx).edit().putString(KEY_KEYWORDS, raw).apply()

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()

    fun getWatchedPackages(ctx: Context): Set<String> =
        prefs(ctx).getString(KEY_PACKAGES, DEFAULT_PACKAGES)
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun getCooldownSeconds(ctx: Context): Int =
        prefs(ctx).getInt(KEY_COOLDOWN, DEFAULT_COOLDOWN)

    fun isKeepAwake(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_KEEP_AWAKE, true)

    fun isCaseSensitive(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CASE_SENSITIVE, false)
}

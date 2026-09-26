package com.vrushabhhirap.shukracharyascurse.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * Unified application preferences manager.
 * Provides typed accessors for UI theming, session cache, telemetry toggles,
 * and encrypted preference storage for non-critical local settings.
 */
class AppPreferences private constructor(context: Context) {

    companion object {
        private const val PREFS_FILE = "sanjivani_app_preferences"
        private const val SECURE_PREFS_FILE = "sanjivani_secure_preferences"

        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_ANIMATIONS_ENABLED = "pref_animations_enabled"
        private const val KEY_DEV_DIAGNOSTICS = "pref_dev_diagnostics"
        private const val KEY_LAST_KNOWN_USER = "pref_last_user"
        private const val KEY_LAST_LOGIN_TIMESTAMP = "pref_last_login_time"
        private const val KEY_OFFLINE_SYNC_MODE = "pref_offline_sync"
        private const val KEY_FONT_SCALE = "pref_font_scale"
        private const val KEY_MANUSCRIPT_SOUNDS = "pref_manuscript_audio"

        @Volatile
        private var sInstance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return sInstance ?: synchronized(this) {
                sInstance ?: AppPreferences(context.applicationContext).also { sInstance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences =
        context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)

    var isAnimationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, value).apply()

    var isDevDiagnosticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEV_DIAGNOSTICS, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_DIAGNOSTICS, value).apply()

    var isOfflineSyncMode: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_SYNC_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_SYNC_MODE, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "DARK_VEDIC") ?: "DARK_VEDIC"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var lastKnownUser: String
        get() = prefs.getString(KEY_LAST_KNOWN_USER, "kacha_disciple") ?: "kacha_disciple"
        set(value) = prefs.edit().putString(KEY_LAST_KNOWN_USER, value).apply()

    var lastLoginTimestamp: Long
        get() = prefs.getLong(KEY_LAST_LOGIN_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_LOGIN_TIMESTAMP, value).apply()

    var fontScaleMultiplier: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    var isManuscriptSoundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUSCRIPT_SOUNDS, false)
        set(value) = prefs.edit().putBoolean(KEY_MANUSCRIPT_SOUNDS, value).apply()

    /**
     * Stores an obfuscated key-value pair in secure preferences.
     */
    fun putObfuscatedString(key: String, value: String) {
        val encoded = Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        securePrefs.edit().putString(key, encoded).apply()
    }

    /**
     * Reads an obfuscated string from secure preferences.
     */
    fun getObfuscatedString(key: String, defaultValue: String): String {
        val raw = securePrefs.getString(key, null) ?: return defaultValue
        return try {
            val decodedBytes = Base64.decode(raw, Base64.NO_WRAP)
            String(decodedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        securePrefs.edit().clear().apply()
    }
}

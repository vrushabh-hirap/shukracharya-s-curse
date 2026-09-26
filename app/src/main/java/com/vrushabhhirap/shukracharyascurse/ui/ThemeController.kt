package com.vrushabhhirap.shukracharyascurse.ui

import android.app.Activity
import android.graphics.Color
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vrushabhhirap.shukracharyascurse.utils.AppPreferences

/**
 * Controller for application-wide theming, status bar coloration,
 * and visual effects.
 */
object ThemeController {

    const val THEME_DARK_VEDIC = "DARK_VEDIC"
    const val THEME_OBSIDIAN = "OBSIDIAN"
    const val THEME_PARCHMENT = "PARCHMENT"

    fun applyTheme(activity: Activity) {
        val prefs = AppPreferences.getInstance(activity)
        val mode = prefs.themeMode

        when (mode) {
            THEME_PARCHMENT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        setupWindowDecorations(activity)
    }

    fun setupWindowDecorations(activity: Activity) {
        val window = activity.window ?: return
        val decorView = window.decorView ?: return

        // Set status bar & navigation bar background colors
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#0D0B0E")
        window.navigationBarColor = Color.parseColor("#0D0B0E")

        // Use AndroidX WindowCompat for lifecycle-safe system bar appearance configuration
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }

    fun getAccentColorHex(): String {
        return "#D4AF37" // Muted Antique Gold
    }

    fun getSurfaceColorHex(): String {
        return "#16131A" // Dark ashrama background
    }
}

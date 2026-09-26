package com.vrushabhhirap.shukracharyascurse.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vrushabhhirap.shukracharyascurse.LoginActivity
import com.vrushabhhirap.shukracharyascurse.R
import com.vrushabhhirap.shukracharyascurse.db.LegacyMigrationHelper
import com.vrushabhhirap.shukracharyascurse.utils.AnalyticsHelper
import com.vrushabhhirap.shukracharyascurse.utils.AppPreferences
import com.vrushabhhirap.shukracharyascurse.utils.DeviceInfoHelper

/**
 * Aesthetic launch activity introducing the Sanjivani manuscript archive.
 * Performs background migration check and initializes telemetry session.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        ThemeController.applyTheme(this)

        // Initialize core application state
        val prefs = AppPreferences.getInstance(this)
        val migrationHelper = LegacyMigrationHelper(this)
        migrationHelper.runPendingMigrations()

        // Telemetry session start
        AnalyticsHelper.startSession("SESSION_INIT")
        AnalyticsHelper.logEvent(
            AnalyticsHelper.EventType.LIFECYCLE,
            "SplashActivity",
            "Device: " + DeviceInfoHelper.getFormattedSummary(this)
        )

        // Animate elements
        val ivLogo = findViewById<ImageView>(R.id.iv_splash_logo)
        val tvTitle = findViewById<TextView>(R.id.tv_splash_title)
        val tvSubtitle = findViewById<TextView>(R.id.tv_splash_subtitle)

        if (prefs.isAnimationsEnabled) {
            val animSet = AnimationSet(true).apply {
                interpolator = DecelerateInterpolator()
                addAnimation(AlphaAnimation(0.0f, 1.0f).apply { duration = 800 })
                addAnimation(ScaleAnimation(0.85f, 1.0f, 0.85f, 1.0f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply { duration = 800 })
            }
            ivLogo?.startAnimation(animSet)

            val fadeText = AlphaAnimation(0.0f, 1.0f).apply {
                duration = 1000
                startOffset = 300
            }
            tvTitle?.startAnimation(fadeText)
            tvSubtitle?.startAnimation(fadeText)
        }

        // Smooth transition to LoginActivity
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this, LoginActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }, 1200)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

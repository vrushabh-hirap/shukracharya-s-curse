package com.vrushabhhirap.shukracharyascurse

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vrushabhhirap.shukracharyascurse.model.UserSession
import com.vrushabhhirap.shukracharyascurse.ui.ThemeController
import com.vrushabhhirap.shukracharyascurse.utils.AnalyticsHelper
import com.vrushabhhirap.shukracharyascurse.utils.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Portal authentication activity.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private var sessionAttemptCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        ThemeController.applyTheme(this)

        prefs = AppPreferences.getInstance(this)

        val etPasscode = findViewById<EditText>(R.id.et_incantation)
        val btnSubmit = findViewById<Button>(R.id.btn_resurrect)
        val ivSeal = findViewById<ImageView>(R.id.iv_om_seal)
        val tvLastLogin = findViewById<TextView>(R.id.tv_last_login_info)

        // Display last known invocation metadata
        val lastLogin = prefs.lastLoginTimestamp
        if (lastLogin > 0 && tvLastLogin != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvLastLogin.text = getString(R.string.lore_last_invocation, sdf.format(Date(lastLogin)))
        }

        etPasscode?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Sanskrit Unicode detection & visual feedback
                val str = s?.toString() ?: ""
                val hasSanskrit = str.any { it in '\u0900'..'\u097F' }
                if (hasSanskrit) {
                    etPasscode.setTextColor(resources.getColor(R.color.vedic_gold, theme))
                } else {
                    etPasscode.setTextColor(resources.getColor(R.color.parchment_text, theme))
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSubmit?.setOnClickListener {
            sessionAttemptCount++
            val entered = etPasscode?.text?.toString()?.trim() ?: ""

            // Rotate Om symbol on submit for responsive tactile feel
            if (ivSeal != null && prefs.isAnimationsEnabled) {
                val rotate = RotateAnimation(
                    0f, 360f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                    RotateAnimation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 500
                }
                ivSeal.startAnimation(rotate)
            }

            prefs.lastLoginTimestamp = System.currentTimeMillis()
            AnalyticsHelper.logEvent(
                AnalyticsHelper.EventType.AUTH_ATTEMPT,
                "LoginActivity",
                "Invocation initiated (length=${entered.length})"
            )

            startActivity(Intent(this, SecretActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsHelper.logEvent(AnalyticsHelper.EventType.LIFECYCLE, "LoginActivity", "Portal active")
    }
}

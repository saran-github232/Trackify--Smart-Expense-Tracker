package com.saran.expensemanager

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.core.view.WindowCompat
import com.google.android.gms.ads.MobileAds
import com.saran.expensemanager.databinding.ActivitySplashBinding

class SplashActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable {
        val userPrefs = UserPrefs(this)
        val next = if (!userPrefs.isOnboardingComplete)
            Intent(this, OnboardingActivity::class.java)
        else
            Intent(this, MainActivity::class.java)
        startActivity(next)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Splash background is always dark/colored, regardless of light/dark theme, so keep
        // status/nav bar icons light rather than following EdgeToEdgeActivity's theme-based default.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        supportActionBar?.hide()

        Thread { MobileAds.initialize(applicationContext) {} }.start()

        // Background shake listener doesn't survive a device reboot (no BOOT_COMPLETED receiver —
        // not worth the added background-start complexity), so make sure it's running again here.
        if (ShakePrefs(this).enabled) ShakeOverlayService.start(this)

        binding.ivLogo.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
        binding.tvAppName.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left))
        binding.tvTagline.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))

        handler.postDelayed(runnable, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }
}

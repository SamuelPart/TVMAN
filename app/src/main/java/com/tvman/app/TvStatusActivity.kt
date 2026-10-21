package com.tvman.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Native, fail-light loading screen before the TV home. */
class TvStatusActivity : AppCompatActivity() {
    private var splashMotion: AnimatorSet? = null
    private var homeLaunchStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_tv_status)
            fitSplashToScreen()
            startSplashMotion()
        } catch (error: RuntimeException) {
            android.util.Log.e("TvStatusActivity", "Splash failed; opening TV home directly", error)
            openTvHome()
            return
        }

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            openTvHome()
        }
    }

    private fun openTvHome() {
        if (homeLaunchStarted || isFinishing || isDestroyed) return
        homeLaunchStarted = true
        startActivity(Intent(this, TvHomeActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }

    private fun fitSplashToScreen() {
        if (resources.configuration.screenHeightDp >= 390) return
        val factor = (resources.configuration.screenHeightDp / 390f).coerceIn(0.72f, 0.92f)
        findViewById<View>(R.id.splashContent).apply {
            scaleX = factor
            scaleY = factor
        }
    }

    private fun startSplashMotion() {
        val bunny = findViewById<View>(R.id.splashBunny)
        val aura = findViewById<View>(R.id.splashAura)
        val orbit = findViewById<View>(R.id.splashOrbit)
        val progress = findViewById<View>(R.id.splashProgress)

        val hit = ObjectAnimator.ofFloat(bunny, View.SCALE_X, 0.88f, 1.08f, 0.98f, 1f).apply {
            duration = 920L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val breathe = ObjectAnimator.ofFloat(bunny, View.SCALE_Y, 0.94f, 1.07f, 0.98f, 1f).apply {
            duration = 920L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val tilt = ObjectAnimator.ofFloat(bunny, View.ROTATION, -4f, 4f, -2f, 0f).apply {
            duration = 1450L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val pulse = ObjectAnimator.ofFloat(aura, View.ALPHA, 0.30f, 0.88f, 0.30f).apply {
            duration = 1150L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val spin = ObjectAnimator.ofFloat(orbit, View.ROTATION, 0f, 360f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        val progressPulse = ObjectAnimator.ofFloat(progress, View.SCALE_X, 0.35f, 1f, 0.35f).apply {
            duration = 1050L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        splashMotion = AnimatorSet().apply {
            playTogether(hit, breathe, tilt, pulse, spin, progressPulse)
            start()
        }
    }

    override fun onDestroy() {
        splashMotion?.cancel()
        splashMotion = null
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 2_600L
    }
}

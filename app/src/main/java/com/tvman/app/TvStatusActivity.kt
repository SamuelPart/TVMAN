package com.tvman.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Native Newton's-cradle loading screen before the TV home. */
class TvStatusActivity : AppCompatActivity() {
    private var homeLaunchStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_tv_status)
        } catch (error: RuntimeException) {
            android.util.Log.e("TvStatusActivity", "Cradle splash failed; opening TV home directly", error)
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

    private companion object {
        const val SPLASH_DURATION_MS = 2_600L
    }
}

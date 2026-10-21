package com.tvman.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Short animated splash shown before the TV home; never waits on network requests. */
class TvStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_status)

        lifecycleScope.launch {
            // Keep the mascot visible for one full animation loop, then open Home.
            delay(SPLASH_DURATION_MS)
            if (!isFinishing && !isDestroyed) {
                startActivity(Intent(this@TvStatusActivity, TvHomeActivity::class.java))
                finish()
            }
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 2_600L
    }
}

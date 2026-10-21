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
        val isTelevision = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        if (!isTelevision) {
            // The custom animated splash is unstable on this phone; bypass it and launch the TV UI directly.
            startActivity(Intent(this, TvHomeActivity::class.java))
            finish()
            return
        }

        // Keep the animated bunny splash on Android TV, then open the TV home.
        setContentView(R.layout.activity_tv_status)
        lifecycleScope.launch {
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

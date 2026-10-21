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
            // Open the same TV home immediately on phones; skip only the TV splash to keep launch reliable.
            startActivity(Intent(this, TvHomeActivity::class.java))
            finish()
            return
        }

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

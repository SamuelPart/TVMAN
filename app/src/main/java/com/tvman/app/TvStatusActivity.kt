package com.tvman.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen animated loading scene shown before the rest of the TV app. */
class TvStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_status)

        val startedAt = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            // Prime the shared catalog cache before opening Home; remote failures fall back locally.
            withContext(Dispatchers.IO) {
                runCatching { CineRepository.getBundledQuickCatalog(this@TvStatusActivity) }
                runCatching { CineRepository.getCineCatalog(this@TvStatusActivity) }
            }
            val remaining = MIN_SPLASH_MS - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0L) delay(remaining)
            if (!isFinishing) {
                startActivity(Intent(this@TvStatusActivity, TvHomeActivity::class.java))
                finish()
            }
        }
    }

    private companion object {
        const val MIN_SPLASH_MS = 2_400L
    }
}

package com.tvman.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Launches the TV home, where the Newton cradle remains visible until poster artwork is ready. */
class TvStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TvHomeActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}

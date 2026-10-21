package com.tvman.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Standalone launch screen shown before the TV home. */
class TvStatusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_status)

        TvStatusBarController.install(
            this,
            findViewById(R.id.tvStatusBar),
            onSearch = { openMain(MainActivity.TAB_CINE, focusSearch = true) },
            onFilters = { openMain(MainActivity.TAB_CINE, focusFilters = true) },
            onSettings = { openMain(MainActivity.TAB_SETTINGS) },
            onNotifications = {
                Toast.makeText(this, "No tienes notificaciones nuevas", Toast.LENGTH_SHORT).show()
            }
        )

        findViewById<View>(R.id.tvStatusEnter).setOnClickListener {
            startActivity(Intent(this, TvHomeActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.tvStatusEnter).post {
            findViewById<View>(R.id.tvStatusEnter).requestFocus()
        }
    }

    private fun openMain(tab: String, focusSearch: Boolean = false, focusFilters: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TV_TAB, tab)
        if (focusSearch) intent.putExtra(MainActivity.EXTRA_FOCUS_CINE_SEARCH, true)
        if (focusFilters) intent.putExtra(MainActivity.EXTRA_FOCUS_CINE_FILTERS, true)
        startActivity(intent)
    }
}

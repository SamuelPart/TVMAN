package com.tvman.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/** Full-screen Android TV landing page, designed for landscape screens and DPAD. */
class TvHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_home)

        val route: (String) -> Unit = { tab ->
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TV_TAB, tab))
        }
        findViewById<View>(R.id.tvNavHome).setOnClickListener { findViewById<View>(R.id.tvNavHome).requestFocus() }
        findViewById<View>(R.id.tvNavChannels).setOnClickListener { route(MainActivity.TAB_CHANNELS) }
        findViewById<View>(R.id.tvNavMovies).setOnClickListener { route(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvNavSearch).setOnClickListener { route(MainActivity.TAB_SEARCH) }
        findViewById<View>(R.id.tvNavSettings).setOnClickListener { route(MainActivity.TAB_SETTINGS) }
        findViewById<View>(R.id.tvFeaturedButton).setOnClickListener { route(MainActivity.TAB_CHANNELS) }
        findViewById<View>(R.id.tvCardChannels).setOnClickListener { route(MainActivity.TAB_CHANNELS) }
        findViewById<View>(R.id.tvCardMovies).setOnClickListener { route(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvCardSearch).setOnClickListener { route(MainActivity.TAB_SEARCH) }

        listOf(R.id.tvNavHome, R.id.tvNavChannels, R.id.tvNavMovies, R.id.tvNavSearch,
            R.id.tvNavSettings, R.id.tvFeaturedButton, R.id.tvCardChannels, R.id.tvCardMovies,
            R.id.tvCardSearch).forEach { id ->
            findViewById<View>(id).setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.035f else 1f).scaleY(if (focused) 1.035f else 1f)
                    .setDuration(130).start()
                view.elevation = if (focused) 10f else 0f
            }
        }
        findViewById<View>(R.id.tvNavHome).requestFocus()
    }
}

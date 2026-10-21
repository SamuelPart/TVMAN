package com.tvman.app

import android.app.Activity
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/** Shared TV status/actions bar; time follows the device locale and timezone. */
object TvStatusBarController {
    fun install(
        activity: Activity,
        bar: View,
        onSearch: () -> Unit,
        onFilters: () -> Unit,
        onSettings: () -> Unit,
        onNotifications: () -> Unit
    ) {
        bar.findViewById<View>(R.id.tvStatusSearch)?.setOnClickListener { onSearch() }
        bar.findViewById<View>(R.id.tvStatusFilters)?.setOnClickListener { onFilters() }
        bar.findViewById<View>(R.id.tvStatusSettings)?.setOnClickListener { onSettings() }
        bar.findViewById<View>(R.id.tvStatusNotifications)?.setOnClickListener { onNotifications() }

        val clock = bar.findViewById<TextView>(R.id.tvStatusTime) ?: return
        val handler = Handler(Looper.getMainLooper())
        val timeFormat = android.text.format.DateFormat.getTimeFormat(activity)
        val updateClock = object : Runnable {
            override fun run() {
                timeFormat.timeZone = java.util.TimeZone.getDefault()
                clock.text = timeFormat.format(java.util.Date())
                handler.postDelayed(this, 30_000L)
            }
        }
        updateClock.run()
        bar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                handler.removeCallbacks(updateClock)
                updateClock.run()
            }

            override fun onViewDetachedFromWindow(view: View) {
                handler.removeCallbacks(updateClock)
            }
        })

        val wifi = bar.findViewById<ImageView>(R.id.tvStatusWifi)
        val wifiEnabled = runCatching {
            (activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
        }.getOrNull()
        wifi?.alpha = if (wifiEnabled == false) 0.42f else 1f
    }
}

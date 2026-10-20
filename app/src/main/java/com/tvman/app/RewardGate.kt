package com.tvman.app

import android.app.Activity
import android.content.Context

/** Playback is never gated by advertising in TVMAN. */
object RewardGate {
    fun load(context: Context) = Unit
    fun requireAdThen(activity: Activity, onGranted: () -> Unit) = onGranted()
}

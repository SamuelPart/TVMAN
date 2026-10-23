package com.tvman.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Motion helpers for the modern UI.
 *
 * - [springPress]: iPhone-style micro-interaction — the view squeezes to 93%
 *   while pressed and springs back with an overshoot bounce.
 * - [UiMotion.startGlowDrift]: PlayStation-style ambient glow — the orb slowly
 *   drifts and breathes forever behind the UI.
 */

@SuppressLint("ClickableViewAccessibility")
fun View.springPress() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(0.90f)
                    .scaleY(0.90f)
                    .setDuration(120)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360)
                    .setInterpolator(OvershootInterpolator(3.6f))
                    .start()
            }
        }
        false // keep click listeners working
    }
}

/** Use the entire TV display; visible in-app TextClock widgets use the device's local timezone. */
fun Activity.enterTvFullscreen() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }
}

object UiMotion {

    /** iPhone-style detail entrance: the panel springs up into place (with optional delay for staggered builds). */
    fun reveal(view: View, delayMs: Long = 0L) {
        view.alpha = 0f
        view.translationY = 56f
        view.animate().cancel()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(340)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    /** iPhone-style pop: the view scales in from 80% with an elastic overshoot. */
    fun pop(view: View, delayMs: Long = 0L) {
        view.alpha = 0f
        view.scaleX = 0.8f
        view.scaleY = 0.8f
        view.animate().cancel()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(360)
            .setInterpolator(OvershootInterpolator(1.6f))
            .withEndAction { view.animate().setStartDelay(0) }
            .start()
    }

    /** Endless slow drift + breathing for the ambient glow orbs. */
    fun startGlowDrift(view: View, dx: Float, dy: Float, baseAlpha: Float, duration: Long) {
        val driftX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, dx).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val driftY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, dy).apply {
            this.duration = (duration * 1.3f).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val breathe = ObjectAnimator.ofFloat(view, View.ALPHA, baseAlpha * 0.4f, baseAlpha).apply {
            this.duration = (duration * 0.75f).toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        driftX.start()
        driftY.start()
        breathe.start()
    }
}

/** Make a card discoverable and visibly selected with an Android TV remote. */
fun View.enableTVFocus() {
    isFocusable = true
    isFocusableInTouchMode = true
    setOnFocusChangeListener { view, hasFocus ->
        view.animate().cancel()
        view.animate()
            .scaleX(if (hasFocus) 1.045f else 1f)
            .scaleY(if (hasFocus) 1.045f else 1f)
            .setDuration(140)
            .start()
        view.elevation = if (hasFocus) 12f else 0f
    }
}

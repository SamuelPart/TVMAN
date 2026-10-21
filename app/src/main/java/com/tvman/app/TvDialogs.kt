package com.tvman.app

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/** Shared, remote-first error dialog used by screens that can retry an operation. */
object TvDialogs {
    fun showRetry(
        activity: Activity,
        title: String,
        message: String,
        onRetry: () -> Unit,
        onBack: () -> Unit = { activity.finish() }
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Reintentar", null)
            .setNegativeButton("Volver", null)
            .create()
        dialog.setOnShowListener {
            val retry = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val back = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            listOf(retry, back).forEach { button ->
                button.minWidth = dp(activity, 112)
                button.minHeight = dp(activity, 56)
                button.isFocusable = true
                button.foreground = activity.getDrawable(R.drawable.tv_focus_ring)
                button.setTextColor(activity.getColor(R.color.ios_blue))
            }
            retry.contentDescription = "Reintentar"
            back.contentDescription = "Volver a la pantalla anterior"
            retry.setOnClickListener {
                dialog.dismiss()
                onRetry()
            }
            back.setOnClickListener {
                dialog.dismiss()
                onBack()
            }
            retry.requestFocus()
        }
        dialog.show()
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}

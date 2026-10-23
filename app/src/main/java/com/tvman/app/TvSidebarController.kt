package com.tvman.app

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/** Fixed, always-labeled TV navigation rail matching the approved screen plan. */
object TvSidebarController {
    private const val SIDEBAR_WIDTH_DP = 150
    private const val CONTENT_INSET_DP = 166

    private val itemIds = intArrayOf(
        R.id.sideNavHome,
        R.id.sideNavChannels,
        R.id.sideNavMovies,
        R.id.sideNavSeries,
        R.id.sideNavMyList,
        R.id.sideNavSettings
    )

    fun install(activity: Activity, sidebar: View, contentScroll: View? = null) {
        val params = sidebar.layoutParams
        params.width = dp(activity, SIDEBAR_WIDTH_DP)
        sidebar.layoutParams = params
        sidebar.setPadding(dp(activity, 10), dp(activity, 20), dp(activity, 10), dp(activity, 16))

        itemIds.forEach { id ->
            sidebar.findViewById<View>(id)?.apply {
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { view, hasFocus ->
                    view.animate().cancel()
                    view.animate()
                        .scaleX(if (hasFocus) 1.04f else 1f)
                        .scaleY(if (hasFocus) 1.04f else 1f)
                        .translationZ(if (hasFocus) dp(activity, 4).toFloat() else 0f)
                        .setDuration(140L)
                        .setInterpolator(DecelerateInterpolator(1.5f))
                        .start()
                }
            }
        }

        contentScroll?.let { scroll ->
            scroll.setPaddingRelative(
                dp(activity, CONTENT_INSET_DP),
                scroll.paddingTop,
                scroll.paddingEnd,
                scroll.paddingBottom
            )
        }
        updateMyListCount(sidebar, FavoritesManager.getAll(activity).count { !it.isChannel })
    }

    fun updateMyListCount(sidebar: View, count: Int) {
        sidebar.findViewById<TextView>(R.id.sideMyListBadge)?.let { badge ->
            val safeCount = count.coerceAtLeast(0)
            badge.text = if (safeCount > 99) "99+" else safeCount.toString()
            badge.visibility = if (safeCount > 0) View.VISIBLE else View.GONE
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

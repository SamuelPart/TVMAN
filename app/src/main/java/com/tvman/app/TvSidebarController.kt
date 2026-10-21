package com.tvman.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/** DPAD-aware shared rail: full labels on focus, icon-only while browsing content. */
object TvSidebarController {
    private const val COLLAPSED_WIDTH_DP = 82
    private const val EXPANDED_WIDTH_DP = 278
    private const val COLLAPSED_CONTENT_INSET_DP = 106
    private const val EXPANDED_CONTENT_INSET_DP = 302
    private const val ANIMATION_MS = 280L
    private const val LABEL_FADE_MS = 130L
    private const val COLLAPSE_LEAD_MS = 85L

    private val rowIds = intArrayOf(
        R.id.sideNavHome, R.id.sideNavContinue, R.id.sideNavMyList, R.id.sideNavChannels,
        R.id.sideNavSeries, R.id.sideNavMovies, R.id.sideNavGames, R.id.sideNavReleases,
        R.id.sideNavPopular, R.id.sideNavSettings, R.id.sideNavExit
    )

    fun install(activity: Activity, sidebar: View, contentScroll: View? = null) {
        val root = sidebar as? ViewGroup ?: return
        val context = activity
        val expandedWidth = dp(context, EXPANDED_WIDTH_DP)
        val collapsedWidth = dp(context, COLLAPSED_WIDTH_DP)
        val labels = rowIds.asIterable().mapNotNull { id ->
            root.findViewById<TextView>(id)?.let { id to it.text.toString() }
        }.toMap()
        val focusableIds = rowIds + intArrayOf(R.id.sideSearchAction, R.id.sideSearchInput, R.id.sideCollapseButton)
        focusableIds.forEach { id ->
            root.findViewById<View>(id)?.setOnFocusChangeListener { view, hasFocus ->
                view.animate().cancel()
                view.animate()
                    .scaleX(if (hasFocus) 1.03f else 1f)
                    .scaleY(if (hasFocus) 1.03f else 1f)
                    .translationZ(if (hasFocus) dp(context, 4).toFloat() else 0f)
                    .setDuration(145L)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()
            }
        }
        val fadingDetails: List<View> = listOfNotNull(
            root.findViewById<View>(R.id.sideBrand),
            root.findViewById<View>(R.id.sideProfileDetails),
            root.findViewById<View>(R.id.sideSectionMenu),
            root.findViewById<View>(R.id.sideSectionTrend),
            root.findViewById<View>(R.id.sideSectionGeneral),
            root.findViewById<View>(R.id.sideSearchInput)
        )
        var animator: ValueAnimator? = null
        var expanded = false

        fun applyDetails(show: Boolean) {
            root.findViewById<View>(R.id.sideProfileDetails)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<LinearLayout>(R.id.sideProfile)?.let { profile ->
                profile.gravity = if (show) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
            }
            root.findViewById<View>(R.id.sideBrand)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.sideSectionMenu)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.sideSectionTrend)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.sideSectionGeneral)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<EditText>(R.id.sideSearchInput)?.visibility = if (show) View.VISIBLE else View.GONE
            root.findViewById<ImageButton>(R.id.sideCollapseButton)?.let { button ->
                button.rotation = if (show) 180f else 0f
                button.contentDescription = if (show) "Contraer navegación" else "Expandir navegación"
            }
            root.findViewById<TextView>(R.id.sideMyListBadge)?.let { badge ->
                val count = badge.tag as? Int ?: 0
                badge.text = if (count > 99) "99+" else count.toString()
                badge.visibility = if (show && count > 0) View.VISIBLE else View.GONE
            }
            fadingDetails.forEach { it.alpha = 1f }

            root.findViewById<View>(R.id.sideSearchContainer)?.let { search ->
                search.setPaddingRelative(if (show) dp(context, 10) else 0, 0, if (show) dp(context, 6) else 0, 0)
                (search as? LinearLayout)?.gravity = android.view.Gravity.CENTER_VERTICAL
            }
            root.findViewById<ImageButton>(R.id.sideSearchAction)?.let { button ->
                button.layoutParams = button.layoutParams.apply {
                    width = if (show) dp(context, 36) else ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
            labels.forEach { (id, label) ->
                root.findViewById<TextView>(id)?.let { item ->
                    item.text = if (show) label else ""
                    item.gravity = if (show) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
                    item.compoundDrawablePadding = if (show) dp(context, 13) else 0
                    item.setPaddingRelative(if (show) dp(context, 12) else 0, 0, if (show) dp(context, 8) else 0, 0)
                    item.contentDescription = label
                }
            }
            root.setPaddingRelative(if (show) dp(context, 18) else dp(context, 6), dp(context, 22), if (show) dp(context, 18) else dp(context, 6), dp(context, 16))
            root.findViewById<LinearLayout>(R.id.sideBrandRow)?.gravity =
                if (show) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
            contentScroll?.let { scroll ->
                scroll.setPaddingRelative(
                    dp(context, if (show) EXPANDED_CONTENT_INSET_DP else COLLAPSED_CONTENT_INSET_DP),
                    scroll.paddingTop,
                    scroll.paddingEnd,
                    scroll.paddingBottom
                )
            }
        }

        fun animateDetails(alpha: Float, delay: Long = 0L) {
            fadingDetails.forEach { view ->
                view.animate().cancel()
                view.animate().alpha(alpha).setStartDelay(delay).setDuration(LABEL_FADE_MS).start()
            }
        }

        fun setExpanded(show: Boolean, animated: Boolean) {
            if (expanded == show) return
            expanded = show
            val params = root.layoutParams ?: return
            val from = params.width.takeIf { it > 0 } ?: collapsedWidth
            val to = if (show) expandedWidth else collapsedWidth
            animator?.cancel()

            if (!animated) {
                params.width = to
                root.layoutParams = params
                applyDetails(show)
                return
            }

            if (show) {
                applyDetails(true)
                fadingDetails.forEach { it.alpha = 0f }
                animateDetails(1f, delay = 65L)
            } else {
                animateDetails(0f)
            }

            animator = ValueAnimator.ofInt(from, to).apply {
                duration = ANIMATION_MS
                startDelay = if (show) 0L else COLLAPSE_LEAD_MS
                interpolator = DecelerateInterpolator(1.7f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        if (!show) applyDetails(false)
                    }
                })
                addUpdateListener { value ->
                    params.width = value.animatedValue as Int
                    root.layoutParams = params
                }
                start()
            }
        }

        root.findViewById<View>(R.id.sideCollapseButton)?.setOnClickListener {
            setExpanded(!expanded, animated = true)
        }

        root.layoutParams?.let { params ->
            params.width = collapsedWidth
            root.layoutParams = params
        }
        updateMyListCount(root, FavoritesManager.getAll(context).count { !it.isChannel })
        applyDetails(false)
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            val focusInside = newFocus != null && isDescendantOf(newFocus, root)
            setExpanded(focusInside, animated = true)
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                animator?.cancel()
                fadingDetails.forEach { it.animate().cancel() }
            }
        })
        root.post {
            val currentFocus = activity.currentFocus
            setExpanded(currentFocus != null && isDescendantOf(currentFocus, root), animated = true)
        }
    }

    fun updateMyListCount(sidebar: View, count: Int) {
        val badge = sidebar.findViewById<TextView>(R.id.sideMyListBadge) ?: return
        badge.tag = count.coerceAtLeast(0)
        badge.text = if (count > 99) "99+" else count.coerceAtLeast(0).toString()
        val expandedWidth = dp(sidebar.context, 130)
        val isExpanded = (sidebar.layoutParams?.width ?: sidebar.width) >= expandedWidth
        badge.visibility = if (isExpanded && count > 0) View.VISIBLE else View.GONE
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

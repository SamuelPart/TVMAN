package com.tvman.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Ad placements are intentionally inert in the TVMAN TV build. */
object NativeAds {
    const val VARIANT_MEDIA = 0
    const val VARIANT_COMPACT = 1
    const val GRID_AD_ROWS = 4
    const val GRID_AD_TYPE = 0x7E0000F7

    fun <T> interleaveWithAds(items: List<T>, columns: Int): List<Any?> = items.map { it as Any? }
    fun isAdMarker(item: Any?): Boolean = false

    fun attachRecycler(
        activity: Activity,
        slot: FrameLayout,
        variant: Int = VARIANT_COMPACT,
        onResult: (Boolean) -> Unit = {}
    ) {
        slot.visibility = View.GONE
        onResult(false)
    }

    fun attach(activity: Activity, slot: ViewGroup, variant: Int = VARIANT_MEDIA) {
        slot.visibility = View.GONE
    }

    fun createAdSlot(parent: ViewGroup): FrameLayout = FrameLayout(parent.context).apply {
        visibility = View.GONE
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
    }
}

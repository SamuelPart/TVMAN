package com.tvman.app

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TVManApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(TVScreenAdapter())

        // Keep playlist/scraper and catalog refresh workflows from the IPTV source branch.
        ScraperConfig.loadCached(this)
        CoroutineScope(Dispatchers.IO).launch { ScraperConfig.refresh(this@TVManApplication) }
        CoroutineScope(Dispatchers.IO).launch { CineRepository.prefetchCatalog(this@TVManApplication) }
        CatalogBot.start(this)
    }

    /** Applies TV-wide canvas, system bars, and DPAD focus behavior to every screen. */
    private inner class TVScreenAdapter : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            val videoScreen = activity is PlayerActivity || activity is WebVideoPlayerActivity || activity is WallpaperViewerActivity
            if (videoScreen) return
            activity.window.statusBarColor = Color.WHITE
            activity.window.navigationBarColor = Color.WHITE
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.post {
                if (content.childCount == 0) return@post
                val root = content.getChildAt(0)
                root.setBackgroundColor(Color.rgb(246, 248, 252))
                prepareRemoteFocus(root, activity.resources.displayMetrics.density)
            }
        }

        private fun prepareRemoteFocus(view: View, density: Float) {
            if (view.visibility == View.GONE) return
            if (view.isClickable || view.isLongClickable) {
                view.isFocusable = true
                view.minimumWidth = maxOf(view.minimumWidth, (48 * density).toInt())
                view.minimumHeight = maxOf(view.minimumHeight, (48 * density).toInt())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = getDrawable(R.drawable.tv_focus_ring)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) prepareRemoteFocus(view.getChildAt(i), density)
            }
        }
    }
}

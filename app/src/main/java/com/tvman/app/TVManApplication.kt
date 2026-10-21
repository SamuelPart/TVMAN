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
        override fun onActivityPaused(activity: Activity) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
            val focused = root.findFocus() ?: return
            if (focused.id != View.NO_ID) {
                activity.getSharedPreferences("tv_remote_state", Activity.MODE_PRIVATE).edit()
                    .putInt(activity.javaClass.name, focused.id)
                    .apply()
            }
        }
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            val videoScreen = activity is PlayerActivity || activity is WebVideoPlayerActivity || activity is WallpaperViewerActivity
            if (videoScreen) return
            if (activity is TvHomeActivity) {
                activity.window.statusBarColor = Color.TRANSPARENT
                activity.window.navigationBarColor = Color.TRANSPARENT
                activity.window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                // Keep system chrome visually integrated with the TV canvas.
                activity.window.statusBarColor = Color.rgb(5, 10, 16)
                activity.window.navigationBarColor = Color.rgb(5, 10, 16)
                activity.window.decorView.systemUiVisibility = 0
            }
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            content.post {
                if (content.childCount == 0) return@post
                val root = content.getChildAt(0)
                prepareRemoteFocus(root, activity.resources.displayMetrics.density)
                val savedId = activity.getSharedPreferences("tv_remote_state", Activity.MODE_PRIVATE)
                    .getInt(activity.javaClass.name, View.NO_ID)
                if (savedId != View.NO_ID) {
                    root.postDelayed({
                        val saved = root.findViewById<View>(savedId)
                        val homeLoading = activity is TvHomeActivity &&
                            activity.findViewById<View>(R.id.tvLoadingOverlay)?.visibility != View.GONE
                        if (homeLoading) return@postDelayed
                        if (saved != null && saved.isShown && saved.isFocusable) {
                            saved.requestFocus()
                        } else if (root.findFocus() == null || root.findFocus() === root) {
                            findFirstRemoteAction(root)?.requestFocus()
                        }
                    }, 120L)
                }
            }
        }

        private val preparedRecyclerViews = java.util.WeakHashMap<androidx.recyclerview.widget.RecyclerView, Boolean>()

        private fun findFirstRemoteAction(view: View): View? {
            if (!view.isShown || !view.isEnabled) return null
            if (view.isClickable && view.isFocusable) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    findFirstRemoteAction(view.getChildAt(index))?.let { return it }
                }
            }
            return null
        }

        private fun inferRemoteLabel(view: View): String? {
            val label = (view as? android.widget.TextView)?.text?.toString()?.trim().orEmpty()
            if (label.any { it.isLetterOrDigit() }) return label
            val idName = if (view.id != View.NO_ID) {
                try { view.resources.getResourceEntryName(view.id).lowercase() } catch (_: Exception) { "" }
            } else ""
            return when {
                "back" in idName || "close" in idName -> "Volver"
                "cancel" in idName -> "Cancelar"
                "search" in idName -> "Buscar"
                "playpause" in idName || "play_pause" in idName -> "Reproducir o pausar"
                "play" in idName -> "Reproducir"
                "pause" in idName -> "Pausar"
                "favorite" in idName || "like" in idName -> "Favoritos"
                "delete" in idName || "clear" in idName -> "Borrar"
                "settings" in idName || "gear" in idName -> "Ajustes"
                "history" in idName -> "Historial"
                "filter" in idName -> "Filtrar"
                "cast" in idName -> "Transmitir"
                "share" in idName -> "Compartir"
                "profile" in idName -> "Perfil"
                "add" in idName -> "Añadir"
                else -> null
            }
        }

        private fun prepareRemoteFocus(view: View, density: Float) {
            if (view.isClickable || view.isLongClickable) {
                view.isFocusable = true
                if (view.contentDescription.isNullOrBlank()) {
                    view.contentDescription = inferRemoteLabel(view)
                }
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                view.minimumWidth = maxOf(view.minimumWidth, (56 * density).toInt())
                view.minimumHeight = maxOf(view.minimumHeight, (56 * density).toInt())
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    view.foreground = getDrawable(R.drawable.tv_focus_ring)
                }
            }
            if (view is androidx.recyclerview.widget.RecyclerView && !preparedRecyclerViews.containsKey(view)) {
                preparedRecyclerViews[view] = true
                view.addOnChildAttachStateChangeListener(object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(child: View) = prepareRemoteFocus(child, density)
                    override fun onChildViewDetachedFromWindow(child: View) = Unit
                })
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) prepareRemoteFocus(view.getChildAt(i), density)
            }
        }
    }
}

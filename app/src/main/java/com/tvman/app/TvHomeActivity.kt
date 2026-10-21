package com.tvman.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Cinematic Android TV home: hero and poster rows are populated from the TMDB-enriched catalog. */
class TvHomeActivity : AppCompatActivity() {
    private lateinit var posterRow: LinearLayout
    private lateinit var popularRow: LinearLayout
    private lateinit var actionRow: LinearLayout
    private lateinit var sciFiRow: LinearLayout
    private lateinit var familyRow: LinearLayout
    private lateinit var homeSections: LinearLayout
    private lateinit var backdrop: ImageView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var addButton: TextView
    private lateinit var postersTitle: TextView
    private lateinit var continueTitle: TextView
    private lateinit var continueRow: LinearLayout
    private lateinit var loadingOverlay: View
    private lateinit var catalogErrorOverlay: View
    private lateinit var previewPanel: View
    private lateinit var previewTitle: TextView
    private lateinit var previewMeta: TextView
    private lateinit var previewGenres: TextView
    private lateinit var previewOverview: TextView
    private val previewHandler = Handler(Looper.getMainLooper())
    private var pendingPreviewBackdrop: Runnable? = null
    private var pendingPreviewDismiss: Runnable? = null
    private var loadingDismissed = false
    private var homeEntranceStarted = false
    private var catalog: List<CineMedia> = emptyList()
    private var featured: CineMedia? = null
    private var featuredCandidates: List<CineMedia> = emptyList()
    private var featuredIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_home)

        posterRow = findViewById(R.id.tvPosterRow)
        popularRow = findViewById(R.id.tvPopularRow)
        actionRow = findViewById(R.id.tvActionRow)
        sciFiRow = findViewById(R.id.tvSciFiRow)
        familyRow = findViewById(R.id.tvFamilyRow)
        homeSections = findViewById(R.id.tvHomeSections)
        backdrop = findViewById(R.id.tvBackdrop)
        titleView = findViewById(R.id.tvHeroTitle)
        metaView = findViewById(R.id.tvHeroMeta)
        descriptionView = findViewById(R.id.tvHeroDescription)
        addButton = findViewById(R.id.tvHeroAdd)
        postersTitle = findViewById(R.id.tvPostersTitle)
        continueTitle = findViewById(R.id.tvContinueTitle)
        continueRow = findViewById(R.id.tvContinueRow)
        loadingOverlay = findViewById(R.id.tvLoadingOverlay)
        catalogErrorOverlay = findViewById(R.id.tvCatalogErrorOverlay)
        previewPanel = findViewById(R.id.tvPosterPreview)
        previewTitle = findViewById(R.id.tvPosterPreviewTitle)
        previewMeta = findViewById(R.id.tvPosterPreviewMeta)
        previewGenres = findViewById(R.id.tvPosterPreviewGenres)
        previewOverview = findViewById(R.id.tvPosterPreviewOverview)
        findViewById<View>(R.id.sideNavHome).isSelected = true
        TvSidebarController.install(
            this,
            findViewById(R.id.tvTopBar),
            findViewById(R.id.tvContentScroll)
        )

        loadingOverlay.alpha = 1f
        findViewById<View>(R.id.tvCatalogRetry).setOnClickListener {
            catalogErrorOverlay.visibility = View.GONE
            loadingDismissed = false
            loadingOverlay.visibility = View.VISIBLE
            loadingOverlay.alpha = 1f
            loadingOverlay.requestFocus()
            scheduleLoadingTimeout()
            loadCatalog()
        }
        scheduleLoadingTimeout()

        findViewById<View>(R.id.sideNavHome).setOnClickListener { findViewById<View>(R.id.sideNavHome).requestFocus() }
        findViewById<View>(R.id.sideNavContinue).setOnClickListener { openMainTab(MainActivity.TAB_SEARCH, cineAction = "continue") }
        findViewById<View>(R.id.sideNavMyList).setOnClickListener { openMainTab(MainActivity.TAB_CINE, cineAction = "favorites") }
        findViewById<View>(R.id.sideNavChannels).setOnClickListener { openMainTab(MainActivity.TAB_CHANNELS) }
        findViewById<View>(R.id.sideNavMovies).setOnClickListener { openMainTab(MainActivity.TAB_CINE, cineType = "movie") }
        findViewById<View>(R.id.sideNavSeries).setOnClickListener { openMainTab(MainActivity.TAB_CINE, cineType = "series") }
        findViewById<View>(R.id.sideNavPopular).setOnClickListener { openMainTab(MainActivity.TAB_CINE, cineMood = "top") }
        findViewById<View>(R.id.sideNavReleases).setOnClickListener { openMainTab(MainActivity.TAB_CINE, cineType = "new") }
        findViewById<View>(R.id.sideNavGames).setOnClickListener {
            Toast.makeText(this, "Juegos próximamente", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.sideNavSettings).setOnClickListener { openMainTab(MainActivity.TAB_SETTINGS) }
        findViewById<View>(R.id.sideNavExit).setOnClickListener { finishAffinity() }

        findViewById<View>(R.id.tvHeroPlay).setOnClickListener { featured?.let(::openMedia) ?: openMainTab(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvHeroPrev).setOnClickListener { moveFeatured(-1) }
        findViewById<View>(R.id.tvHeroNext).setOnClickListener { moveFeatured(1) }
        addButton.setOnClickListener { toggleFeaturedFavorite() }
        findViewById<View>(R.id.tvPostersHint).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
        loadingOverlay.requestFocus()
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.tvTopBar)?.let { sidebar ->
            TvSidebarController.updateMyListCount(sidebar, FavoritesManager.getAll(this).count { !it.isChannel })
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) &&
            (currentFocus?.id == R.id.tvHeroPrev || currentFocus?.id == R.id.tvHeroNext) &&
            featuredCandidates.size > 1) {
            moveFeatured(if (event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private val homeSectionKeys = listOf("recommendations", "popular", "action", "scifi", "family")
    private val homeSectionLabels = mapOf(
        "recommendations" to "Recomendados / Mi lista",
        "popular" to "Lo más visto",
        "action" to "Acción y aventura",
        "scifi" to "Ciencia ficción",
        "family" to "Familia y animación"
    )

    private fun orderedHomeSections(): MutableList<String> {
        val saved = getSharedPreferences("tv_home", MODE_PRIVATE)
            .getString("home_section_order", "").orEmpty()
            .split(",").filter { it in homeSectionKeys }.distinct()
        return (saved + homeSectionKeys.filterNot { it in saved }).toMutableList()
    }

    private fun applyHomeSections() {
        val prefs = getSharedPreferences("tv_home", MODE_PRIVATE)
        val sections = mapOf(
            "recommendations" to findViewById<LinearLayout>(R.id.tvSectionRecommendations),
            "popular" to findViewById<LinearLayout>(R.id.tvSectionPopular),
            "action" to findViewById<LinearLayout>(R.id.tvSectionAction),
            "scifi" to findViewById<LinearLayout>(R.id.tvSectionSciFi),
            "family" to findViewById<LinearLayout>(R.id.tvSectionFamily)
        )
        val rowCounts = mapOf(
            "recommendations" to posterRow.childCount,
            "popular" to popularRow.childCount,
            "action" to actionRow.childCount,
            "scifi" to sciFiRow.childCount,
            "family" to familyRow.childCount
        )
        homeSections.removeAllViews()
        orderedHomeSections().forEach { key ->
            val section = sections.getValue(key)
            section.visibility = if (prefs.getBoolean("visible_$key", true) && (rowCounts[key] ?: 0) > 0)
                View.VISIBLE else View.GONE
            homeSections.addView(section)
        }
    }

    private fun showHomeCustomization() {
        val prefs = getSharedPreferences("tv_home", MODE_PRIVATE)
        val enabled = homeSectionKeys.associateWith { key -> prefs.getBoolean("visible_$key", true) }.toMutableMap()
        val order = orderedHomeSections()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        }

        fun redraw(focusKey: String? = null) {
            list.removeAllViews()
            order.forEachIndexed { index, key ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val checkbox = android.widget.CheckBox(this).apply {
                    text = homeSectionLabels.getValue(key)
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    isChecked = enabled[key] == true
                    minHeight = dp(56)
                    layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
                    tag = "check:$key"
                    contentDescription = "Mostrar ${homeSectionLabels.getValue(key)}"
                    foreground = getDrawable(R.drawable.tv_focus_ring)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(163, 65, 255))
                    setOnCheckedChangeListener { _, checked -> enabled[key] = checked }
                }
                row.addView(checkbox)
                fun moveButton(label: String, description: String, delta: Int): TextView = TextView(this).apply {
                    text = label
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = getDrawable(R.drawable.tv_top_action)
                    foreground = getDrawable(R.drawable.tv_focus_ring)
                    isFocusable = true
                    isClickable = true
                    minWidth = dp(52)
                    minHeight = dp(52)
                    contentDescription = description
                    setOnClickListener {
                        val oldIndex = order.indexOf(key)
                        val newIndex = oldIndex + delta
                        if (newIndex in order.indices) {
                            order.removeAt(oldIndex)
                            order.add(newIndex, key)
                            redraw(key)
                        }
                    }
                }
                val up = moveButton("↑", "Mover ${homeSectionLabels.getValue(key)} hacia arriba", -1).apply { tag = "up:$key" }
                val down = moveButton("↓", "Mover ${homeSectionLabels.getValue(key)} hacia abajo", 1).apply { tag = "down:$key" }
                row.addView(up, LinearLayout.LayoutParams(dp(54), dp(56)).apply { marginStart = dp(6) })
                row.addView(down, LinearLayout.LayoutParams(dp(54), dp(56)).apply { marginStart = dp(6) })
                list.addView(row, LinearLayout.LayoutParams(-1, dp(62)))
            }
            if (focusKey != null) list.post { list.findViewWithTag<View>("check:$focusKey")?.requestFocus() }
        }
        redraw()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Personalizar Inicio")
            .setMessage("Activa las filas que quieres ver y usa las flechas para ordenarlas.")
            .setView(scroll)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()
        dialog.setOnShowListener {
            list.post { list.findViewWithTag<View>("check:${order.first()}")?.requestFocus() }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val editor = prefs.edit()
                    .putString("home_section_order", order.joinToString(","))
                homeSectionKeys.forEach { key -> editor.putBoolean("visible_$key", enabled[key] == true) }
                editor.apply()
                applyHomeSections()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun scheduleLoadingTimeout() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && catalog.isEmpty()) showHomeCatalogError()
            else if (!isFinishing) dismissLoadingOverlay()
        }, 12_000L)
    }

    private fun showHomeCatalogError() {
        if (catalogErrorOverlay.visibility == View.VISIBLE) return
        dismissLoadingOverlay()
        catalogErrorOverlay.visibility = View.VISIBLE
        catalogErrorOverlay.postDelayed({ findViewById<View>(R.id.tvCatalogRetry).requestFocus() }, 360L)
    }

    private fun loadCatalog() {
        lifecycleScope.launch {
            val quick = withContext(Dispatchers.IO) {
                runCatching { CineRepository.getBundledQuickCatalog(this@TvHomeActivity) }.getOrDefault(emptyList())
            }
            if (quick.isNotEmpty()) {
                catalog = quick
                quick.forEach(CineRepository::applyPosterCache)
                displayCatalog()
            }

            val fullCatalog = withContext(Dispatchers.IO) {
                runCatching { CineRepository.getCineCatalog(this@TvHomeActivity) }.getOrDefault(emptyList())
            }
            if (fullCatalog.isNotEmpty()) {
                catalogErrorOverlay.visibility = View.GONE
                catalog = fullCatalog
                catalog.forEach(CineRepository::applyPosterCache)
                displayCatalog()
                val forArtwork = catalog.sortedByDescending { it.rating ?: -1.0 }.take(60)
                CineRepository.startPosterBaker(this@TvHomeActivity, forArtwork, lifecycleScope) {
                    catalog.forEach(CineRepository::applyPosterCache)
                    displayCatalog()
                }
            } else if (quick.isEmpty()) {
                showHomeCatalogError()
            }
        }
    }

    private fun displayCatalog() {
        val ranked = catalog.sortedByDescending { it.rating ?: -1.0 }
        val previousUrl = featured?.url
        featuredCandidates = ranked
            .filter { tmdbImage(it.backdropUrl) || tmdbImage(it.posterUrl) }
            .distinctBy { it.url }
            .take(12)
            .ifEmpty { ranked.take(1) }
        featuredIndex = featuredCandidates.indexOfFirst { it.url == previousUrl }
            .takeIf { it >= 0 } ?: 0
        featured = featuredCandidates.getOrNull(featuredIndex)
        featured?.let(::bindFeatured)
        val canBrowseHero = featuredCandidates.size > 1
        findViewById<View>(R.id.tvHeroPrev).visibility = if (canBrowseHero) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvHeroNext).visibility = if (canBrowseHero) View.VISIBLE else View.GONE

        val favorites = FavoritesManager.getAll(this)
            .filterNot { it.isChannel }
            .mapNotNull { saved -> catalog.firstOrNull { it.url == saved.url } ?: saved.media }
            .distinctBy { it.url }
        val recommendations = ranked.filter { it !== featured && tmdbImage(it.posterUrl) }
        postersTitle.text = if (favorites.isNotEmpty()) "MI LISTA" else "RECOMENDADOS PARA TI"
        fillPosterRow(posterRow, if (favorites.isNotEmpty()) favorites else recommendations.take(14))
        findViewById<TextView>(R.id.tvSecondRowTitle).text = "LO MÁS VISTO"
        fillPosterRow(popularRow, ranked.filter { it !== featured && tmdbImage(it.posterUrl) }.take(14))
        val editorialPool = ranked.asSequence()
            .filter { it.url != featured?.url && tmdbImage(it.posterUrl) }
            .take(500)
            .map { it to TasteProfile.genreKeysOf(it) }
            .toList()
        fillPosterRow(actionRow, editorialPool.filter { "acción" in it.second }.take(14).map { it.first })
        fillPosterRow(sciFiRow, editorialPool.filter { "ciencia ficción" in it.second }.take(14).map { it.first })
        fillPosterRow(familyRow, editorialPool.filter { "familia" in it.second || "animación" in it.second }.take(14).map { it.first })
        val resumeItems = ContinueWatchingManager.getAll(this)
            .filter { !it.isChannel && it.media != null }
            .take(12)
        fillContinueRow(resumeItems)
        val showContinue = continueRow.childCount > 0
        continueTitle.visibility = if (showContinue) View.VISIBLE else View.GONE
        continueRow.parent?.let { (it as? View)?.visibility = if (showContinue) View.VISIBLE else View.GONE }
        applyHomeSections()
        startHomeEntranceIfReady()
    }

    private fun moveFeatured(direction: Int) {
        if (featuredCandidates.size < 2) return
        featuredIndex = (featuredIndex + direction + featuredCandidates.size) % featuredCandidates.size
        val selected = featuredCandidates[featuredIndex]
        featured = selected
        bindFeatured(selected)
        if (loadingDismissed) {
            listOf(titleView, metaView, descriptionView).forEach { view ->
                view.animate().cancel()
                view.alpha = 0f
                view.translationY = dp(12).toFloat()
                view.animate().alpha(1f).translationY(0f).setDuration(260L).start()
            }
        }
    }

    private fun bindFeatured(media: CineMedia) {
        titleView.text = media.title.uppercase(Locale.getDefault())
        titleView.maxLines = if (media.title.length > 26) 2 else 1
        val year = media.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }
        val kind = if (media.type == "series") "SERIE" else "PELÍCULA"
        val rating = media.rating?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(Locale.US, it)}" }
        metaView.text = listOfNotNull(kind, rating, year, "TMDB").joinToString("   ·   ")
        descriptionView.text = media.overview?.takeIf { it.isNotBlank() }
            ?: "Descubre películas y series seleccionadas para disfrutar en pantalla grande."
        val backdropUrl = media.backdropUrl?.takeIf(::tmdbImage) ?: media.posterUrl?.takeIf(::tmdbImage)
        if (backdropUrl != null) {
            Glide.with(this)
                .load(backdropUrl.replace("/w780/", "/w1280/").replace("/w500/", "/w1280/"))
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(320))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                    ): Boolean = false

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        dismissLoadingOverlay()
                        return false
                    }
                })
                .into(backdrop)
        }
        val isSaved = FavoritesManager.isFavorite(this, media.url)
        addButton.text = if (isSaved) "✓" else "+"
        addButton.contentDescription = if (isSaved) "Quitar de Mi lista" else "Añadir a Mi lista"
    }

    private fun fillPosterRow(row: LinearLayout, items: List<CineMedia>) {
        row.removeAllViews()
        items.forEach { media ->
            val poster = media.posterUrl?.takeIf(::tmdbImage) ?: return@forEach
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                isFocusable = true
                isClickable = true
                tag = media
                contentDescription = posterAccessibilityLabel(media)
                foreground = getDrawable(R.drawable.tv_focus_ring)
                setPadding(dp(3), dp(3), dp(3), dp(5))
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(187)).apply { marginEnd = dp(15) }
                setOnClickListener { openMedia(media) }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = getDrawable(R.drawable.tv_poster_placeholder)
                clipToOutline = true
                contentDescription = media.title
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(151))
            }
            Glide.with(this).load(poster).centerCrop().placeholder(R.drawable.tv_poster_placeholder).into(image)
            val name = TextView(this).apply {
                text = media.title
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(7), dp(2), 0)
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(25))
            }
            card.addView(image)
            card.addView(name)
            card.setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(130).start()
                if (focused) {
                    showPosterPreview(media)
                    schedulePosterBackdropPreview(media, view)
                } else schedulePosterPreviewDismiss()
            }
            row.addView(card)
        }
    }

    private fun fillContinueRow(entries: List<ContinueWatchingManager.ResumeEntry>) {
        continueRow.removeAllViews()
        entries.forEach { entry ->
            val media = entry.media ?: return@forEach
            val poster = media.posterUrl?.takeIf(::tmdbImage) ?: return@forEach
            val percent = if (entry.durationMs > 0L)
                ((entry.positionMs * 100L) / entry.durationMs).toInt().coerceIn(0, 100) else 0
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                tag = media
                contentDescription = posterAccessibilityLabel(media) + ", $percent por ciento visto"
                foreground = getDrawable(R.drawable.tv_focus_ring)
                setPadding(dp(3), dp(3), dp(3), dp(5))
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(195)).apply { marginEnd = dp(15) }
                setOnClickListener {
                    clearPosterPreview()
                    startActivity(Intent(this@TvHomeActivity, PlayerActivity::class.java).apply {
                        putExtra("channelName", entry.title)
                        putExtra("channelUrl", entry.url)
                        putExtra("startPosition", entry.positionMs)
                        putExtra("cineMedia", media)
                        putExtra("isLiveTv", false)
                    })
                }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = getDrawable(R.drawable.tv_poster_placeholder)
                clipToOutline = true
                contentDescription = media.title
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(148))
            }
            Glide.with(this).load(poster).centerCrop()
                .placeholder(R.drawable.tv_poster_placeholder).into(image)
            val name = TextView(this).apply {
                text = media.title
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(6), dp(2), 0)
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(22))
            }
            val progress = android.widget.ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                this.progress = percent
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(163, 65, 255))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x66FFFFFF)
                contentDescription = "$percent por ciento visto"
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(4)).apply {
                    topMargin = dp(5)
                    marginStart = dp(1)
                }
            }
            card.addView(image)
            card.addView(name)
            card.addView(progress)
            card.setOnFocusChangeListener { view, focused ->
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.06f else 1f)
                    .scaleY(if (focused) 1.06f else 1f).setDuration(130).start()
                if (focused) {
                    showPosterPreview(media)
                    schedulePosterBackdropPreview(media, view)
                } else schedulePosterPreviewDismiss()
            }
            continueRow.addView(card)
        }
    }

    private fun posterAccessibilityLabel(media: CineMedia): String {
        val year = media.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }
        val genres = TasteProfile.genreKeysOf(media).take(2).joinToString(", ")
        return listOfNotNull(media.title, year, genres.takeIf { it.isNotBlank() }).joinToString(". ")
    }

    private fun showPosterPreview(media: CineMedia) {
        pendingPreviewDismiss?.let(previewHandler::removeCallbacks)
        pendingPreviewDismiss = null
        pendingPreviewBackdrop?.let(previewHandler::removeCallbacks)
        pendingPreviewBackdrop = null

        previewTitle.text = media.title
        val year = media.releaseDate?.take(4)?.takeIf { it.all(Char::isDigit) }
        val rating = media.rating?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(Locale.US, it)}" }
        previewMeta.text = listOfNotNull(
            if (media.type == "series") "SERIE" else "PELÍCULA", year, rating
        ).joinToString("   ·   ")
        val genres = TasteProfile.genreKeysOf(media)
        previewGenres.text = genres.take(3).joinToString("   ·   ").ifBlank { "CATÁLOGO TMDB" }
        previewOverview.text = media.overview?.takeIf { it.isNotBlank() }
            ?: "Explora este título para conocer más detalles."

        if (previewPanel.visibility != View.VISIBLE) {
            previewPanel.alpha = 0f
            previewPanel.translationX = dp(16).toFloat()
            previewPanel.visibility = View.VISIBLE
        }
        previewPanel.animate().cancel()
        previewPanel.animate().alpha(1f).translationX(0f).setDuration(220L).start()
    }

    private fun schedulePosterBackdropPreview(media: CineMedia, card: View) {
        pendingPreviewBackdrop?.let(previewHandler::removeCallbacks)
        val preview = Runnable {
            if (card.hasFocus()) loadBackdropFor(media)
        }
        pendingPreviewBackdrop = preview
        previewHandler.postDelayed(preview, 700L)
    }

    private fun schedulePosterPreviewDismiss() {
        pendingPreviewDismiss?.let(previewHandler::removeCallbacks)
        val dismiss = Runnable {
            if (currentFocus?.tag !is CineMedia) {
                previewPanel.animate().cancel()
                previewPanel.animate().alpha(0f).translationX(dp(12).toFloat())
                    .setDuration(180L).withEndAction {
                        if (currentFocus?.tag !is CineMedia) {
                            previewPanel.visibility = View.GONE
                            featured?.let(::loadBackdropFor)
                        }
                    }.start()
            }
        }
        pendingPreviewDismiss = dismiss
        previewHandler.postDelayed(dismiss, 180L)
    }

    private fun loadBackdropFor(media: CineMedia) {
        val imageUrl = media.backdropUrl?.takeIf(::tmdbImage) ?: media.posterUrl?.takeIf(::tmdbImage) ?: return
        Glide.with(this)
            .load(imageUrl.replace("/w780/", "/w1280/").replace("/w500/", "/w1280/"))
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(320))
            .into(backdrop)
    }

    override fun onDestroy() {
        previewHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun clearPosterPreview() {
        pendingPreviewBackdrop?.let(previewHandler::removeCallbacks)
        pendingPreviewBackdrop = null
        pendingPreviewDismiss?.let(previewHandler::removeCallbacks)
        pendingPreviewDismiss = null
        previewPanel.animate().cancel()
        previewPanel.visibility = View.GONE
        previewPanel.alpha = 0f
        featured?.let(::loadBackdropFor)
    }

    private fun openMedia(media: CineMedia) {
        clearPosterPreview()
        val destination = if (media.type == "movie") CineMovieDetailActivity::class.java else CineTvShowDetailActivity::class.java
        startActivity(Intent(this, destination).putExtra("media", media))
        overridePendingTransition(R.anim.ps_enter, R.anim.ps_exit)
    }

    private fun openMainTab(
        tab: String,
        cineType: String? = null,
        cineMood: String? = null,
        cineAction: String? = null,
        query: String? = null,
        focusCineSearch: Boolean = false,
        focusCineFilters: Boolean = false
    ) {
        clearPosterPreview()
        val intent = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TV_TAB, tab)
        cineType?.let { intent.putExtra(MainActivity.EXTRA_CINE_TYPE, it) }
        cineMood?.let { intent.putExtra(MainActivity.EXTRA_CINE_MOOD, it) }
        cineAction?.let { intent.putExtra(MainActivity.EXTRA_CINE_ACTION, it) }
        query?.takeIf { it.isNotBlank() }?.let { intent.putExtra(MainActivity.EXTRA_SEARCH_QUERY, it) }
        if (focusCineSearch) intent.putExtra(MainActivity.EXTRA_FOCUS_CINE_SEARCH, true)
        if (focusCineFilters) intent.putExtra(MainActivity.EXTRA_FOCUS_CINE_FILTERS, true)
        startActivity(intent)
        overridePendingTransition(R.anim.ps_enter, R.anim.ps_exit)
    }

    private fun toggleFeaturedFavorite() {
        val media = featured ?: run {
            Toast.makeText(this, "Cargando títulos de TMDB…", Toast.LENGTH_SHORT).show()
            return
        }
        val added = if (FavoritesManager.isFavorite(this, media.url)) {
            FavoritesManager.remove(this, media.url)
            false
        } else FavoritesManager.toggleMedia(this, media)
        addButton.text = if (added) "✓" else "+"
        Toast.makeText(this, if (added) "Añadido a Mi lista" else "Quitado de Mi lista", Toast.LENGTH_SHORT).show()
        displayCatalog()
    }

    private fun dismissLoadingOverlay() {
        if (loadingDismissed || !::loadingOverlay.isInitialized) return
        loadingDismissed = true
        loadingOverlay.animate().alpha(0f).setDuration(320L).withEndAction {
            loadingOverlay.visibility = View.GONE
            findViewById<View>(R.id.sideNavHome).requestFocus()
            startHomeEntranceIfReady()
        }.start()
    }

    /** Stagger the home sections and individual controls in after the loading screen. */
    private fun startHomeEntranceIfReady() {
        if (homeEntranceStarted || !loadingDismissed || catalog.isEmpty()) return
        homeEntranceStarted = true

        val entranceViews = listOf(
            R.id.tvHeroEyebrow, R.id.tvHeroTitle, R.id.tvHeroMeta, R.id.tvHeroDescription,
            R.id.tvHeroPlay, R.id.tvHeroAdd, R.id.tvHeroPrev, R.id.tvHeroNext,
            R.id.tvPostersTitle, R.id.tvPostersHint,
            R.id.tvPosterScroll, R.id.tvSecondRowTitle, R.id.tvPopularRow,
            R.id.tvContinueTitle, R.id.tvContinueScroll
        ).mapNotNull { findViewById<View>(it) }

        var delay = 0L
        entranceViews.forEach { view ->
            animateEntrance(view, delay, 18f)
            delay += 55L
        }
        listOf(posterRow, popularRow, continueRow).forEach { row ->
            for (index in 0 until row.childCount) {
                animateEntrance(row.getChildAt(index), delay, 24f)
                delay += 38L
            }
        }
    }

    private fun animateEntrance(view: View, delay: Long, distanceDp: Float) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(distanceDp.toInt()).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(420L)
            .start()
    }

    private fun tmdbImage(url: String?): Boolean = url?.startsWith("https://image.tmdb.org/") == true
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

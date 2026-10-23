package com.tvman.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Direct TV landing screen: toolbar, left navigation and a two-plus-four artwork grid. */
class TvHomeActivity : AppCompatActivity() {
    private lateinit var featuredRow: LinearLayout
    private lateinit var posterRow: LinearLayout
    private var renderedCatalogKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTvFullscreen()
        setContentView(R.layout.activity_tv_home)

        featuredRow = findViewById(R.id.homeFeaturedRow)
        posterRow = findViewById(R.id.homePosterRow)
        bindNavigation()
        findViewById<View>(R.id.homeSearch).setOnClickListener {
            startActivity(Intent(this, CineSearchActivity::class.java))
        }
        loadArtworkWithoutBlockingTheScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterTvFullscreen()
    }

    private fun bindNavigation() {
        findViewById<View>(R.id.navTv).setOnClickListener { openMainTab(MainActivity.TAB_CHANNELS) }
        findViewById<View>(R.id.navFeatured).setOnClickListener { openList("Destacado", "recent") }
        findViewById<View>(R.id.navMovies).setOnClickListener { openMainTab(MainActivity.TAB_CINE, "movie") }
        findViewById<View>(R.id.navSeries).setOnClickListener { openMainTab(MainActivity.TAB_CINE, "series") }
        findViewById<View>(R.id.navKids).setOnClickListener { openList("Kids", "genre", "familia") }
        findViewById<View>(R.id.navAnime).setOnClickListener { openList("Anime", "genre", "anime") }
        findViewById<View>(R.id.navExplore).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
    }

    /** Render packaged artwork first; catalog enrichment updates tiles later without a blocking splash. */
    private fun loadArtworkWithoutBlockingTheScreen() {
        lifecycleScope.launch {
            val bundled = withContext(Dispatchers.IO) {
                runCatching { CineRepository.getBundledQuickCatalog(this@TvHomeActivity) }.getOrDefault(emptyList())
            }
            if (bundled.isNotEmpty()) renderArtwork(bundled)

            val catalog = withContext(Dispatchers.IO) {
                runCatching { CineRepository.getCineCatalog(this@TvHomeActivity) }.getOrDefault(emptyList())
            }
            if (catalog.isNotEmpty()) {
                catalog.forEach(CineRepository::applyPosterCache)
                renderArtwork(catalog)
                val artworkCandidates = catalog
                    .filter { !it.backdropUrl.isNullOrBlank() || !it.posterUrl.isNullOrBlank() }
                    .take(36)
                if (artworkCandidates.isNotEmpty()) {
                    CineRepository.startPosterBaker(this@TvHomeActivity, artworkCandidates, lifecycleScope) {
                        if (!isFinishing && !isDestroyed) renderArtwork(catalog)
                    }
                }
            }
        }
    }

    private fun renderArtwork(source: List<CineMedia>) {
        val items = source.asSequence()
            .distinctBy { it.url }
            .filter { media -> !media.backdropUrl.isNullOrBlank() || !media.posterUrl.isNullOrBlank() || !media.rawLogo.isNullOrBlank() }
            .sortedByDescending { it.rating ?: -1.0 }
            .toList()
        if (items.isEmpty()) return
        val signature = items.take(6).joinToString("|") {
            "${it.url}:${it.backdropUrl}:${it.posterUrl}:${it.rawLogo}"
        }
        if (signature == renderedCatalogKey) return
        renderedCatalogKey = signature

        featuredRow.removeAllViews()
        posterRow.removeAllViews()
        items.take(2).forEachIndexed { index, media ->
            featuredRow.addView(createArtworkCard(media, featured = true, index = index))
        }
        items.drop(2).take(4).forEach { media ->
            posterRow.addView(createArtworkCard(media, featured = false))
        }
        featuredRow.post {
            if (currentFocus == null) featuredRow.getChildAt(0)?.requestFocus()
        }
    }

    private fun createArtworkCard(media: CineMedia, featured: Boolean, index: Int = 0): View {
        val outerMargin = if (featured) dp(3) else dp(2)
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = outerMargin
                marginEnd = outerMargin
            }
            isFocusable = true
            isClickable = true
            foreground = getDrawable(R.drawable.tv_focus_ring)
            contentDescription = media.title
            setOnClickListener { openMedia(media) }
        }

        val image = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = media.title
        }
        val artwork = if (featured) {
            media.backdropUrl?.takeIf { it.isNotBlank() }
                ?: media.posterUrl?.takeIf { it.isNotBlank() }
                ?: media.rawLogo
        } else {
            media.posterUrl?.takeIf { it.isNotBlank() }
                ?: media.backdropUrl?.takeIf { it.isNotBlank() }
                ?: media.rawLogo
        }
        Glide.with(this)
            .load(artwork)
            .centerCrop()
            .placeholder(R.drawable.tv_poster_placeholder)
            .error(R.drawable.tv_poster_placeholder)
            .into(image)
        frame.addView(image)

        if (featured) {
            val scrim = View(this).apply {
                background = getDrawable(R.drawable.bg_tv_home_card_scrim)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            frame.addView(scrim)
            val title = TextView(this).apply {
                text = media.title.uppercase(java.util.Locale.getDefault())
                setTextColor(Color.WHITE)
                textSize = if (media.title.length > 24) 23f else 28f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                maxLines = 2
                gravity = Gravity.BOTTOM or Gravity.START
                setShadowLayer(8f, 0f, 2f, Color.BLACK)
                setPadding(dp(20), dp(14), dp(20), dp(28))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                )
            }
            frame.addView(title)
            if (index == 0) frame.addView(createCarouselDots())
        }
        return frame
    }

    private fun createCarouselDots(): View {
        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(12),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(14) }
        }
        repeat(5) { index ->
            val dot = View(this).apply {
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (index == 2) Color.rgb(142, 80, 245) else 0xFFB8BBC1.toInt())
                    if (index == 2) cornerRadius = dp(8).toFloat()
                }
                background = shape
                layoutParams = LinearLayout.LayoutParams(
                    if (index == 2) dp(24) else dp(7),
                    dp(7)
                ).apply { marginStart = dp(4); marginEnd = dp(4) }
            }
            dots.addView(dot)
        }
        return dots
    }

    private fun openMedia(media: CineMedia) {
        val destination = if (media.type == "movie") {
            CineMovieDetailActivity::class.java
        } else {
            CineTvShowDetailActivity::class.java
        }
        startActivity(Intent(this, destination).putExtra("media", media))
    }

    private fun openMainTab(tab: String, cineType: String? = null) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TV_TAB, tab)
        cineType?.let { intent.putExtra(MainActivity.EXTRA_CINE_TYPE, it) }
        startActivity(intent)
    }

    private fun openList(title: String, kind: String, param: String = "") {
        startActivity(Intent(this, CinePopularAllActivity::class.java).apply {
            putExtra("title", title)
            putExtra("kind", kind)
            putExtra("param", param)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

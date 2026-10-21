package com.tvman.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Cinematic Android TV home: hero and poster rows are populated from the TMDB-enriched catalog. */
class TvHomeActivity : AppCompatActivity() {
    private lateinit var posterRow: LinearLayout
    private lateinit var popularRow: LinearLayout
    private lateinit var backdrop: ImageView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var addButton: TextView
    private lateinit var postersTitle: TextView
    private var catalog: List<CineMedia> = emptyList()
    private var featured: CineMedia? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_home)

        posterRow = findViewById(R.id.tvPosterRow)
        popularRow = findViewById(R.id.tvPopularRow)
        backdrop = findViewById(R.id.tvBackdrop)
        titleView = findViewById(R.id.tvHeroTitle)
        metaView = findViewById(R.id.tvHeroMeta)
        descriptionView = findViewById(R.id.tvHeroDescription)
        addButton = findViewById(R.id.tvHeroAdd)
        postersTitle = findViewById(R.id.tvPostersTitle)

        findViewById<View>(R.id.tvNavHome).setOnClickListener { findViewById<View>(R.id.tvNavHome).requestFocus() }
        findViewById<View>(R.id.tvNavMovies).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvNavSeries).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvNavKids).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvSearchAction).setOnClickListener { openMainTab(MainActivity.TAB_SEARCH) }
        findViewById<View>(R.id.tvProfile).setOnClickListener { openMainTab(MainActivity.TAB_SETTINGS) }
        findViewById<View>(R.id.tvHeroPlay).setOnClickListener { featured?.let(::openMedia) ?: openMainTab(MainActivity.TAB_CINE) }
        addButton.setOnClickListener { toggleFeaturedFavorite() }
        findViewById<View>(R.id.tvPostersHint).setOnClickListener { openMainTab(MainActivity.TAB_CINE) }
        findViewById<View>(R.id.tvNavHome).requestFocus()

        loadCatalog()
    }

    private fun loadCatalog() {
        lifecycleScope.launch {
            val quick = withContext(Dispatchers.IO) { CineRepository.getBundledQuickCatalog(this@TvHomeActivity) }
            if (quick.isNotEmpty()) {
                catalog = quick
                quick.forEach(CineRepository::applyPosterCache)
                displayCatalog()
            }

            val fullCatalog = withContext(Dispatchers.IO) { CineRepository.getCineCatalog(this@TvHomeActivity) }
            if (fullCatalog.isNotEmpty()) {
                catalog = fullCatalog
                catalog.forEach(CineRepository::applyPosterCache)
                displayCatalog()
                val forArtwork = catalog.sortedByDescending { it.rating ?: -1.0 }.take(60)
                CineRepository.startPosterBaker(this@TvHomeActivity, forArtwork, lifecycleScope) {
                    catalog.forEach(CineRepository::applyPosterCache)
                    displayCatalog()
                }
            }
        }
    }

    private fun displayCatalog() {
        val ranked = catalog.sortedByDescending { it.rating ?: -1.0 }
        val bestBackdrop = ranked.firstOrNull { tmdbImage(it.backdropUrl) }
        val bestPoster = ranked.firstOrNull { tmdbImage(it.posterUrl) }
        featured = bestBackdrop ?: bestPoster ?: ranked.firstOrNull()
        featured?.let(::bindFeatured)

        val favorites = FavoritesManager.getAll(this)
            .filterNot { it.isChannel }
            .mapNotNull { saved -> catalog.firstOrNull { it.url == saved.url } ?: saved.media }
            .distinctBy { it.url }
        val recommendations = ranked.filter { it !== featured && tmdbImage(it.posterUrl) }
        postersTitle.text = if (favorites.isNotEmpty()) "MI LISTA" else "RECOMENDADOS PARA TI"
        fillPosterRow(posterRow, if (favorites.isNotEmpty()) favorites else recommendations.take(14))
        findViewById<TextView>(R.id.tvSecondRowTitle).text = "LO MÁS VISTO"
        fillPosterRow(popularRow, ranked.filter { it !== featured && tmdbImage(it.posterUrl) }.take(14))
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
            Glide.with(this).load(backdropUrl.replace("/w780/", "/w1280/").replace("/w500/", "/w1280/"))
                .centerCrop().into(backdrop)
        }
        addButton.text = if (FavoritesManager.isFavorite(this, media.url)) "✓" else "+"
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
            }
            row.addView(card)
        }
    }

    private fun openMedia(media: CineMedia) {
        val destination = if (media.type == "movie") CineMovieDetailActivity::class.java else CineTvShowDetailActivity::class.java
        startActivity(Intent(this, destination).putExtra("media", media))
    }

    private fun openMainTab(tab: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TV_TAB, tab))
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

    private fun tmdbImage(url: String?): Boolean = url?.startsWith("https://image.tmdb.org/") == true
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

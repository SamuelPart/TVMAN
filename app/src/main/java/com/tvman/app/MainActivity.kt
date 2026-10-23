package com.tvman.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import kotlin.math.roundToInt
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.FrameLayout
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvman.app.databinding.ActivityMainBinding
import androidx.recyclerview.widget.PagerSnapHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TV_TAB = "tvman_tv_tab"
        const val TAB_CHANNELS = "channels"
        const val TAB_CINE = "cine"
        const val TAB_SEARCH = "search"
        const val TAB_SETTINGS = "settings"
        const val EXTRA_CINE_TYPE = "tvman_cine_type"
        const val EXTRA_CINE_ACTION = "tvman_cine_action"
    }

    private lateinit var binding: ActivityMainBinding

    
    private lateinit var channelsAdapter: ChannelAdapter
    private lateinit var channelSectionsAdapter: TvChannelCategoryAdapter
    private var channelSectionInitialized = false
    private lateinit var searchAdapter: ChannelAdapter
    private lateinit var countryAdapter: CategoryAdapter
    private lateinit var searchCountryAdapter: CategoryAdapter
    
    private var allChannels: List<Channel> = emptyList()
    private var categories: List<String> = emptyList()
    private var countries: List<String> = emptyList()
    private var languages: List<String> = emptyList()
    
    // Filtering states
    private var selectedCategory: String = "Todos"
    private var selectedCountry: String = "Todos"
    private var selectedLanguage: String = "Todos"
    private var selectedAlphabet: String = "Sin Ordenar" // "Sin Ordenar", "A-Z", "Z-A"
    private var selectedSearchCountry: String = "Todos"
    private var selectedSearchGroup: String = "Todos"
    private var selectedSearchLanguage: String = "Todos"

    // Cine & Series states
    private lateinit var cineAdapter: CineSearchResultAdapter

    private var allCineMedia: List<CineMedia> = emptyList()
    private var selectedCineType: String = "all" // "all", "movie", "series"
    private var selectedCineGenre: String? = null
    private var selectedCineYear: String? = null

    // Public test lists (legal & free streams)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTvFullscreen()
        val hasSharedTitle = !intent.getStringExtra("deeplink_title").isNullOrBlank()
        if (intent.getStringExtra(EXTRA_TV_TAB) == null && !hasSharedTitle) {
            startActivity(Intent(this, TvHomeActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyAppTheme()

        // La barra de estado (hora, red, notificaciones) siempre visible,
        // con el color grafito de la app (definido en themes.xml).


        setupBottomNavigation()
        selectedCineType = intent.getStringExtra(EXTRA_CINE_TYPE) ?: "all"
        when (intent.getStringExtra(EXTRA_TV_TAB)) {
            TAB_CHANNELS -> binding.bottomNavigation.selectedItemId = R.id.navigation_channels
            TAB_CINE -> binding.bottomNavigation.selectedItemId = R.id.navigation_cine
            TAB_SEARCH -> binding.bottomNavigation.selectedItemId = R.id.navigation_search
            TAB_SETTINGS -> binding.bottomNavigation.selectedItemId = R.id.navigation_settings
        }
        setupRecyclerViews()
        configurePortraitChannelLayout()
        incrementOpenCounter()
        setupHomeBanner()
        setupHomeNewSection()
        setupHomeSections()
        setupSearchHistories()
        setupListeners()
        updateCineFilterButtons()
        renderCineGenres()
        if (binding.bottomNavigation.selectedItemId == R.id.navigation_cine) {
            binding.root.post { showCinePage() }
        }
        when (intent.getStringExtra(EXTRA_CINE_ACTION)) {
            "continue" -> startActivity(Intent(this, WatchHistoryActivity::class.java))
            "favorites" -> openCineList("MI LISTA", "favorites")
        }
        updateEmptyStates() // Initial state shows instructions everywhere
        
        // Auto-restore last successfully loaded playlist on app startup!
        restoreSavedPlaylist()


        // Load the TMDb integrated Cine & Series Catalog!
        loadCineCatalog()

        // ¿Llegamos desde un enlace compartido (tvman://ver?d=...)? Entra
        // directo al Cine; en cuanto el catalogo cargue, se abre la ficha.
        pendingDeepLink = intent.getStringExtra("deeplink_title") ?: DeepLink.fromIntent(intent)

    }

    private fun setupBottomNavigation() {
        // TV navigation is used on every supported screen, including phones running the TV interface.
        binding.bottomNavigation.visibility = View.GONE
        binding.tvmanNavBar.visibility = View.GONE
        updateTVMANNav(R.id.navigation_home)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            updateTVMANNav(item.itemId)
            when (item.itemId) {
                R.id.navigation_home -> { returnToTvHome(); true }
                R.id.navigation_channels -> {
                    showTab(View.GONE, View.VISIBLE, View.GONE, View.GONE, View.GONE)
                    binding.root.post { enterTvChannelDirectory() }
                    true
                }
                R.id.navigation_search -> {
                    showTab(View.GONE, View.GONE, View.GONE, View.GONE, View.VISIBLE)
                    binding.root.post { binding.tvChannelKeyboard.focusFirstKey() }
                    true
                }
                R.id.navigation_cine -> {
                    showTab(View.GONE, View.GONE, View.VISIBLE, View.GONE, View.GONE)
                    binding.root.post { showCinePage() }
                    true
                }
                R.id.navigation_settings -> {
                    showTab(View.GONE, View.GONE, View.GONE, View.VISIBLE, View.GONE)
                    true
                }
                else -> false
            }
        }
        binding.navBtnHome.setOnClickListener { returnToTvHome() }
        binding.navBtnChannels.setOnClickListener { binding.bottomNavigation.selectedItemId = R.id.navigation_channels }
        binding.navBtnSearch.setOnClickListener { binding.bottomNavigation.selectedItemId = R.id.navigation_search }
        binding.navBtnCine.setOnClickListener { binding.bottomNavigation.selectedItemId = R.id.navigation_cine }
        binding.navBtnSettings.setOnClickListener { binding.bottomNavigation.selectedItemId = R.id.navigation_settings }

        setupSidebarNavigation()
        TvSidebarController.install(this, findViewById(R.id.tvSidebar))
    }

    private fun setupSidebarNavigation() {
        findViewById<View>(R.id.sideNavHome).setOnClickListener { returnToTvHome() }
        findViewById<View>(R.id.sideNavMyList).setOnClickListener { openCineList("MI LISTA", "favorites") }
        findViewById<View>(R.id.sideNavChannels).setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.navigation_channels
        }
        findViewById<View>(R.id.sideNavMovies).setOnClickListener { showSidebarCine("movie") }
        findViewById<View>(R.id.sideNavSeries).setOnClickListener { showSidebarCine("series") }
        findViewById<View>(R.id.sideNavSettings).setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.navigation_settings
        }
    }

    private fun showSidebarCine(type: String) {
        selectedCineType = type
        selectedCineGenre = null
        selectedCineYear = null
        binding.bottomNavigation.selectedItemId = R.id.navigation_cine
        updateTVMANNav(R.id.navigation_cine)
        binding.root.post { showCinePage() }
    }

    private fun showCinePage() {
        // Cine inicia directamente en su catálogo de títulos.
        binding.cineCatalogScreen.visibility = View.VISIBLE
        updateCineFilterButtons()
        renderCineGenres()
        applyCineFilters()
        binding.rvCineGrid.requestFocus()
    }

    private fun returnToTvHome() {
        startActivity(Intent(this, TvHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun showTab(homeVis: Int, channelsVis: Int, cineVis: Int, browserVis: Int, searchVis: Int) {
        showTabAnimated(binding.containerHome, homeVis)
        showTabAnimated(binding.containerChannels, channelsVis)
        showTabAnimated(binding.containerCine, cineVis)
        showTabAnimated(binding.containerBrowser, browserVis)
        showTabAnimated(binding.containerSearch, searchVis)

    }


    /** PS5-style tab entry: the panel rises and springs into place. */
    private fun showTabAnimated(view: View, vis: Int) {
        view.visibility = vis
        if (vis == View.VISIBLE) {
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = 28f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun setupRecyclerViews() {
        // Directorio de TV: secciones a la izquierda y lista de sintonización a la derecha.
        binding.rvChannelCategories.layoutManager = LinearLayoutManager(this)
        channelSectionsAdapter = TvChannelCategoryAdapter(emptyList()) { section ->
            onTvChannelSectionSelected(section)
        }
        binding.rvChannelCategories.adapter = channelSectionsAdapter

        val channelsGridLayout = GridLayoutManager(this, 1)
        binding.rvChannelsGrid.layoutManager = channelsGridLayout
        channelsAdapter = ChannelAdapter(
            emptyList(),
            onChannelClick = { channel -> openPlayer(channel) },
            isFavorite = { FavoritesManager.isFavorite(this, it.url) },
            onFavoriteToggle = { channel ->
                val added = FavoritesManager.toggleChannel(this, channel)
                Toast.makeText(this, if (added) "Añadido a Favoritos" else "Quitado de Favoritos", Toast.LENGTH_SHORT).show()
                refreshTvChannelDirectory()
            },
        )
        binding.rvChannelsGrid.adapter = channelsAdapter
        channelsAdapter.tunerMode = true

        // Channel search has its own TV screen and a single-focus channel list.
        val searchGridLayout = GridLayoutManager(this, 1)
        binding.rvSearchGrid.layoutManager = searchGridLayout
        searchAdapter = ChannelAdapter(
            emptyList(),
            onChannelClick = { channel -> openPlayer(channel) },
            isFavorite = { FavoritesManager.isFavorite(this, it.url) },
            onFavoriteToggle = { channel ->
                val added = FavoritesManager.toggleChannel(this, channel)
                Toast.makeText(this, if (added) "Añadido a Favoritos" else "Quitado de Favoritos", Toast.LENGTH_SHORT).show()
                refreshTvChannelDirectory()
            },
        )
        binding.rvSearchGrid.adapter = searchAdapter
        searchAdapter.tunerMode = true

        // Catálogo Cine: una cuadrícula amplia junto al rail vertical de géneros.
        val cineGridLayout = GridLayoutManager(this, 5)
        binding.rvCineGrid.layoutManager = cineGridLayout
        cineAdapter = CineSearchResultAdapter(
            emptyList(),
            onMediaClick = { media -> openCineDetail(media) },
            isFavorite = { FavoritesManager.isFavorite(this, it.url) },
            onFavoriteToggle = { media ->
                val added = FavoritesManager.toggleMedia(this, media)
                Toast.makeText(this, if (added) "Añadido a Mi lista" else "Quitado de Mi lista", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvCineGrid.adapter = cineAdapter
        binding.rvCineGrid.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val total = recyclerView.adapter?.itemCount ?: return
                if (lm.findLastVisibleItemPosition() >= total - 8) cineLoadMore()
            }
        })

        binding.cineCatalogScreen.visibility = View.GONE
    }

    private fun configurePortraitChannelLayout() {
        if (resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) return
        binding.channelsBody.orientation = LinearLayout.VERTICAL
        binding.channelCategoryRail.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(248)
        )
        binding.channelsResultPanel.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply { topMargin = dp(12) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterTvFullscreen()
    }

    override fun onResume() {
        super.onResume()
        TvSidebarController.updateMyListCount(findViewById(R.id.tvSidebar), FavoritesManager.getAll(this).count { !it.isChannel })
        // Al volver a la app: aplica los posters horneados y repinta
        if (allCineMedia.isNotEmpty()) {
            allCineMedia.forEach { CineRepository.applyPosterCache(it) }
            if (::cineAdapter.isInitialized) cineAdapter.notifyDataSetChanged()
        }
        startHomeSearchRotator()
        resumeHomeBanner()
        applyAccentColor()
        handlePendingSettings()
        if (::channelsAdapter.isInitialized && binding.containerChannels.visibility == View.VISIBLE) {
            refreshTvChannelDirectory()
        }
        syncCineCatalogIfNeeded()
    }

    /** Si el BOT detectó cambios en GitHub (películas/series nuevas), refresca
     *  el catálogo y repinta el Cine sin reinstalar. */
    private fun syncCineCatalogIfNeeded() {
        if (!CatalogBot.consumeChangedFlag(this)) return
        lifecycleScope.launch {
            val sync = withContext(Dispatchers.IO) { CineRepository.refreshCatalog(this@MainActivity) }
            allCineMedia = sync.catalog
            applyCineFilters()
            refreshHomeSections()
            if (sync.addedTitles > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "🎬 Catálogo actualizado: ${sync.addedTitles} ${if (sync.addedTitles == 1) "título nuevo" else "títulos nuevos"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Aplica lo que pidieron las pantallas de Ajustes (cargar lista, limpiar
     *  o re-aplicar tema/acento/parental) al volver a MainActivity. */
    private fun handlePendingSettings() {
        SettingsSync.consumePendingLoad(this)?.let { url ->
            loadIptvList(url)
        }
        if (SettingsSync.consumePendingClear(this)) {
            clearPlaylist()
        }
        if (SettingsSync.consume(this)) {
            applyAppTheme()
            applyAccentColor()
            applyFiltersAndSorting()
            filterSearchTabUnified()
        }
    }

    /** Repaints the Home favorites strip from the local store. */
    /** Re-tints nav + cine segment with the chosen accent. */
    private fun applyAccentColor() {
        val navTint = AccentManager.navTintList(this)
        binding.bottomNavigation.itemIconTintList = navTint
        binding.bottomNavigation.itemTextColor = navTint
        updateCineFilterButtons()
    }

    private fun incrementOpenCounter() {
        val prefs = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
        prefs.edit().putInt("app_open_count", prefs.getInt("app_open_count", 0) + 1).apply()
    }

    // ================= HOME V4 - COVERFLOW =================

    // ================= BANNER DE INICIO + ULTIMAMENTE NUEVO + PAGINACION =================

    /** Repinta el banner y TODAS las secciones de Inicio tras cargar el catalogo. */
    private fun refreshHomeSections() {
        refreshHomeBanner()
        refreshHomeNewSection()
        buildHomeSectionsAsync()
    }

    /** BOT: crea la siguiente seccion aleatoria al final de Inicio (max 15),
     *  con loading propio y carga fuera del hilo de UI. */
    private fun maybeCreateRandomSection() {
        if (randomBusy || randomCreated >= 15) return
        if (!::homeSagasAdapter.isInitialized) return
        randomBusy = true
        val spec = randomSpecs[randomCursor % randomSpecs.size]
        randomCursor++
        val idx = randomCreated++

        val ctx = this
        fun dp2(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        // contenedor de la seccion
        val box = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        binding.containerRandomSections.addView(box)

        val tv = android.widget.TextView(ctx).apply {
            text = spec.first
            setTextColor(0xFFC9A96E.toInt()); textSize = 13f
            letterSpacing = 0.12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp2(22), 0, 0)
        }
        val pb = android.widget.ProgressBar(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dp2(30), dp2(30)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp2(10)
            }
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(0xFFC9A96E.toInt())
        }
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp2(10) }
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(ctx, 3)
            isNestedScrollingEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
        }
        box.addView(tv); box.addView(pb); box.addView(rv)

        lifecycleScope.launch {
            val pool = withContext(Dispatchers.Default) {
                allCineMedia
                    .filter { inScope(it) }
                    .filter { TasteProfile.genreKeysOf(it).contains(spec.second) }
                    .sortedByDescending { it.rating ?: -1.0 }
            }
            if (isFinishing || isDestroyed) return@launch
            pb.visibility = android.view.View.GONE
            if (pool.isEmpty()) {
                // Sin contenido para este genero: la seccion NO se crea
                binding.containerRandomSections.removeView(box)
                binding.containerRandomSections.postDelayed({ randomBusy = false }, 200)
                return@launch
            }
            val adapter = CineSearchResultAdapter(pool.shuffled().take(9), onMediaClick = { openCineDetail(it) })
            rv.adapter = adapter
            // botones
            fun pill(label: String, icon: Int, action: () -> Unit): android.widget.LinearLayout =
                android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    background = androidx.core.content.ContextCompat.getDrawable(ctx, R.drawable.bg_apple_glass_pill)
                    setPadding(dp2(18), dp2(10), dp2(18), dp2(10))
                    isClickable = true; isFocusable = true
                    addView(android.widget.ImageView(ctx).apply {
                        setImageResource(icon)
                        setColorFilter(0xFFC9A96E.toInt())
                        layoutParams = android.widget.LinearLayout.LayoutParams(dp2(15), dp2(15))
                    })
                    addView(android.widget.TextView(ctx).apply {
                        text = label
                        setTextColor(0xFFFFFFFF.toInt()); textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = dp2(7)
                        }
                    })
                    setOnClickListener { action() }
                }
            val row = android.widget.LinearLayout(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp2(12)
                }
                gravity = android.view.Gravity.CENTER
                orientation = android.widget.LinearLayout.HORIZONTAL
                addView(pill("Ver más", R.drawable.ic_ios_arrow_right) {
                    openCineList(spec.first, "genre", spec.second)
                })
                addView(android.view.View(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp2(10), 1)
                })
                addView(pill("Cambiar", R.drawable.ic_ios_refresh) {
                    adapter.updateList(pool.shuffled().take(9))
                })
            }
            box.addView(row)
            // pequeño respiro entre secciones para no sobrecargar
            binding.containerRandomSections.postDelayed({ randomBusy = false }, 350)
        }
    }

    /** Cablea las secciones STREAMING - PLATAFORMAS, EL NUEVO STREAMING DE
     *  HOY, ESTRENOS EN CINE y SAGAS (cuadriculas 3x3 con botones Ver mas y
     *  Cambiar, fila de logos y anuncio en bloque). */
    private fun setupHomeSections() {
        homeStreamingAdapter = CineSearchResultAdapter(emptyList(), onMediaClick = { openCineDetail(it) })
        homeStreamingAdapter.platformBadgeResolver = { m -> PlatformCatalog.logoOf(m) }
        binding.rvHomeStreaming.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        binding.rvHomeStreaming.adapter = homeStreamingAdapter
        binding.rvHomeStreaming.isNestedScrollingEnabled = false

        homeEstrenosAdapter = CineSearchResultAdapter(emptyList(), onMediaClick = { openCineDetail(it) })
        binding.rvHomeEstrenos.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        binding.rvHomeEstrenos.adapter = homeEstrenosAdapter
        binding.rvHomeEstrenos.isNestedScrollingEnabled = false

        platformAdapter = PlatformRowAdapter { p ->
            openCineList(p.name.uppercase(), "platform", p.key)
        }
        binding.rvHomePlatforms.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHomePlatforms.adapter = platformAdapter

        homeSagasAdapter = HomeSagasAdapter { openCineDetail(it) }
        binding.rvHomeSagas.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvHomeSagas.adapter = homeSagasAdapter
        binding.rvHomeSagas.isNestedScrollingEnabled = false

        binding.btnStreamingVerMas.setOnClickListener {
            it.springPress()
            openCineList("EL NUEVO STREAMING DE HOY", "new_month", "", badges = true, scope = scopeKind to scopeParam)
        }
        binding.btnStreamingCambiar.setOnClickListener {
            it.springPress()
            sectionsData?.let { d -> homeStreamingAdapter.updateList(d.streaming.shuffled().take(9)) }
        }
        binding.btnEstrenosVerMas.setOnClickListener {
            it.springPress()
            openCineList("ESTRENOS EN CINE", "recent", "", scope = scopeKind to scopeParam)
        }
        binding.btnEstrenosCambiar.setOnClickListener {
            it.springPress()
            sectionsData?.let { d -> homeEstrenosAdapter.updateList(d.estrenos.shuffled().take(9)) }
        }
        binding.btnSagasVerMas.setOnClickListener {
            it.springPress()
            openCineList("SAGAS", "sagas", "", scope = scopeKind to scopeParam)
        }
        binding.btnSagasCambiar.setOnClickListener {
            it.springPress()
            // Rota a la SIGUIENTE saga de las mejores (orden TMDB)
            sectionsData?.let { d ->
                if (d.sagaGroups.isNotEmpty()) {
                    d.sagaCursor = (d.sagaCursor + 1) % d.sagaGroups.size
                    homeSagasAdapter.submit(
                        d.sagaGroups[d.sagaCursor].second.take(14).map { SagaEntry.Poster(it) }
                    )
                }
            }
        }

        // BOT de secciones aleatorias: al llegar cerca del final, crea la
        // siguiente (con su loading) para bajar sin limite
        binding.containerHome.setOnScrollChangeListener { v, _, _, _, _ ->
            @Suppress("USELESS_CAST")
            val sv = v as? android.widget.ScrollView ?: return@setOnScrollChangeListener
            val child = sv.getChildAt(0) ?: return@setOnScrollChangeListener
            if (sv.scrollY + sv.height >= child.height - 700) maybeCreateRandomSection()
        }

        // Logos oficiales de las plataformas (TMDB watch/providers)
        lifecycleScope.launch {
            PlatformCatalog.fetchLogos(CineRepository.TMDB_API_KEY)
            platformAdapter.notifyDataSetChanged()
        }
    }

    /** Construye las secciones de Inicio FUERA del hilo de UI y las pinta
     *  ESCALONADAS (una a una, con loading sin texto) para que el catalogo
     *  grande nunca congele la pantalla. */
    private fun buildHomeSectionsAsync() {
        if (!::homeStreamingAdapter.isInitialized) return
        binding.pbHomeLoader.visibility = View.VISIBLE
        lifecycleScope.launch {
            val data = withContext(Dispatchers.Default) {
                val scoped = allCineMedia.filter { inScope(it) }
                val streaming = scoped
                    .filter { PlatformCatalog.keyOf(it) != null }
                    .sortedByDescending { it.releaseDate ?: "" }
                val estrenos = scoped
                    .filter { ((it.releaseDate ?: "").take(4).toIntOrNull() ?: 0) >= 2024 }
                    .sortedByDescending { it.releaseDate ?: "" }
                val sagaGroups = Sagas.buildGroups(scoped)
                // SOLO la saga TOP (mejor rating TMDB): carteles directos, sin titulo
                val sagasFlat = sagaGroups.firstOrNull()
                    ?.second?.take(14)?.map { SagaEntry.Poster(it) } ?: emptyList()
                HomeSectionsData(streaming, estrenos, sagasFlat, sagaGroups)
            }
            sectionsData = data
            // Carga escalonada: una seccion cada 120 ms
            homeStreamingAdapter.updateList(data.streaming.take(9))
            binding.rvHomeStreaming.postDelayed({
                homeEstrenosAdapter.updateList(data.estrenos.take(9))
                binding.rvHomeSagas.postDelayed({
                    homeSagasAdapter.submit(data.sagas)
                    binding.pbHomeLoader.visibility = View.GONE
                }, 120)
            }, 120)
        }
    }

    private val CINE_PAGE = 60
    private var cinePagingFull: List<CineMedia> = emptyList()
    private var cineShown = 0
    private var cineLoadingMore = false
    private var cineFilterJob: kotlinx.coroutines.Job? = null
    private var bannerRandom = false
    private var bannerRunnable: Runnable? = null
    private var bannerIdx = 0
    private var bannerSize = 0
    private var homeNewPool: List<CineMedia> = emptyList()
    private lateinit var homeNewAdapter: CineSearchResultAdapter
    private val bannerSlides = mutableListOf<CineMedia>()
    private lateinit var homeStreamingAdapter: CineSearchResultAdapter
    private lateinit var homeEstrenosAdapter: CineSearchResultAdapter
    private lateinit var platformAdapter: PlatformRowAdapter
    private lateinit var homeSagasAdapter: HomeSagasAdapter
    private var sectionsData: HomeSectionsData? = null
    private var scopeKind = "all"
    private var scopeParam = ""

    /** ¿Este titulo pertenece a la seccion elegida en los chips de Inicio? */
    private fun inScope(m: CineMedia): Boolean = when (scopeKind) {
        "type" -> if (scopeParam == "series") m.type != "movie" else m.type == "movie"
        "genre" -> m.title.lowercase().contains("animac") ||
            m.group.lowercase().contains("animac") ||
            m.group.lowercase().contains("anime") ||
            TasteProfile.genreKeysOf(m).contains("animación")
        "platform_all" -> PlatformCatalog.keyOf(m) != null
        else -> true
    }

    /** Cambia la seccion de Inicio: TODO se reconstruye con ese alcance. */
    private fun setHomeScope(kind: String, param: String) {
        if (scopeKind == kind && scopeParam == param) return
        scopeKind = kind; scopeParam = param
        // las secciones aleatorias ya creadas eran del alcance anterior
        binding.containerRandomSections.removeAllViews()
        randomCreated = 0
        randomCursor = 0
        paintSectionChips()
        refreshHomeSections()
    }

    /** Chip activo dorado, el resto en vidrio. */
    private fun paintSectionChips() {
        fun paint(chip: android.widget.TextView, active: Boolean) {
            chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_apple_glass_pill)
            chip.setTextColor(if (active) 0xFF0F2144.toInt() else 0xFFD6DAE4.toInt())
        }
        paint(binding.chipHomeInicio, scopeKind == "all")
        paint(binding.chipHomePeliculas, scopeKind == "type" && scopeParam == "movie")
        paint(binding.chipHomeSeries, scopeKind == "type" && scopeParam == "series")
        paint(binding.chipHomeAnimacion, scopeKind == "genre")
        paint(binding.chipHomePlataformas, scopeKind == "platform_all")
    }
    private var randomCreated = 0
    private var randomCursor = 0
    private var randomBusy = false
    private val randomSpecs = listOf(
        Pair("ACCIÓN Y ADRENALINA", "acción"),
        Pair("COMEDIAS PARA REÍR", "comedia"),
        Pair("TERROR DE NOCHE", "terror"),
        Pair("ROMANCE", "romance"),
        Pair("CIENCIA FICCIÓN", "ciencia ficción"),
        Pair("THRILLERS", "thriller"),
        Pair("FANTASÍA ÉPICA", "fantasía"),
        Pair("PARA LA FAMILIA", "familia"),
        Pair("DRAMA REAL", "drama"),
        Pair("MÚSICA Y CONCIERTOS", "música"),
        Pair("ÉPOCA E HISTORIA", "histórica"),
        Pair("ANIMACIÓN", "animación")
    )

    private data class HomeSectionsData(
        val streaming: List<CineMedia>,
        val estrenos: List<CineMedia>,
        val sagas: List<SagaEntry>,
        val sagaGroups: List<Pair<String, List<CineMedia>>>,
        var sagaCursor: Int = 0
    )

    /** Paginacion del grid de Cine: ancha de a [CINE_PAGE] con loading sin
     *  texto, para que catalogos extensos no congelen la UI. */
    private fun cineLoadMore() {
        if (cineLoadingMore || cineShown >= cinePagingFull.size) return
        cineLoadingMore = true
        binding.pbCineLoader.visibility = View.VISIBLE
        binding.rvCineGrid.postDelayed({
            cineShown = minOf(cineShown + CINE_PAGE, cinePagingFull.size)
            cineAdapter.updateList(cinePagingFull.take(cineShown))
            binding.pbCineLoader.visibility = View.GONE
            cineLoadingMore = false
        }, 450)
    }

    /** Configura el banner (rotacion manual cada 4.2s con puntos) y los
     *  botones Ver mas / Cambiar de la seccion Ultimamente Nuevo. */
    private fun setupHomeBanner() {
        binding.flipHomeBanner.inAnimation =
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rot_in)
        binding.flipHomeBanner.outAnimation =
            android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rot_out)

        // DESLIZAR con el dedo cambia la diapositiva; un toque abre el titulo
        val detector = android.view.GestureDetector(this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: android.view.MotionEvent): Boolean = true
                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                    val m = bannerSlides.getOrNull(bannerIdx)
                    if (m != null) openCineDetail(m) else shareAppPromo()
                    return true
                }
                override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent,
                                     vx: Float, vy: Float): Boolean {
                    if (kotlin.math.abs(vx) > kotlin.math.abs(vy) && kotlin.math.abs(vx) > 2200) {
                        if (vx < 0) showBannerAt(bannerIdx + 1) else showBannerAt(bannerIdx - 1)
                        return true
                    }
                    return false
                }
            })
        binding.flipHomeBanner.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev); true
        }
        binding.btnHomeVerMas.setOnClickListener {
            it.springPress()
            openCineList("ULTIMAMENTE NUEVO", "new_month", "", scope = scopeKind to scopeParam)
        }
        binding.btnHomeCambiar.setOnClickListener {
            it.springPress()
            bannerRandom = true
            refreshHomeBanner()
            if (::homeNewAdapter.isInitialized) homeNewAdapter.updateList(homeNewPool.shuffled().take(9))
        }
    }

    /** Seccion Ultimamente Nuevo: cuadricula 3x3 con lo ultimo del catalogo. */
    private fun setupHomeNewSection() {
        homeNewAdapter = CineSearchResultAdapter(emptyList(), onMediaClick = { openCineDetail(it) })
        binding.rvHomeNew.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        binding.rvHomeNew.adapter = homeNewAdapter
        binding.rvHomeNew.isNestedScrollingEnabled = false
    }

    private fun refreshHomeNewSection() {
        if (!::homeNewAdapter.isInitialized) return
        val scoped = allCineMedia.filter { !it.posterUrl.isNullOrBlank() && inScope(it) }
        val withPoster = if (scoped.isNotEmpty()) scoped else allCineMedia.filter { !it.posterUrl.isNullOrBlank() }
        homeNewPool = if (withPoster.size >= 9) withPoster.sortedByDescending { it.releaseDate ?: "" } else withPoster
        if (!bannerRandom) homeNewAdapter.updateList(homeNewPool.take(9))
    }

    /** (Re)construye las 8 diapositivas del banner: 7 titulos del catalogo y
     *  la ultima es publicidad propia de la app. En modo aleatorio muestra
     *  contenido al azar (boton Cambiar). */
    private fun refreshHomeBanner() {
        val flipper = binding.flipHomeBanner
        stopHomeBanner()
        flipper.removeAllViews()
        val scoped = allCineMedia.filter { !it.posterUrl.isNullOrBlank() && inScope(it) }
        // respaldo: si la seccion no tiene posters aun, usa el catalogo completo
        val withPoster = if (scoped.isNotEmpty()) scoped
        else allCineMedia.filter { !it.posterUrl.isNullOrBlank() }
        val pool = if (bannerRandom) withPoster.shuffled()
        else withPoster.sortedByDescending { it.releaseDate ?: "" }
        val slides = pool.take(7)

        val dotRow = binding.dotsHomeBanner
        dotRow.removeAllViews()
        bannerSize = slides.size + 1
        val dots = (0 until bannerSize).map {
            android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                    marginStart = dp(5); marginEnd = dp(5)
                }
                setBackgroundResource(R.drawable.bg_banner_dot)
                alpha = 0.35f
            }
        }
        dots.forEach { dotRow.addView(it) }

        bannerSlides.clear()
        bannerSlides.addAll(slides)
        slides.forEach { m ->
            val slide = android.widget.FrameLayout(this)
            // Fondo: el mismo poster llenando y ligeramente atenuado
            slide.addView(android.widget.ImageView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(this@MainActivity).load(m.posterUrl).centerCrop()
                    .placeholder(R.drawable.bg_tile_glass).into(this)
                setColorFilter(0x66000000.toInt(), android.graphics.PorterDuff.Mode.SRC_OVER)
            })
            // EL CARTEL como tarjeta redondeada con sombra: se ve el poster completo
            val card = androidx.cardview.widget.CardView(this).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(150), dp(216))
                    .apply { gravity = android.view.Gravity.CENTER }
                radius = dp(12).toFloat()
                cardElevation = dp(7).toFloat()
                preventCornerOverlap = false
                useCompatPadding = false
            }
            card.addView(android.widget.ImageView(this).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                Glide.with(this@MainActivity).load(m.posterUrl).centerCrop()
                    .placeholder(R.drawable.bg_tile_glass).into(this)
            })
            slide.addView(card)
            flipper.addView(slide)
        }

        // Ultima diapositiva: publicidad propia de la app
        val promo = android.widget.FrameLayout(this)
        promo.setBackgroundResource(R.drawable.bg_ios_gradient)
        val col = android.widget.LinearLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER
            }
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            addView(android.widget.ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.icon_tvman_play)
                layoutParams = android.widget.LinearLayout.LayoutParams(dp(46), dp(46))
            })
            addView(android.widget.TextView(this@MainActivity).apply {
                text = "TVMAN IPTV"
                setTextColor(0xFFC9A96E.toInt()); textSize = 19f; letterSpacing = 0.18f
                (layoutParams as? android.widget.LinearLayout.LayoutParams)?.topMargin = dp(8)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            })
            addView(android.widget.TextView(this@MainActivity).apply {
                text = "Tu reproductor de listas M3U: películas, series y TV en vivo en un solo lugar"
                setTextColor(0xFFD6DAE4.toInt()); textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(26), dp(6), dp(26), 0)
            })
            addView(android.widget.TextView(this@MainActivity).apply {
                text = "COMPARTIR"
                setTextColor(0xFF0F2144.toInt()); textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_chip_active)
                setPadding(dp(24), dp(8), dp(24), dp(8))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                }
            })
        }
        promo.addView(col)
        flipper.addView(promo)

        if (slides.isNotEmpty()) {
            bannerIdx = 0
            paintBannerDots()
            armBannerLoop()
        }
    }

    /** Salta a la diapositiva [target] (con envoltura) y reinicia el ciclo. */
    private fun showBannerAt(target: Int) {
        if (bannerSize == 0) return
        val newIdx = (target % bannerSize + bannerSize) % bannerSize
        if (newIdx == bannerIdx) return
        val forward = newIdx == (bannerIdx + 1) % bannerSize
        if (forward) binding.flipHomeBanner.showNext() else binding.flipHomeBanner.showPrevious()
        bannerIdx = newIdx
        paintBannerDots()
        armBannerLoop()
    }

    private fun paintBannerDots() {
        (0 until binding.dotsHomeBanner.childCount).forEach { i ->
            binding.dotsHomeBanner.getChildAt(i).alpha = if (i == bannerIdx) 1f else 0.35f
        }
    }

    private fun armBannerLoop() {
        stopHomeBanner()
        val r = object : Runnable {
            override fun run() {
                bannerIdx = (bannerIdx + 1) % bannerSize
                binding.flipHomeBanner.showNext()
                paintBannerDots()
                bannerRunnable = this
                binding.flipHomeBanner.postDelayed(this, 4200)
            }
        }
        bannerRunnable = r
        binding.flipHomeBanner.postDelayed(r, 4200)
    }

    private fun stopHomeBanner() {
        bannerRunnable?.let { binding.flipHomeBanner.removeCallbacks(it) }
        bannerRunnable = null
    }

    private fun resumeHomeBanner() {
        if (bannerRunnable == null && binding.flipHomeBanner.childCount > 1) armBannerLoop()
    }

    /** Publicidad propia de la app: comparte el enlace de la tienda. */
    private fun shareAppPromo() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    android.content.Intent.EXTRA_TEXT,
                    "TVMAN IPTV: reproductor de listas M3U con películas, series y TV en vivo. " +
                        "https://play.google.com/store/apps/details?id=$packageName"
                )
            }
            startActivity(android.content.Intent.createChooser(intent, "Comparte la app"))
        } catch (_: Exception) {}
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnChannelsEmptySettings).setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.navigation_settings
        }
        findViewById<View>(R.id.btnSearchEmptySettings).setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.navigation_settings
        }

        // Search Tab Input Listener
        binding.edtSearchTab.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSearchTabUnified()
                if (s.isNullOrEmpty()) {
                    val current = getSearchHistory(CHANNELS_HISTORY_KEY)
                    if (current.isNotEmpty()) {
                        channelsHistoryAdapter.updateList(current)
                        binding.layoutChannelsSearchHistory.visibility = View.VISIBLE
                    }
                } else {
                    binding.layoutChannelsSearchHistory.visibility = View.GONE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.edtSearchTab.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.edtSearchTab.text.isEmpty()) {
                val current = getSearchHistory(CHANNELS_HISTORY_KEY)
                if (current.isNotEmpty()) {
                    channelsHistoryAdapter.updateList(current)
                    binding.layoutChannelsSearchHistory.visibility = View.VISIBLE
                }
            } else {
                binding.layoutChannelsSearchHistory.visibility = View.GONE
            }
        }

        binding.edtSearchTab.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.edtSearchTab.text.toString().trim()
                if (query.isNotEmpty()) addSearchQuery(CHANNELS_HISTORY_KEY, query)
                binding.layoutChannelsSearchHistory.visibility = View.GONE
                hideKeyboard()
                true
            } else false
        }
        binding.edtSearchTab.showSoftInputOnFocus = false
        binding.tvChannelKeyboard.bindInput(binding.edtSearchTab) {
            val query = binding.edtSearchTab.text.toString().trim()
            if (query.isNotEmpty()) addSearchQuery(CHANNELS_HISTORY_KEY, query)
            binding.layoutChannelsSearchHistory.visibility = View.GONE
        }

        // Settings: cada opción abre su propia pantalla
        binding.btnSettingsIptv.springPress()
        binding.btnSettingsParental.springPress()
        binding.btnSettingsTheme.springPress()
        binding.btnSettingsAccent.springPress()
        binding.btnSettingsHistory.springPress()
        binding.btnSettingsClearCache.springPress()
        binding.btnSettingsStats.springPress()

        binding.btnSettingsIptv.setOnClickListener {
            startActivity(Intent(this, IptvListActivity::class.java))
        }
        binding.btnSettingsParental.setOnClickListener {
            startActivity(Intent(this, ParentalControlActivity::class.java))
        }
        binding.btnSettingsTheme.setOnClickListener {
            startActivity(Intent(this, ThemeActivity::class.java))
        }
        binding.btnSettingsAccent.setOnClickListener {
            startActivity(Intent(this, AccentColorActivity::class.java))
        }
        binding.btnSettingsHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnSettingsClearCache.setOnClickListener {
            startActivity(Intent(this, StorageActivity::class.java))
        }
        binding.btnSettingsStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // Filter button in Channels tab (Opens the requested multi-option menu!)
        binding.btnFilterChannels.setOnClickListener {
            showFilterDialog()
        }

        // Search remains one action away from the channel directory.
        binding.btnChannelSearch.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.navigation_search
        }
        // Filter button in Search tab
        binding.btnSearchFilter.setOnClickListener {
            showSearchFilterDialog()
        }

        // Toggle Channels Search History Click Listener
        binding.btnSearchHistoryTab.setOnClickListener {
            val isVisible = binding.layoutChannelsSearchHistory.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutChannelsSearchHistory.visibility = View.GONE
            } else {
                val current = getSearchHistory(CHANNELS_HISTORY_KEY)
                if (current.isNotEmpty()) {
                    channelsHistoryAdapter.updateList(current)
                    binding.layoutChannelsSearchHistory.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, "El historial de búsqueda está vacío", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Barra de Inicio: abre el BUSCADOR DE CINE (peliculas/series; nada de
        // canales). El reloj abre el historial de lo visto.
        binding.homeSearchBar.setOnClickListener {
            startActivity(android.content.Intent(this, CineSearchActivity::class.java))
        }
        binding.btnWatchHistory.setOnClickListener {
            startActivity(android.content.Intent(this, WatchHistoryActivity::class.java))
        }

        // Secciones de Inicio: ES LA MISMA PANTALLA; al tocar un chip, TODO el
        // Inicio (banner, cuadriculas, sagas, aleatorias) se actualiza a esa
        // seccion. Inicio vuelve al catalogo completo. Sin titulos ni "atras".
        binding.chipHomeInicio.setOnClickListener {
            it.springPress(); setHomeScope("all", "")
        }
        binding.chipHomePeliculas.setOnClickListener {
            it.springPress(); setHomeScope("type", "movie")
        }
        binding.chipHomeSeries.setOnClickListener {
            it.springPress(); setHomeScope("type", "series")
        }
        binding.chipHomeAnimacion.setOnClickListener {
            it.springPress(); setHomeScope("genre", "animación")
        }
        binding.chipHomePlataformas.setOnClickListener {
            it.springPress(); setHomeScope("platform_all", "")
        }
        paintSectionChips()
        startHomeSearchRotator()


        // La búsqueda de películas y series vive en su pantalla independiente.
        binding.btnCineSearchToggle.setOnClickListener {
            startActivity(Intent(this, CineSearchActivity::class.java))
        }
        binding.btnCineSearchToggle.springPress()

        binding.btnCineFilters.setOnClickListener { showCineFilterDialog() }

    }

    /** TV-friendly options dialog: each action is focusable with the remote. */
    private fun showIosOptionPopup(title: String, subtitle: String, options: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_ios)
            setPadding(22.dp(), 22.dp(), 22.dp(), 16.dp())
            minimumWidth = 300.dp()
            clipToOutline = true
        }

        root.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#98989F"))
            textSize = 13f
            setPadding(0, 6.dp(), 0, 16.dp())
        })

        var dialog: AlertDialog? = null
        options.forEachIndexed { index, (iconRes, label) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_tile_glass)
                foreground = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.tv_focus_ring)
                isFocusable = true
                isClickable = true
                setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp() }
                springPress()
            }
            val chip = FrameLayout(this).apply {
                setBackgroundResource(R.drawable.bg_circle_glass)
                layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
            }
            chip.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(22.dp(), 22.dp(), Gravity.CENTER)
            })
            row.addView(chip)
            row.addView(TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 13.dp()
                }
            })
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_ios_arrow_right)
                setColorFilter(Color.parseColor("#98989F"))
                layoutParams = LinearLayout.LayoutParams(18.dp(), 18.dp())
            })
            row.setOnClickListener {
                dialog?.dismiss()
                onPick(index)
            }
            root.addView(row)
        }

        dialog = AlertDialog.Builder(this)
            .setView(root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.let { w ->
            val attrs = w.attributes
            attrs.windowAnimations = R.style.iOSPopupAnimation
            w.attributes = attrs
        }
    }

    private fun enterTvChannelDirectory() {
        if (!::channelsAdapter.isInitialized || !::channelSectionsAdapter.isInitialized) return
        refreshTvChannelDirectory()
        binding.rvChannelCategories.post {
            val position = channelSectionsAdapter.positionOf(channelSectionsAdapter.selectedKey)
            if (position >= 0) {
                binding.rvChannelCategories.scrollToPosition(position)
                binding.rvChannelCategories.post {
                    val selected = binding.rvChannelCategories.findViewHolderForAdapterPosition(position)?.itemView
                    if (selected?.requestFocus() != true) binding.rvChannelCategories.requestFocus()
                }
            }
        }
    }

    private fun refreshTvChannelDirectory() {
        if (!::channelsAdapter.isInitialized || !::channelSectionsAdapter.isInitialized) return
        val favorites = getFavoriteChannels()
        val recents = getRecentChannels()
        val sections = buildList {
            add(TvChannelSection("favorites", "Favoritos", favorites.size))
            add(TvChannelSection("recent", "Vistos recientemente", recents.size))
            add(TvChannelSection("all", "Todos los canales", allChannels.size))
            categories.filter { it.isNotBlank() && !it.equals("Todos", true) }
                .distinct().sortedBy { it.lowercase() }.forEach { group ->
                    add(TvChannelSection("group:$group", group, allChannels.count { it.group == group }))
                }
        }
        val isFirstEntry = !channelSectionInitialized
        channelSectionsAdapter.submitSections(sections, preserveSelection = true)
        if (isFirstEntry) {
            channelSectionsAdapter.selectKey("all")
            channelSectionInitialized = true
        } else if (sections.none { it.key == channelSectionsAdapter.selectedKey }) {
            channelSectionsAdapter.selectKey("all")
        }
        applyChannelDirectoryFilter()
    }

    private fun getFavoriteChannels(): List<Channel> {
        val loadedByUrl = allChannels.associateBy { it.url }
        return FavoritesManager.getAll(this).asSequence()
            .filter { it.isChannel }
            .mapNotNull { item -> loadedByUrl[item.url] ?: item.channel }
            .distinctBy { it.url }
            .toList()
    }

    private fun getRecentChannels(): List<Channel> {
        val loadedByUrl = allChannels.associateBy { it.url }
        return ContinueWatchingManager.getAll(this).asSequence()
            .filter { it.isChannel }
            .mapNotNull { entry -> loadedByUrl[entry.url] }
            .distinctBy { it.url }
            .take(30)
            .toList()
    }

    private fun channelsForSection(key: String): List<Channel> = when {
        key == "favorites" -> getFavoriteChannels()
        key == "recent" -> getRecentChannels()
        key == "all" -> allChannels
        key.startsWith("group:") -> allChannels.filter { it.group == key.removePrefix("group:") }
        else -> allChannels
    }

    private fun onTvChannelSectionSelected(section: TvChannelSection) {
        selectedCategory = if (section.key.startsWith("group:")) section.key.removePrefix("group:") else "Todos"
        applyChannelDirectoryFilter()
    }

    private fun applyChannelDirectoryFilter() {
        if (!::channelsAdapter.isInitialized || !::channelSectionsAdapter.isInitialized) return
        val section = channelSectionsAdapter.selectedKey
        val sectionItems = channelsForSection(section)
        val parentalActive = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
            .getBoolean("parental_active", false)
        var filtered = sectionItems.filter { channel ->
            (selectedCategory == "Todos" || channel.group == selectedCategory) &&
                (selectedCountry == "Todos" || channel.country == selectedCountry) &&
                (selectedLanguage == "Todos" || channel.language == selectedLanguage)
        }
        if (parentalActive) {
            val hiddenCategories = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
                .getStringSet("parental_hidden_categories", emptySet()) ?: emptySet()
            filtered = filtered.filter { !isAdultChannel(it) && (it.group == null || it.group !in hiddenCategories) }
        }
        filtered = when (selectedAlphabet) {
            "A-Z" -> filtered.sortedBy { it.name.lowercase() }
            "Z-A" -> filtered.sortedByDescending { it.name.lowercase() }
            else -> filtered
        }
        channelsAdapter.updateList(filtered)
        binding.rvChannelsGrid.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.tvChannelsNoResults.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        val title = when {
            section == "favorites" -> "Favoritos"
            section == "recent" -> "Vistos recientemente"
            section == "all" -> "Todos los canales"
            section.startsWith("group:") -> section.removePrefix("group:")
            else -> "Canales"
        }
        binding.txtTvChannelListTitle.text = title
        val activeFilters = listOfNotNull(
            selectedCountry.takeUnless { it == "Todos" }?.let { "País: $it" },
            selectedLanguage.takeUnless { it == "Todos" }?.let { "Idioma: $it" },
            selectedAlphabet.takeUnless { it == "Sin Ordenar" }?.let { "Orden: $it" },
            if (parentalActive) "Control parental" else null
        )
        binding.txtSelectedFilterInfo.text = buildString {
            append(if (filtered.size == 1) "1 canal" else "${filtered.size} canales")
            if (activeFilters.isNotEmpty()) append(" · ${activeFilters.joinToString(" · ")}")
        }
        if (filtered.isEmpty()) {
            binding.txtTvChannelsNoResultsTitle.text = when (section) {
                "favorites" -> "Aún no tienes favoritos"
                "recent" -> "Todavía no has visto canales"
                else -> "No hay canales en esta sección"
            }
        }
    }

    private fun showFilterDialog() {
        showIosOptionPopup(
            "Filtrar y ordenar",
            "Elige una opción con la cruceta y confirma con OK.",
            listOf(
                R.drawable.ic_ios_tv to "Grupo",
                R.drawable.ic_ios_globe to "País",
                R.drawable.ic_ios_message to "Idioma"
            )
        ) { which ->
            when (which) {
                0 -> showCategoryFilterSelector()
                1 -> showCountryFilterSelector()
                2 -> showLanguageFilterSelector()
            }
        }
    }

    private fun showCountryFilterSelector() {
        if (countries.isEmpty()) {
            Toast.makeText(this, "No hay países cargados.", Toast.LENGTH_SHORT).show()
            return
        }

        val listWithoutTodos = countries.filter { it != "Todos" }
        
        var dialog: AlertDialog? = null
        val recyclerView = androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = CountryDialogAdapter(listWithoutTodos) { selected ->
                selectedCountry = selected
                dialog?.dismiss()
                applyFiltersAndSorting()
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Seleccionar País 🗺️")
            .setView(recyclerView)
            .setNeutralButton("Mostrar Todos 🔄") { _, _ ->
                selectedCountry = "Todos"
                applyFiltersAndSorting()
            }
            .setNegativeButton("Cerrar", null)
            .create()

        dialog.show()
    }

    private fun showLanguageFilterSelector() {
        if (languages.isEmpty()) {
            Toast.makeText(this, "No hay idiomas detectados.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = languages.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Idioma")
            .setItems(items) { _, which ->
                selectedLanguage = items[which]
                applyFiltersAndSorting()
            }
            .show()
    }

    private fun showCategoryFilterSelector() {
        if (categories.isEmpty()) {
            Toast.makeText(this, "No hay categorías cargadas.", Toast.LENGTH_SHORT).show()
            return
        }

        val items = categories.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Categoría")
            .setItems(items) { _, which ->
                selectedCategory = items[which]
                if (selectedCategory == "Todos" && ::channelSectionsAdapter.isInitialized) {
                    channelSectionsAdapter.selectKey("all")
                }
                applyFiltersAndSorting()
            }
            .show()
    }

    private fun showAlphabetSortSelector() {
        val options = arrayOf("Sin Ordenar 🔄", "A-Z (Ascendente) 🔼", "Z-A (Descendente) 🔽")
        AlertDialog.Builder(this)
            .setTitle("Ordenar por Alfabeto")
            .setItems(options) { _, which ->
                selectedAlphabet = when (which) {
                    1 -> "A-Z"
                    2 -> "Z-A"
                    else -> "Sin Ordenar"
                }
                applyFiltersAndSorting()
            }
            .show()
    }

    private fun resetAllFilters() {
        selectedCategory = "Todos"
        selectedCountry = "Todos"
        selectedLanguage = "Todos"
        selectedAlphabet = "Sin Ordenar"
        applyFiltersAndSorting()
        Toast.makeText(this, "Filtros restablecidos", Toast.LENGTH_SHORT).show()
    }

    private fun applyFiltersAndSorting() {
        refreshHomeSections()
        if (selectedCategory != "Todos") {
            val groupKey = "group:$selectedCategory"
            if (::channelSectionsAdapter.isInitialized && channelSectionsAdapter.positionOf(groupKey) >= 0) {
                channelSectionsAdapter.selectKey(groupKey)
            }
        }
        refreshTvChannelDirectory()
    }

    /** Parental control: select whole channel categories to hide when active. */
    private fun showSearchFilterDialog() {
        showIosOptionPopup(
            "Filtros de canales",
            "Elige una opción con la cruceta y confirma con OK.",
            listOf(
                R.drawable.ic_ios_tv to "Grupo o categoría",
                R.drawable.ic_ios_globe to "País",
                R.drawable.ic_ios_message to "Idioma",
                R.drawable.ic_ios_refresh to "Restablecer filtros"
            )
        ) { which ->
            when (which) {
                0 -> showSearchGroupFilterSelector()
                1 -> showSearchCountryFilterSelector()
                2 -> showSearchLanguageFilterSelector()
                3 -> {
                    selectedSearchGroup = "Todos"
                    selectedSearchCountry = "Todos"
                    selectedSearchLanguage = "Todos"
                    filterSearchTabUnified()
                }
            }
        }
    }

    private fun showSearchGroupFilterSelector() {
        val options = categories.filter { it.isNotBlank() }.distinct().toTypedArray()
        if (options.isEmpty()) {
            Toast.makeText(this, "No hay grupos de canales cargados.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Grupo de canales")
            .setSingleChoiceItems(options, options.indexOf(selectedSearchGroup).coerceAtLeast(0)) { dialog, which ->
                selectedSearchGroup = options[which]
                dialog.dismiss()
                filterSearchTabUnified()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSearchLanguageFilterSelector() {
        val options = languages.filter { it.isNotBlank() }.distinct().toTypedArray()
        if (options.isEmpty()) {
            Toast.makeText(this, "No hay idiomas cargados.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Idioma del canal")
            .setSingleChoiceItems(options, options.indexOf(selectedSearchLanguage).coerceAtLeast(0)) { dialog, which ->
                selectedSearchLanguage = options[which]
                dialog.dismiss()
                filterSearchTabUnified()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSearchCountryFilterSelector() {
        if (countries.isEmpty()) {
            Toast.makeText(this, "No hay países cargados.", Toast.LENGTH_SHORT).show()
            return
        }

        val listWithoutTodos = countries.filter { it != "Todos" }
        
        var dialog: AlertDialog? = null
        val recyclerView = androidx.recyclerview.widget.RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = CountryDialogAdapter(listWithoutTodos) { selected ->
                selectedSearchCountry = selected
                dialog?.dismiss()
                filterSearchTabUnified()
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Filtrar Búsqueda por País 🗺️")
            .setView(recyclerView)
            .setNeutralButton("Mostrar Todos 🔄") { _, _ ->
                selectedSearchCountry = "Todos"
                filterSearchTabUnified()
            }
            .setNegativeButton("Cerrar", null)
            .create()

        dialog.show()
    }

    private fun getFileExtension(url: String): String {
        return try {
            val cleanUrl = url.lowercase().split("?")[0]
            val lastDot = cleanUrl.lastIndexOf('.')
            if (lastDot != -1) {
                cleanUrl.substring(lastDot + 1).uppercase()
            } else {
                "STREAM"
            }
        } catch (e: Exception) {
            "VIDEO"
        }
    }

    private fun getDomainName(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return ""
            host.replace("www.", "")
        } catch (e: Exception) {
            ""
        }
    }

    private fun restoreSavedPlaylist() {
        val sharedPref = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
        val lastUrl = sharedPref.getString("last_url", null)
        if (!lastUrl.isNullOrEmpty()) {
            // Premium Instant Loading Cache Optimization!
            val cacheFile = java.io.File(filesDir, "cached_playlist.m3u")
            if (cacheFile.exists()) {
                lifecycleScope.launch {
                    binding.globalProgressBar.visibility = View.VISIBLE
                    val cachedChannels = withContext(Dispatchers.IO) {
                        try {
                            cacheFile.inputStream().use { M3UParser.parse(it) }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    binding.globalProgressBar.visibility = View.GONE
                    if (!cachedChannels.isNullOrEmpty()) {
                        allChannels = cachedChannels
                        
                        // Parse categories, countries and languages instantly
                        val parsedCategories = mutableListOf("Todos")
                        val extractedGroups = allChannels.mapNotNull { it.group }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                        parsedCategories.addAll(extractedGroups)
                        categories = parsedCategories

                        val parsedCountries = mutableListOf("Todos")
                        val extractedCountries = allChannels.mapNotNull { it.country }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                        parsedCountries.addAll(extractedCountries)
                        countries = parsedCountries

                        val parsedLanguages = mutableListOf("Todos")
                        val extractedLanguages = allChannels.mapNotNull { it.language }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                        parsedLanguages.addAll(extractedLanguages)
                        languages = parsedLanguages

                        selectedCategory = "Todos"
                        selectedCountry = "Todos"
                        selectedLanguage = "Todos"
                        selectedSearchCountry = "Todos"
                        selectedSearchGroup = "Todos"
                        selectedSearchLanguage = "Todos"
                        selectedAlphabet = "Sin Ordenar"
                        
                        channelSectionInitialized = false
                        refreshTvChannelDirectory()
                        searchAdapter.updateList(allChannels)
                        binding.edtSearchTab.setText("")
                        binding.txtSearchCount.text = "Ingresa el nombre de un canal para buscar entre los ${allChannels.size} cargados"
                        
                        updateEmptyStates()
                    } else {
                        loadIptvList(lastUrl, isAutoRestore = true)
                    }
                }
            } else {
                loadIptvList(lastUrl, isAutoRestore = true)
            }
        }
    }


    /** Vacía la lista cargada (lo usa la pantalla de Almacenamiento / Limpiar). */
    private fun clearPlaylist() {
        val sharedPref = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
        sharedPref.edit().remove("last_url").apply()
        allChannels = emptyList()
        categories = emptyList()
        countries = emptyList()
        languages = emptyList()
        channelSectionInitialized = false
        refreshTvChannelDirectory()
        updateEmptyStates()
        Toast.makeText(this, "Lista eliminada del historial", Toast.LENGTH_SHORT).show()
    }

    private fun loadIptvList(urlString: String, isAutoRestore: Boolean = false) {
        hideKeyboard()

        // Show global progress
        binding.globalProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchAndParseM3u(urlString)
            }

            binding.globalProgressBar.visibility = View.GONE

            if (result != null) {
                allChannels = result
                
                if (allChannels.isNotEmpty()) {
                    // Save URL to persistent storage since it loaded successfully!
                    val sharedPref = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("last_url", urlString).apply()

                    // Add to URL load history!
                    addSearchQuery(PLAYLIST_HISTORY_KEY, urlString)

                    // 1. Parse and extract unique categories from list
                    val parsedCategories = mutableListOf("Todos")
                    val extractedGroups = allChannels.mapNotNull { it.group }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                    parsedCategories.addAll(extractedGroups)
                    categories = parsedCategories

                    // Guardar categorías para la pantalla de control parental.
                    sharedPref.edit().putStringSet("categories_list", categories.toSet()).apply()

                    // 2. Parse and extract unique countries from list!
                    val parsedCountries = mutableListOf("Todos")
                    val extractedCountries = allChannels.mapNotNull { it.country }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                    parsedCountries.addAll(extractedCountries)
                    countries = parsedCountries

                    // 3. Parse and extract unique languages from list!
                    val parsedLanguages = mutableListOf("Todos")
                    val extractedLanguages = allChannels.mapNotNull { it.language }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                    parsedLanguages.addAll(extractedLanguages)
                    languages = parsedLanguages

                    // 4. Reset variables
                    selectedCategory = "Todos"
                    selectedCountry = "Todos"
                    selectedLanguage = "Todos"
                    selectedSearchCountry = "Todos"
                    selectedSearchGroup = "Todos"
                    selectedSearchLanguage = "Todos"
                    selectedAlphabet = "Sin Ordenar"
                    
                    // Refresh the TV directory and the separate search grid.
                    channelSectionInitialized = false
                    refreshTvChannelDirectory()
                    searchAdapter.updateList(allChannels)

                    // Clear search input on tab 3
                    binding.edtSearchTab.setText("")
                    binding.txtSearchCount.text = "Ingresa el nombre de un canal para buscar entre los ${allChannels.size} cargados"

                    // 6. Update empty state visibility structures
                    updateEmptyStates()

                    if (!isAutoRestore) {
                        Toast.makeText(this@MainActivity, "¡Lista cargada con éxito!", Toast.LENGTH_SHORT).show()
                    }

                    // 7. AUTO-SWITCH tab to "CANALES" so the user immediately sees the grid
                    binding.bottomNavigation.selectedItemId = R.id.navigation_channels
                } else {
                    allChannels = emptyList()
                    categories = emptyList()
                    countries = emptyList()
                    languages = emptyList()
                    updateEmptyStates()
                    Toast.makeText(this@MainActivity, "La lista M3U está vacía", Toast.LENGTH_LONG).show()
                }
            } else {
                updateEmptyStates()
                if (!isAutoRestore) {
                    Toast.makeText(this@MainActivity, "Error al descargar o procesar la lista. Revisa el enlace.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchAndParseM3u(urlString: String): List<Channel>? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 20000
            connection.requestMethod = "GET"
            connection.doInput = true
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                // High-performance streaming byte copy to save cache locally
                val bytes = connection.inputStream.readBytes()
                try {
                    val cacheFile = java.io.File(filesDir, "cached_playlist.m3u")
                    cacheFile.writeBytes(bytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val byteArrayInputStream = java.io.ByteArrayInputStream(bytes)
                val parsed = M3UParser.parse(byteArrayInputStream)
                parsed
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun filterChannelsByCategoryAndCountry() {
        if (selectedCategory != "Todos") {
            val groupKey = "group:$selectedCategory"
            if (::channelSectionsAdapter.isInitialized && channelSectionsAdapter.positionOf(groupKey) >= 0) {
                channelSectionsAdapter.selectKey(groupKey)
            }
        }
        refreshTvChannelDirectory()
    }

    private fun onCategorySelected(category: String) {
        selectedCategory = category
        filterChannelsByCategoryAndCountry()
    }

    private fun onCountrySelected(country: String) {
        selectedCountry = country
        filterChannelsByCategoryAndCountry()
    }

    private fun filterSearchTabUnified() {
        val sharedPref = getSharedPreferences("iptv_pref", Context.MODE_PRIVATE)
        val isParentalActive = sharedPref.getBoolean("parental_active", false)

        val query = binding.edtSearchTab.text.toString().trim()
        var filtered = allChannels.filter {
            val matchesQuery = query.isEmpty() || it.name.contains(query, ignoreCase = true) || (it.group != null && it.group.contains(query, ignoreCase = true))
            val matchesGroup = selectedSearchGroup == "Todos" || it.group == selectedSearchGroup
            val matchesCountry = selectedSearchCountry == "Todos" || it.country == selectedSearchCountry
            val matchesLanguage = selectedSearchLanguage == "Todos" || it.language == selectedSearchLanguage
            matchesQuery && matchesGroup && matchesCountry && matchesLanguage
        }

        if (isParentalActive) {
            filtered = filtered.filter { !isAdultChannel(it) }
        }

        searchAdapter.updateList(filtered)
        val activeSearchFilters = listOfNotNull(
            selectedSearchGroup.takeUnless { it == "Todos" },
            selectedSearchCountry.takeUnless { it == "Todos" },
            selectedSearchLanguage.takeUnless { it == "Todos" }
        )
        binding.txtSearchCount.text = when {
            query.isEmpty() && activeSearchFilters.isEmpty() -> "${allChannels.size} canales · escribe para filtrar"
            activeSearchFilters.isEmpty() -> "${filtered.size} canales encontrados"
            else -> "${filtered.size} resultados · ${activeSearchFilters.joinToString(" · ")}"
        }
        
    }

    private fun onSearchCountrySelected(country: String) {
        selectedSearchCountry = country
        filterSearchTabUnified()
    }

    private fun updateEmptyStates() {
        val hasList = allChannels.isNotEmpty()

        if (hasList) {
            // TAB 2 (CANALES): Hide empty message, show channels list & categories
            binding.layoutChannelsEmpty.visibility = View.GONE
            binding.layoutChannelsContent.visibility = View.VISIBLE

            // TAB 3 (BUSCADOR): Hide empty message, show search input & grid
            binding.layoutSearchEmpty.visibility = View.GONE
            binding.layoutSearchContent.visibility = View.VISIBLE
        } else {
            // TAB 2 (CANALES): Show empty instructions message
            binding.layoutChannelsEmpty.visibility = View.VISIBLE
            binding.layoutChannelsContent.visibility = View.GONE

            // TAB 3 (BUSCADOR): Show empty instructions message
            binding.layoutSearchEmpty.visibility = View.VISIBLE
            binding.layoutSearchContent.visibility = View.GONE
        }
    }

    private fun openPlayer(channel: Channel) {
        val visibleQueue = if (::channelsAdapter.isInitialized) channelsAdapter.channelsSnapshot() else emptyList()
        val zapQueue = when {
            visibleQueue.any { it.url == channel.url } -> visibleQueue
            allChannels.any { it.url == channel.url } -> allChannels
            else -> listOf(channel)
        }
        LiveChannelSession.setQueue(zapQueue, channel.url)
        launchPlayerActivity(channel)
    }

    private fun launchPlayerActivity(channel: Channel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channelName", channel.name)
            putExtra("channelUrl", channel.url)
            putExtra("isLiveTv", true) // Mark as Live TV channel!
            putExtra("channelLogo", channel.logoUrl)
        }
        startActivity(intent)
    }

    private fun openPlayerWithSources(selectedUrl: String, allSources: ArrayList<String>) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channelName", "Video Web: " + getDomainName(selectedUrl))
            putExtra("channelUrl", selectedUrl)
            putStringArrayListExtra("allSources", allSources)
            putExtra("isLiveTv", true) // External web player option acts as live or regular stream depending on origin
        }
        startActivity(intent)
    }

    private fun isAdultChannel(channel: Channel): Boolean {
        val keywords = listOf("xxx", "18+", "adult", "porn", "erotic", "erotica", "sensual", "forbiden", "prohibido", "hot", "sex", "redlight", "playboy")
        val name = channel.name.lowercase()
        val group = channel.group?.lowercase() ?: ""
        return keywords.any { name.contains(it) || group.contains(it) }
    }

    private var pendingDeepLink: String? = null

    private fun loadCineCatalog() {
        lifecycleScope.launch {
            // FAST PASS: catalogo empaquetado sin red -> contenido visible YA.
            // El loading verde SOLO aparece si no hay nada que mostrar todavia.
            val quick = withContext(Dispatchers.IO) {
                CineRepository.getBundledQuickCatalog(this@MainActivity)
            }
            if (allCineMedia.isEmpty() && quick.isNotEmpty()) {
                allCineMedia = quick
                withContext(Dispatchers.Default) {
                    quick.forEach { TasteProfile.genreKeysOf(it) }
                }
                renderCineGenres()
                quick.forEach { CineRepository.applyPosterCache(it) }
                applyCineFilters()
                refreshHomeSections()
            }

            val catalog = CineRepository.getCineCatalog(this@MainActivity)
            allCineMedia = catalog
            renderCineGenres()
            // Pre-calienta el cache de generos (fuera del hilo de UI) ANTES de
            // armar secciones: asi ni el primer toque en PELICULAS/SERIES congela.
            withContext(Dispatchers.Default) {
                try { catalog.forEach { TasteProfile.genreKeysOf(it) } } catch (_: Exception) {}
            }
            catalog.forEach { CineRepository.applyPosterCache(it) }
            // HORNO: completa los posters de TODO el catalogo con TMDB (2do
            // plano, ritmo seguro) y los persiste para las proximas sesiones
            // Orden del horno: los MEJOR calificados primero (los visibles
            // arriba reciben su poster en los primeros minutos)
            CineRepository.startPosterBaker(
                this@MainActivity,
                catalog.sortedByDescending { it.rating ?: -1.0 },
                lifecycleScope
            ) {
                // Posters nuevos listos: repinta Inicio y el grid de Cine
                refreshHomeSections()
                if (::cineAdapter.isInitialized) cineAdapter.notifyDataSetChanged()
            }
            refreshHomeSections()
            consumePendingDeepLink()
            
            applyCineFilters()
        }
    }

    private val cineGenreOptions = listOf(
        "Acción" to "acción",
        "Comedia" to "comedia",
        "Drama" to "drama",
        "Terror" to "terror",
        "Ciencia ficción" to "ciencia ficción",
        "Animación" to "animación",
        "Documentales" to "documental"
    )

    private fun renderCineGenres() {
        binding.cineGenreRail.removeAllViews()
        val source = allCineMedia.filter { media ->
            selectedCineType == "all" || media.type == selectedCineType
        }
        fun addGenre(label: String, key: String?, count: Int) {
            val row = android.widget.TextView(this).apply {
                text = "$label ($count)"
                textSize = 14f
                maxLines = 1
                setPadding(dp(12), dp(10), dp(8), dp(10))
                isFocusable = true
                isClickable = true
                contentDescription = "$label, $count títulos"
                setBackgroundResource(
                    if (selectedCineGenre == key) R.drawable.bg_tv_sidebar_item else android.R.color.transparent
                )
                setTextColor(android.graphics.Color.parseColor(
                    if (selectedCineGenre == key) "#FFFFFFFF" else "#FFB7C0CE"
                ))
                setOnClickListener {
                    selectedCineGenre = key
                    renderCineGenres()
                    applyCineFilters()
                }
            }
            binding.cineGenreRail.addView(row, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
            ))
        }
        addGenre("Todas", null, source.size)
        cineGenreOptions.forEach { (label, key) ->
            val count = source.count { media -> TasteProfile.genreKeysOf(media).contains(key) }
            if (count > 0) addGenre(label, key, count)
        }
    }

    private fun applyCineFilters() {
        val type = selectedCineType
        val genre = selectedCineGenre
        val year = selectedCineYear
        cineFilterJob?.cancel()
        cineFilterJob = lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                allCineMedia.asSequence()
                    .filter { type == "all" || it.type == type }
                    .filter { genre == null || TasteProfile.genreKeysOf(it).contains(genre) }
                    .filter { year == null || it.releaseDate?.take(4) == year }
                    .sortedByDescending { it.rating ?: -1.0 }
                    .toList()
            }
            cinePagingFull = filtered
            cineShown = minOf(CINE_PAGE, cinePagingFull.size)
            cineLoadingMore = false
            cineAdapter.updateList(cinePagingFull.take(cineShown))
            binding.txtCineCount.text = "Total: ${cinePagingFull.size}"
            binding.layoutCineLoading.visibility = View.GONE
            binding.rvCineGrid.visibility = View.VISIBLE
        }
    }

    private fun updateCineFilterButtons() {
        binding.txtCineCatalogTitle.text = when (selectedCineType) {
            "movie" -> "Películas"
            "series" -> "Series"
            else -> "Películas y series"
        }
    }

    private fun showCineFilterDialog() {
        val options = arrayOf("Tipo", "Género", "Año", "Restablecer filtros")
        AlertDialog.Builder(this)
            .setTitle("Filtros del catálogo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCineTypeSelector()
                    1 -> showCineGenreSelector()
                    2 -> showCineYearSelector()
                    3 -> {
                        selectedCineType = "all"
                        selectedCineGenre = null
                        selectedCineYear = null
                        updateCineFilterButtons()
                        renderCineGenres()
                        applyCineFilters()
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showCineTypeSelector() {
        val labels = arrayOf("Todas", "Películas", "Series")
        val values = arrayOf("all", "movie", "series")
        val selected = values.indexOf(selectedCineType).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Tipo")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                selectedCineType = values[which]
                dialog.dismiss()
                updateCineFilterButtons()
                renderCineGenres()
                applyCineFilters()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showCineGenreSelector() {
        val labels = arrayOf("Todos") + cineGenreOptions.map { it.first }
        val keys = listOf<String?>(null) + cineGenreOptions.map { it.second }
        val selected = keys.indexOf(selectedCineGenre).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Género")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                selectedCineGenre = keys[which]
                dialog.dismiss()
                renderCineGenres()
                applyCineFilters()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showCineYearSelector() {
        val years = allCineMedia.mapNotNull { it.releaseDate?.take(4) }
            .filter { it.length == 4 && it.all(Char::isDigit) }
            .distinct().sortedDescending()
        val labels = arrayOf("Todos") + years.toTypedArray()
        val values = listOf<String?>(null) + years.map { it }
        val selected = values.indexOf(selectedCineYear).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Año")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                selectedCineYear = values[which]
                dialog.dismiss()
                applyCineFilters()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** Abre la ficha del titulo compartido (deep link) una vez que el
     *  catalogo esta en memoria; luego limpia el intent para que el
     *  deep link no se re-dispare al rotar o volver del background. */
    private fun consumePendingDeepLink() {
        val t = pendingDeepLink ?: return
        pendingDeepLink = null
        try {
            intent.removeExtra("deeplink_title")
            intent.data = null
        } catch (_: Exception) {}
        if (t.isBlank()) return
        val media = allCineMedia.firstOrNull {
            it.title.equals(t, ignoreCase = true) || it.searchTitle.equals(t, ignoreCase = true)
        } ?: run {
            // Busqueda tolerante: contiene
            allCineMedia.firstOrNull {
                it.title.contains(t, ignoreCase = true) || it.searchTitle.contains(t, ignoreCase = true)
            }
        }
        try { binding.bottomNavigation.selectedItemId = R.id.navigation_cine } catch (_: Exception) {}
        if (media == null) {
            Toast.makeText(this, "No se encontró el título compartido", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dest = if (media.type == "movie") CineMovieDetailActivity::class.java
                       else CineTvShowDetailActivity::class.java
            startActivity(android.content.Intent(this, dest).apply { putExtra("media", media) })
        } catch (_: Exception) {}
    }

    private fun updateTVMANNav(activeId: Int) {
        val selectedSidebarId = when (activeId) {
            R.id.navigation_home -> R.id.sideNavHome
            R.id.navigation_channels, R.id.navigation_search -> R.id.sideNavChannels
            R.id.navigation_cine -> when (selectedCineType) {
                "series" -> R.id.sideNavSeries
                else -> R.id.sideNavMovies
            }
            R.id.navigation_settings -> R.id.sideNavSettings
            else -> View.NO_ID
        }
        listOf(
            R.id.sideNavHome, R.id.sideNavChannels, R.id.sideNavMovies,
            R.id.sideNavSeries, R.id.sideNavMyList, R.id.sideNavSettings
        ).forEach { id ->
            findViewById<View>(id)?.isSelected = id == selectedSidebarId
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun openCineList(
        title: String,
        kind: String,
        param: String = "",
        badges: Boolean = false,
        scope: Pair<String, String> = "all" to ""
    ) {
        startActivity(Intent(this, CinePopularAllActivity::class.java).apply {
            putExtra("title", title)
            putExtra("kind", kind)
            putExtra("param", param)
            putExtra("show_platform_badges", badges)
            putExtra("scope_kind", scope.first)
            putExtra("scope_param", scope.second)
        })
    }

        private fun openCineDetail(media: CineMedia) {
        val intent = Intent(this, if (media.type == "movie") CineMovieDetailActivity::class.java else CineTvShowDetailActivity::class.java).apply {
            putExtra("media", media)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.ps_enter, R.anim.ps_exit)
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private var homeRotatorRunnable: Runnable? = null

    private fun startHomeSearchRotator() {
        homeRotatorRunnable?.let { binding.homeSearchRotator.removeCallbacks(it) }
        homeRotatorRunnable = null
        binding.homeSearchRotator.setText("Buscar películas y series")
    }

    private fun getSearchHistory(key: String): MutableList<String> {
        val prefs = getSharedPreferences("IPTV_PREFS", Context.MODE_PRIVATE)
        val historyStr = prefs.getString(key, "") ?: ""
        if (historyStr.isEmpty()) return mutableListOf()
        return historyStr.split("|||").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveSearchHistory(key: String, list: List<String>) {
        val prefs = getSharedPreferences("IPTV_PREFS", Context.MODE_PRIVATE)
        val historyStr = list.joinToString("|||")
        prefs.edit().putString(key, historyStr).apply()
    }

    private fun addSearchQuery(key: String, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getSearchHistory(key)
        current.remove(trimmed) // Remove duplicates
        current.add(0, trimmed) // Add to top
        if (current.size > 10) { // Keep last 10 recent searches
            current.removeAt(current.size - 1)
        }
        saveSearchHistory(key, current)
    }

    private lateinit var channelsHistoryAdapter: SearchHistoryAdapter
    
    private val CHANNELS_HISTORY_KEY = "CHANNELS_SEARCH_HISTORY"
    private val PLAYLIST_HISTORY_KEY = "PLAYLIST_URL_HISTORY"

    private fun setupSearchHistories() {
        // Channels search history
        binding.rvChannelsSearchHistory.layoutManager = LinearLayoutManager(this)
        channelsHistoryAdapter = SearchHistoryAdapter(getSearchHistory(CHANNELS_HISTORY_KEY),
            onItemClick = { query ->
                binding.edtSearchTab.setText(query)
                performChannelsSearch(query)
                binding.layoutChannelsSearchHistory.visibility = View.GONE
            },
            onDeleteClick = { query ->
                val current = getSearchHistory(CHANNELS_HISTORY_KEY)
                current.remove(query)
                saveSearchHistory(CHANNELS_HISTORY_KEY, current)
                channelsHistoryAdapter.updateList(current)
                if (current.isEmpty()) {
                    binding.layoutChannelsSearchHistory.visibility = View.GONE
                }
            }
        )
        binding.rvChannelsSearchHistory.adapter = channelsHistoryAdapter

        binding.btnChannelsClearAll.setOnClickListener {
            saveSearchHistory(CHANNELS_HISTORY_KEY, emptyList())
            channelsHistoryAdapter.updateList(emptyList())
            binding.layoutChannelsSearchHistory.visibility = View.GONE
        }

    }

    private fun performChannelsSearch(query: String) {
        binding.edtSearchTab.setText(query)
        filterSearchTabUnified()
        addSearchQuery(CHANNELS_HISTORY_KEY, query)
    }

    private fun applyAppTheme() {
        // The dark cinematic palette is shared by Home, Channels, Search, Cine and Settings.
        binding.root.post { updateAppThemeColors(isDark = true) }
    }

    private fun updateAppThemeColors(isDark: Boolean) {
        val bgColor = if (isDark) androidx.core.content.ContextCompat.getColor(this, R.color.tv_background) else android.graphics.Color.parseColor("#F1F3F7")
        val cardColor = if (isDark) androidx.core.content.ContextCompat.getColor(this, R.color.tv_surface) else android.graphics.Color.WHITE
        val textColor = if (isDark) androidx.core.content.ContextCompat.getColor(this, R.color.tv_text_primary) else android.graphics.Color.BLACK
        
        // 1. Update window background
        window.decorView.setBackgroundColor(bgColor)
        
        // 2. Update main tab containers backgrounds
        binding.containerHome.setBackgroundColor(bgColor)
        binding.containerChannels.setBackgroundColor(bgColor)
        binding.containerCine.setBackgroundColor(bgColor)
        binding.containerBrowser.setBackgroundColor(bgColor)
        binding.containerSearch.setBackgroundColor(bgColor)
        
        // 3. Update bottom navigation bar (both background, text tint, and dynamic icon tint)
        binding.bottomNavigation.setBackgroundColor(cardColor)
        binding.bottomNavigation.itemTextColor = android.content.res.ColorStateList.valueOf(textColor)
        
        // Dynamic bottom icon tint: White in dark mode, Black in light mode
        val iconColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        binding.bottomNavigation.itemIconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        
        // 4. Update recursively all subviews (CardViews, TextViews, and EditTexts)
        updateSubviewsColorRecursive(binding.root, isDark)
    }

    private fun updateSubviewsColorRecursive(view: android.view.View, isDark: Boolean) {
        val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val cardColor = if (isDark) android.graphics.Color.parseColor("#14294F") else android.graphics.Color.parseColor("#FFFFFF")
        
        if (view is android.widget.EditText) {
            // EditText extends TextView, so handle it first; keep search/input fields dark too.
            view.setTextColor(textColor)
            view.setHintTextColor(if (isDark) android.graphics.Color.parseColor("#96A4B8") else android.graphics.Color.parseColor("#7D8797"))
            val transparentBackground = (view.background as? android.graphics.drawable.ColorDrawable)?.color == android.graphics.Color.TRANSPARENT
            if (isDark && !transparentBackground) {
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_settings_card)?.let { view.background = it }
            } else if (!isDark) {
                view.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            }
        } else if (view is androidx.cardview.widget.CardView) {
            view.setCardBackgroundColor(cardColor)
        } else if (view is android.widget.TextView) {
            // Keep explicit accent and dark-on-lavender action labels intact; lift default dark text.
            val current = view.currentTextColor
            val defaultDarkText = current == android.graphics.Color.BLACK ||
                current == android.graphics.Color.parseColor("#FF212121")
            val hasDrawableSurface = view.background != null && view.background !is android.graphics.drawable.ColorDrawable
            if (isDark && defaultDarkText && !hasDrawableSurface) {
                view.setTextColor(textColor)
            } else if (!isDark && defaultDarkText) {
                view.setTextColor(textColor)
            }
        }
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                updateSubviewsColorRecursive(view.getChildAt(i), isDark)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        homeRotatorRunnable?.let { binding.homeSearchRotator.removeCallbacks(it) }
        stopHomeBanner()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

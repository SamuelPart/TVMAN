package com.tvman.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TVManApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Keep playlist/scraper and catalog refresh workflows from the IPTV source branch.
        ScraperConfig.loadCached(this)
        CoroutineScope(Dispatchers.IO).launch { ScraperConfig.refresh(this@TVManApplication) }
        CoroutineScope(Dispatchers.IO).launch { CineRepository.prefetchCatalog(this@TVManApplication) }
        CatalogBot.start(this)
    }
}

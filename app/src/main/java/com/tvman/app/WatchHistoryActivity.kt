package com.tvman.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvman.app.databinding.ActivityWatchHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screen 11: continuation of films/series and the recent live-channel history. */
class WatchHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWatchHistoryBinding
    private lateinit var mediaAdapter: WatchHistoryAdapter
    private lateinit var channelAdapter: ChannelAdapter
    private var channelEntries: List<ContinueWatchingManager.ResumeEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnWatchHistBack.setOnClickListener { finish() }
        binding.btnWatchHistEdit.text = "Limpiar historial"
        binding.btnWatchHistEdit.setOnClickListener { confirmClearHistory() }

        mediaAdapter = WatchHistoryAdapter { entry -> openMedia(entry) }
        binding.rvWatchHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvWatchHistory.adapter = mediaAdapter

        channelAdapter = ChannelAdapter(
            emptyList(),
            onChannelClick = { channel ->
                channelEntries.firstOrNull { it.url == channel.url }?.let { entry ->
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra("channelName", entry.title)
                        putExtra("channelUrl", entry.url)
                        putExtra("channelLogo", entry.channelLogo)
                        putExtra("isLiveTv", true)
                    })
                }
            }
        )
        binding.rvChannelHistory.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvChannelHistory.adapter = channelAdapter

    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun openMedia(entry: ContinueWatchingManager.ResumeEntry) {
        val media = entry.media
        val direct = CineRepository.isDirectStreamUrl(entry.url)
        val target = if (direct) PlayerActivity::class.java else WebVideoPlayerActivity::class.java
        startActivity(Intent(this, target).apply {
            putExtra("channelName", entry.title)
            putExtra("channelUrl", entry.url)
            putExtra("startPosition", entry.positionMs)
            putExtra("cineMedia", media)
            media?.let { putExtra("media", it) }
        })
    }

    private fun reload() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                ContinueWatchingManager.getAll(this@WatchHistoryActivity)
            }
            val posters = withContext(Dispatchers.IO) {
                CineRepository.getCineCatalog(this@WatchHistoryActivity)
                    .associate { it.title.lowercase().trim() to (it.posterUrl ?: it.rawLogo) }
            }
            val media = entries.filterNot { it.isChannel }
            channelEntries = entries.filter { it.isChannel }
            mediaAdapter.submit(media, posters)
            channelAdapter.updateList(channelEntries.map {
                Channel(name = it.title, url = it.url, logoUrl = it.channelLogo)
            })

            binding.txtContinueEmpty.visibility = if (media.isEmpty()) View.VISIBLE else View.GONE
            binding.txtChannelHistoryEmpty.visibility = if (channelEntries.isEmpty()) View.VISIBLE else View.GONE
            binding.rvWatchHistory.visibility = if (media.isEmpty()) View.GONE else View.VISIBLE
            binding.rvChannelHistory.visibility = if (channelEntries.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("¿Limpiar historial?")
            .setMessage("Se eliminarán las posiciones guardadas de películas, series y canales.")
            .setPositiveButton("Limpiar") { _, _ ->
                ContinueWatchingManager.clear(this)
                reload()
                Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

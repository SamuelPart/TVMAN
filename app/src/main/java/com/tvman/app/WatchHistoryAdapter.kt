package com.tvman.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/** Poster cards for the continue-watching row in the approved history screen. */
class WatchHistoryAdapter(
    private val onItemClick: (ContinueWatchingManager.ResumeEntry) -> Unit
) : RecyclerView.Adapter<WatchHistoryAdapter.Holder>() {

    private val items = mutableListOf<ContinueWatchingManager.ResumeEntry>()
    private val posterByKey = mutableMapOf<String, String>()

    fun submit(list: List<ContinueWatchingManager.ResumeEntry>, posters: Map<String, String>) {
        items.clear()
        items.addAll(list)
        posterByKey.clear()
        posterByKey.putAll(posters)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_watch_history, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.txtTitle.text = entry.title
        val percent = if (entry.durationMs > 0) {
            ((entry.positionMs * 100L) / entry.durationMs).toInt().coerceIn(0, 99)
        } else 0
        holder.txtMinute.text = "${entry.positionMs / 60000L} min · $percent%"
        holder.progress.progress = percent
        val poster = entry.media?.posterUrl ?: entry.media?.rawLogo
            ?: posterByKey[entry.title.lowercase().trim()] ?: ""
        Glide.with(holder.img.context)
            .load(poster)
            .centerCrop()
            .placeholder(R.drawable.bg_tile_glass)
            .into(holder.img)
        holder.itemView.contentDescription = "${entry.title}, continuar desde ${entry.positionMs / 60000L} minutos, $percent por ciento visto"
        holder.itemView.setOnClickListener { onItemClick(entry) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgWatchPoster)
        val txtTitle: TextView = view.findViewById(R.id.txtWatchTitle)
        val txtMinute: TextView = view.findViewById(R.id.txtWatchMinute)
        val progress: ProgressBar = view.findViewById(R.id.watchProgress)
    }
}

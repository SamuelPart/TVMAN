package com.tvman.app

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.tvman.app.databinding.ItemChannelBinding
import com.tvman.app.databinding.ItemChannelTunerBinding

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onChannelClick: (Channel) -> Unit,
    private val isFavorite: ((Channel) -> Boolean)? = null,
    private val onFavoriteToggle: ((Channel) -> Unit)? = null,
    private val showAds: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** true = renderiza filas horizontales, con foco ampliado para navegación en TV. */
    var tunerMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** Lista real + marcadores de anuncio (null → no; objeto AD_MARKER → sí). */
    private var displayList: List<Any?> = emptyList()

    /** Columnas de la cuadrícula: el anuncio se inserta cada [NativeAds.GRID_AD_ROWS] filas. */
    var gridColumns: Int = 3
        set(value) {
            if (field != value) {
                field = value
                rebuildDisplayList()
                notifyDataSetChanged()
            }
        }

    companion object {
        private const val TYPE_CARD = 0
        private const val TYPE_TUNER = 1
    }

    init { rebuildDisplayList() }

    private fun rebuildDisplayList() {
        displayList = if (showAds) NativeAds.interleaveWithAds(channels, gridColumns) else channels
    }

    fun channelsSnapshot(): List<Channel> = channels.toList()

    fun isAdAt(position: Int): Boolean = NativeAds.isAdMarker(displayList.getOrNull(position))

    // ================= VIEW HOLDERS =================

    inner class ChannelViewHolder(private val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel) {
            binding.root.springPress()
            binding.txtChannelName.text = channel.name
            loadLogo(binding.imgLogo, channel)
            bindStar(binding.imgChannelFav, channel)
            binding.root.enableTVFocus()
            binding.root.setOnClickListener { onChannelClick(channel) }
        }
    }

    inner class TunerViewHolder(private val binding: ItemChannelTunerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: Channel, position: Int) {
            binding.root.springPress()
            binding.txtChannelNumber.text = String.format("%02d", position + 1)
            binding.txtChannelName.text = channel.name
            val meta = listOfNotNull(
                channel.group?.takeIf { it.isNotBlank() },
                channel.country?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            binding.txtChannelMeta.text =
                if (meta.isNotBlank()) meta else "OK para sintonizar"
            loadLogo(binding.imgLogo, channel)
            bindStar(binding.imgChannelFav, channel)
            binding.viewChannelLiveDot.alpha = 1f
            binding.root.enableTVFocus()
            binding.root.contentDescription = "Canal ${position + 1}: ${channel.name}. ${meta.ifBlank { "Canal en vivo" }}. Pulsa OK para sintonizar."
            binding.root.setOnClickListener { onChannelClick(channel) }
        }
    }

    private fun loadLogo(target: android.widget.ImageView, channel: Channel) {
        Glide.with(target.context)
            .load(channel.logoUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.drawable.bg_placeholder)
            .error(R.drawable.ic_tv)
            .fallback(R.drawable.ic_tv)
            .into(target)
    }

    private fun bindStar(star: android.widget.ImageView, channel: Channel) {
        if (isFavorite != null) {
            star.visibility = View.VISIBLE
            star.isFocusable = true
            star.isClickable = true
            star.foreground = androidx.core.content.ContextCompat.getDrawable(star.context, R.drawable.tv_focus_ring)
            star.contentDescription = if (isFavorite?.invoke(channel) == true) "Quitar de favoritos" else "Añadir a favoritos"
            updateFavIcon(star, isFavorite?.invoke(channel) == true)
            star.setOnClickListener {
                onFavoriteToggle?.invoke(channel)
                val nowFav = isFavorite?.invoke(channel) == true
                star.contentDescription = if (nowFav) "Quitar de favoritos" else "Añadir a favoritos"
                updateFavIcon(star, nowFav)
                android.animation.ObjectAnimator.ofFloat(it, "scaleX", 0.6f, 1f)
                    .apply { duration = 280; start() }
                android.animation.ObjectAnimator.ofFloat(it, "scaleY", 0.6f, 1f)
                    .apply { duration = 280; start() }
            }
        } else {
            star.visibility = View.GONE
            star.isFocusable = false
            star.isClickable = false
        }
    }

    private fun updateFavIcon(star: android.widget.ImageView, fav: Boolean) {
        if (fav) {
            star.setImageResource(R.drawable.ic_ios_star_fill)
            star.imageTintList =
                android.content.res.ColorStateList.valueOf(0xFFFFD60A.toInt())
        } else {
            star.setImageResource(R.drawable.ic_ios_star)
            star.imageTintList =
                android.content.res.ColorStateList.valueOf(0xE6FFFFFF.toInt())
        }
    }

    inner class AdViewHolder(val slot: android.widget.FrameLayout) :
        RecyclerView.ViewHolder(slot) {
        private var loaded = false
        fun ensureLoaded() {
            if (loaded) return
            loaded = true
            val activity = slot.context as? Activity ?: return
            NativeAds.attachRecycler(activity, slot, NativeAds.VARIANT_COMPACT) {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) notifyItemChanged(p)
            }
        }
    }

    // ================= ADAPTER CORE =================

    override fun getItemViewType(position: Int): Int {
        if (NativeAds.isAdMarker(displayList.getOrNull(position))) return NativeAds.GRID_AD_TYPE
        return if (tunerMode) TYPE_TUNER else TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            NativeAds.GRID_AD_TYPE -> AdViewHolder(NativeAds.createAdSlot(parent))
            TYPE_TUNER -> TunerViewHolder(ItemChannelTunerBinding.inflate(inf, parent, false))
            else -> ChannelViewHolder(ItemChannelBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AdViewHolder) {
            holder.ensureLoaded()
            return
        }
        animateEntrance(holder.itemView, position)
        // Índice real del canal (saltando los anuncios intercalados).
        val contentIndex = contentIndexAt(position)
        when (holder) {
            is TunerViewHolder -> holder.bind(channels[contentIndex], contentIndex)
            is ChannelViewHolder -> holder.bind(channels[contentIndex])
        }
    }

    override fun getItemCount(): Int = displayList.size

    /** Posición real dentro de [channels] para una posición de la lista intercalada. */
    private fun contentIndexAt(position: Int): Int {
        var real = 0
        for (i in 0 until position) {
            if (!NativeAds.isAdMarker(displayList.getOrNull(i))) real++
        }
        return real
    }

    /** iPhone/PS stagger: each new card rises + fades in as it gets scrolled into view. */
    private var lastAnimatedPosition = -1

    private fun animateEntrance(view: View, position: Int) {
        if (position <= lastAnimatedPosition) return
        lastAnimatedPosition = position
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 44f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .withEndAction(null)
            .start()
    }

    fun updateList(newChannels: List<Channel>) {
        lastAnimatedPosition = -1
        channels = newChannels
        rebuildDisplayList()
        notifyDataSetChanged()
    }


}

package com.tvman.app

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
    private val onFavoriteToggle: ((Channel) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** true = renderiza filas horizontales, con foco ampliado para navegación en TV. */
    var tunerMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val TYPE_CARD = 0
        private const val TYPE_TUNER = 1
    }

    fun channelsSnapshot(): List<Channel> = channels.toList()

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
            binding.txtChannelNumber.text = String.format("%03d", position + 1)
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

    // ================= ADAPTER CORE =================

    override fun getItemViewType(position: Int): Int {
        return if (tunerMode) TYPE_TUNER else TYPE_CARD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TUNER -> TunerViewHolder(ItemChannelTunerBinding.inflate(inf, parent, false))
            else -> ChannelViewHolder(ItemChannelBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        animateEntrance(holder.itemView, position)
        // La posición del elemento coincide con su número en la lista de canales.
        when (holder) {
            is TunerViewHolder -> holder.bind(channels[position], position)
            is ChannelViewHolder -> holder.bind(channels[position])
        }
    }

    override fun getItemCount(): Int = channels.size

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
        notifyDataSetChanged()
    }


}

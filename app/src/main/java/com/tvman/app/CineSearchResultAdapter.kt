package com.tvman.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.tvman.app.databinding.ItemCineSearchResultBinding

/**
 * Cuadricula de posters con el TITULO DEBAJO del poster (estilo buscador).
 * Se usa en: buscador de cine, "See all / Ver todo" y la cuadricula principal
 * de Cine. Soporta favoritos opcionales (estrella sobre el poster).
 */
class CineSearchResultAdapter(
    private var mediaList: List<CineMedia>,
    private val onMediaClick: (CineMedia) -> Unit,
    private val isFavorite: ((CineMedia) -> Boolean)? = null,
    private val onFavoriteToggle: ((CineMedia) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Resolver opcional del LOGO de plataforma por titulo (secciones de Inicio
     *  y listados con badge). Si es null, el badge no se muestra. */
    var platformBadgeResolver: ((CineMedia) -> String?)? = null

    inner class VH(private val binding: ItemCineSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(media: CineMedia) {
            binding.txtTitle.text = media.title

            val isMovie = media.type == "movie"
            val year = media.releaseDate?.take(4)
            binding.txtSubtitle.text = buildString {
                append(if (isMovie) "Película" else "Serie")
                if (!year.isNullOrEmpty()) append(" · $year")
            }

            val fallback = if (isMovie) R.drawable.ic_ios_movie else R.drawable.ic_ios_tv
            val img = if (!media.posterUrl.isNullOrEmpty()) media.posterUrl else media.rawLogo
            Glide.with(binding.imgPoster.context)
                .load(img)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_placeholder)
                .error(fallback)
                .fallback(fallback)
                .into(binding.imgPoster)

            // Badge de plataforma (opcional)
            val badge = platformBadgeResolver?.invoke(media)
            if (badge != null) {
                binding.imgPlatformBadge.visibility = View.VISIBLE
                Glide.with(binding.imgPlatformBadge)
                    .load(badge)
                    .fitCenter()
                    .placeholder(R.drawable.bg_tile_glass)
                    .into(binding.imgPlatformBadge)
            } else {
                binding.imgPlatformBadge.visibility = View.GONE
            }

            // Favoritos opcionales (solo en la cuadricula principal de Cine)
            if (isFavorite != null && onFavoriteToggle != null) {
                binding.imgFav.visibility = View.VISIBLE
                paintFav(binding, isFavorite.invoke(media))
                binding.imgFav.setOnClickListener {
                    onFavoriteToggle.invoke(media)
                    paintFav(binding, isFavorite.invoke(media))
                }
            } else {
                binding.imgFav.visibility = View.GONE
            }

            binding.root.enableTVFocus()
            binding.root.setOnClickListener { onMediaClick(media) }
        }

        private fun paintFav(binding: ItemCineSearchResultBinding, fav: Boolean) {
            if (fav) {
                binding.imgFav.setImageResource(R.drawable.ic_ios_star_fill)
                binding.imgFav.imageTintList =
                    android.content.res.ColorStateList.valueOf(0xFFFFD60A.toInt())
            } else {
                binding.imgFav.setImageResource(R.drawable.ic_ios_star)
                binding.imgFav.imageTintList =
                    android.content.res.ColorStateList.valueOf(0xE6FFFFFF.toInt())
            }
        }
    }

    override fun getItemViewType(position: Int): Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return VH(
            ItemCineSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as VH).bind(mediaList[position])
    }

    override fun getItemCount(): Int = mediaList.size

    fun updateList(list: List<CineMedia>) {
        mediaList = list
        notifyDataSetChanged()
    }
}

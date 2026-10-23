package com.tvman.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Left-hand channel navigation rail designed for remote focus. */
data class TvChannelSection(val key: String, val title: String, val count: Int)

class TvChannelCategoryAdapter(
    private var sections: List<TvChannelSection>,
    private val onSelect: (TvChannelSection) -> Unit
) : RecyclerView.Adapter<TvChannelCategoryAdapter.SectionHolder>() {

    var selectedKey: String = "recent"
        private set

    inner class SectionHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.txtTvChannelSectionTitle)
        private val count: TextView = itemView.findViewById(R.id.txtTvChannelSectionCount)
        private val marker: View = itemView.findViewById(R.id.viewTvChannelSectionMarker)

        fun bind(section: TvChannelSection) {
            val selected = section.key == selectedKey
            title.text = section.title
            count.text = section.count.toString()
            count.visibility = if (section.count > 0) View.VISIBLE else View.INVISIBLE
            title.setTextColor(if (selected) Color.WHITE else Color.rgb(181, 194, 211))
            count.setTextColor(if (selected) itemView.context.getColor(R.color.tv_accent) else Color.rgb(125, 142, 164))
            marker.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            val bg = GradientDrawable().apply {
                cornerRadius = itemView.resources.displayMetrics.density * 12f
                setColor(if (selected) Color.rgb(64, 40, 124) else Color.TRANSPARENT)
            }
            itemView.background = bg
            itemView.contentDescription = "${section.title}, ${section.count} canales${if (selected) ", seleccionado" else ""}"
            itemView.nextFocusRightId = R.id.rvChannelsGrid
            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val list = itemView.rootView.findViewById<RecyclerView>(R.id.rvChannelsGrid)
                    if (list != null && list.visibility == View.VISIBLE) {
                        list.post {
                            val first = list.findViewHolderForAdapterPosition(0)?.itemView
                            if (first?.requestFocus() != true) list.requestFocus()
                        }
                        true
                    } else false
                } else false
            }
            itemView.setOnClickListener {
                if (selectedKey != section.key) {
                    val old = selectedKey
                    selectedKey = section.key
                    val oldPosition = sections.indexOfFirst { it.key == old }
                    if (oldPosition >= 0) notifyItemChanged(oldPosition)
                    val currentPosition = bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) notifyItemChanged(currentPosition)
                }
                onSelect(section)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionHolder = SectionHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_tv_channel_section, parent, false)
    )

    override fun onBindViewHolder(holder: SectionHolder, position: Int) = holder.bind(sections[position])
    override fun getItemCount(): Int = sections.size

    fun submitSections(updated: List<TvChannelSection>, preserveSelection: Boolean = true) {
        sections = updated
        if (!preserveSelection || sections.none { it.key == selectedKey }) {
            selectedKey = sections.firstOrNull()?.key ?: "all"
        }
        notifyDataSetChanged()
    }

    fun selectKey(key: String) {
        if (sections.none { it.key == key }) return
        val oldPosition = sections.indexOfFirst { it.key == selectedKey }
        selectedKey = key
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        val newPosition = sections.indexOfFirst { it.key == key }
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    fun positionOf(key: String): Int = sections.indexOfFirst { it.key == key }
}

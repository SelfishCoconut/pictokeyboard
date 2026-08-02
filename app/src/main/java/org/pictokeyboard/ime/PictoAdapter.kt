package org.pictokeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.PictoEntity

class PictoAdapter(private val onClick: (PictoEntity) -> Unit, private val onLongClick: (PictoEntity) -> Unit = {}) :
    ListAdapter<PictoAdapter.Tile, PictoAdapter.VH>(DIFF) {

    /**
     * One key as it should be drawn: the picto plus every presentation choice
     * that affects it.
     *
     * The style used to live in adapter fields and be repainted from
     * `submitList`'s completion callback. That callback is not guaranteed to run
     * -- AsyncListDiffer drops it when a newer submit supersedes the diff -- so a
     * style change arriving just before another list update was lost for good,
     * leaving tiles painted in the previous category's colour. Folding the style
     * into the item makes it something DiffUtil can see, and removes the need
     * for a callback at all.
     */
    data class Tile(
        val picto: PictoEntity,
        /**
         * What Coil should load, already resolved off the main thread by
         * [keyboardImageModel]. A [java.io.File], an ARASAAC URL, or null for the
         * placeholder -- `bind` only draws it, and never asks the filesystem.
         */
        val imageModel: Any?,
        val frameColor: Int,
        val borderStyle: String,
        val borderWidthDp: Int,
        val showLabel: Boolean,
    )

    /**
     * How the selected category wants its keys drawn. Bundled rather than passed
     * loose because these four always travel together and always come from the
     * same category -- the same reason [ViewStyles.ChipMetrics] exists.
     */
    data class Style(
        val categoryColor: Int,
        val showLabels: Boolean,
        val borderStyle: String = BorderStyles.SOLID,
        val borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
    )

    /**
     * [imageModels] maps picto id to its resolved Coil model. A picto missing
     * from the map draws the placeholder, which is the same outcome as an
     * explicit null, so a partial map degrades rather than crashing.
     */
    fun submit(pictos: List<PictoEntity>, imageModels: Map<String, Any?>, style: Style) {
        submitList(
            pictos.map { picto ->
                Tile(
                    picto = picto,
                    imageModel = imageModels[picto.id],
                    // Borrowed pictos keep their original category's colour.
                    frameColor = picto.colorArgbOverride ?: style.categoryColor,
                    borderStyle = style.borderStyle,
                    borderWidthDp = style.borderWidthDp,
                    showLabel = style.showLabels && picto.label.isNotBlank(),
                )
            },
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picto, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: TileFrameLayout = view.findViewById(R.id.picto_tile)
        private val image: ImageView = view.findViewById(R.id.picto_image)
        private val label: TextView = view.findViewById(R.id.picto_label)

        fun bind(item: Tile) {
            val picto = item.picto
            tile.background = ViewStyles.framedTile(
                colorArgb = item.frameColor,
                strokeWidthPx = dp(item.borderWidthDp),
                cornerRadiusPx = dp(TILE_CORNER_DP).toFloat(),
                // `tile`, not white-in-light-only: ARASAAC art is black line work,
                // so the tile stays white in dark mode too.
                fillArgb = ContextCompat.getColor(itemView.context, R.color.tile),
                borderStyle = item.borderStyle,
            )

            val model = item.imageModel
            if (model == null) {
                image.setImageResource(R.drawable.ic_picto_placeholder)
            } else {
                image.load(model) {
                    crossfade(false)
                    placeholder(R.drawable.ic_picto_placeholder)
                    error(R.drawable.ic_picto_placeholder)
                }
            }

            label.text = picto.label
            label.visibility = if (item.showLabel) View.VISIBLE else View.GONE

            // The name comes from the data, not from whether the caption happens
            // to be drawn. "Show captions under pictos" is a supported setting,
            // and with it off every key on an AAC board -- the vocabulary itself
            // -- was an anonymous button to TalkBack.
            itemView.contentDescription = picto.spokenText.ifBlank { picto.label }

            itemView.setOnClickListener { onClick(picto) }
            itemView.setOnLongClickListener {
                onLongClick(picto)
                true
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TILE_CORNER_DP = 12

        val DIFF = object : DiffUtil.ItemCallback<Tile>() {
            override fun areItemsTheSame(oldItem: Tile, newItem: Tile) =
                oldItem.picto.id == newItem.picto.id

            override fun areContentsTheSame(oldItem: Tile, newItem: Tile) =
                oldItem == newItem
        }
    }
}

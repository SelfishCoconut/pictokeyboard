package org.pictokeyboard.ime

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.BorderStyles
import org.pictokeyboard.data.db.PictoEntity
import java.io.File

class PictoAdapter(private val onClick: (PictoEntity) -> Unit, private val onLongClick: (PictoEntity) -> Unit = {}) :
    RecyclerView.Adapter<PictoAdapter.VH>() {

    private var items: List<PictoEntity> = emptyList()
    private var categoryColor: Int = Color.LTGRAY
    private var borderStyle: String = BorderStyles.SOLID
    private var borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP
    private var showLabels: Boolean = true

    fun submit(
        pictos: List<PictoEntity>,
        categoryColor: Int,
        showLabels: Boolean,
        borderStyle: String = BorderStyles.SOLID,
        borderWidthDp: Int = BorderStyles.DEFAULT_WIDTH_DP,
    ) {
        this.items = pictos
        this.categoryColor = categoryColor
        this.borderStyle = borderStyle
        this.borderWidthDp = borderWidthDp
        this.showLabels = showLabels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_picto, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], categoryColor, borderStyle, borderWidthDp, showLabels)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tile: SquareFrameLayout = view.findViewById(R.id.picto_tile)
        private val image: ImageView = view.findViewById(R.id.picto_image)
        private val label: TextView = view.findViewById(R.id.picto_label)

        fun bind(picto: PictoEntity, categoryColor: Int, borderStyle: String, borderWidthDp: Int, showLabels: Boolean) {
            // Borrowed pictos keep their original category's colour via the override.
            val color = picto.colorArgbOverride ?: categoryColor
            tile.background = ViewStyles.framedTile(
                colorArgb = color,
                strokeWidthPx = dp(borderWidthDp),
                cornerRadiusPx = dp(12).toFloat(),
                fillArgb = Color.WHITE,
                borderStyle = borderStyle,
            )

            val path = picto.imagePath
            if (path != null && File(path).exists()) {
                image.load(File(path)) {
                    crossfade(false)
                    placeholder(R.drawable.ic_picto_placeholder)
                    error(R.drawable.ic_picto_placeholder)
                }
            } else if (picto.arasaacId != null) {
                image.load(ArasaacUrls.image(picto.arasaacId))
            } else {
                image.setImageResource(R.drawable.ic_picto_placeholder)
            }

            label.text = picto.label
            label.visibility = if (showLabels && picto.label.isNotBlank()) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onClick(picto) }
            itemView.setOnLongClickListener {
                onLongClick(picto)
                true
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }
}

package org.pictokeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.arasaac.ArasaacUrls
import org.pictokeyboard.data.db.CategoryEntity
import java.io.File

class CategoryAdapter(private val onClick: (CategoryEntity) -> Unit) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<CategoryEntity> = emptyList()
    private var selectedId: String? = null

    fun submit(categories: List<CategoryEntity>, selectedId: String?) {
        this.items = categories
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], items[position].id == selectedId)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: LinearLayout = view.findViewById(R.id.category_root)
        private val name: TextView = view.findViewById(R.id.category_name)
        private val icon: ImageView = view.findViewById(R.id.category_icon)

        fun bind(category: CategoryEntity, selected: Boolean) {
            name.text = category.name
            val color = category.colorArgb
            val fill = if (selected) color else ViewStyles.tint(color, 0x33)
            root.background = ViewStyles.framedTile(
                colorArgb = color,
                strokeWidthPx = dp(category.borderWidthDp),
                cornerRadiusPx = dp(10).toFloat(),
                fillArgb = fill,
                borderStyle = category.borderStyle,
            )
            name.setTextColor(
                if (selected) ViewStyles.contrastText(color) else 0xFF222222.toInt(),
            )

            val iconPath = category.iconImagePath
            val iconModel: Any? = when {
                iconPath != null && File(iconPath).exists() -> File(iconPath)
                category.iconArasaacId != null -> ArasaacUrls.image(category.iconArasaacId)
                else -> null
            }
            if (iconModel != null) {
                icon.visibility = View.VISIBLE
                icon.load(iconModel)
            } else {
                icon.visibility = View.GONE
            }

            root.setOnClickListener { onClick(category) }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }
}

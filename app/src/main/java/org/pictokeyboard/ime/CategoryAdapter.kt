package org.pictokeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.ui.theme.CategoryColors

class CategoryAdapter(private val onClick: (CategoryEntity) -> Unit) :
    ListAdapter<CategoryAdapter.Row, CategoryAdapter.VH>(DIFF) {

    /**
     * One chip as it should be drawn. Selection is folded into the item for the
     * same reason the picto tile folds in its style: as adapter state it was
     * invisible to DiffUtil and had to be repainted from `submitList`'s
     * completion callback, which AsyncListDiffer drops when a newer submit
     * supersedes the diff -- leaving the selected chip drawn as unselected.
     */
    data class Row(
        val category: CategoryEntity,
        val selected: Boolean,
        /**
         * The chip's icon, already resolved off the main thread by
         * [keyboardIconModel]. Null draws no icon at all -- a category with no
         * picto is shown by its name alone, which is a supported state.
         */
        val iconModel: Any?,
    )

    /**
     * [iconModels] maps category id to its resolved Coil model. A category
     * missing from the map renders name-only, the same as an explicit null.
     */
    fun submit(categories: List<CategoryEntity>, selectedId: String?, iconModels: Map<String, Any?>) {
        submitList(categories.map { Row(it, it.id == selectedId, iconModels[it.id]) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: LinearLayout = view.findViewById(R.id.category_root)
        private val name: TextView = view.findViewById(R.id.category_name)
        private val icon: ImageView = view.findViewById(R.id.category_icon)

        fun bind(item: Row) {
            val category = item.category
            val selected = item.selected
            name.text = category.name
            val color = category.colorArgb
            root.background = ViewStyles.categoryChip(
                colorArgb = color,
                selected = selected,
                // The chip sits on the spine, which is `paper` -- so that is what
                // its outline has to stay visible against.
                backgroundArgb = ContextCompat.getColor(itemView.context, R.color.paper),
                metrics = ViewStyles.ChipMetrics(
                    cornerRadiusPx = dp(CHIP_CORNER_DP).toFloat(),
                    strokeWidthPx = dp(category.borderWidthDp),
                    borderStyle = category.borderStyle,
                    // Read from the configuration, not the view: onBindViewHolder
                    // runs before the item is attached, so View.getLayoutDirection()
                    // cannot resolve yet and answers LTR -- then answers correctly
                    // once the holder is recycled, giving one list two notch sides.
                    rtl = itemView.resources.configuration.layoutDirection ==
                        View.LAYOUT_DIRECTION_RTL,
                ),
            )
            // A selected chip is flooded with its own hue, so its label has to be
            // chosen against that hue rather than against the keyboard background.
            name.setTextColor(
                if (selected) {
                    CategoryColors.contrastText(color)
                } else {
                    ContextCompat.getColor(itemView.context, R.color.ink)
                },
            )

            val iconModel = item.iconModel
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

    companion object {
        private const val CHIP_CORNER_DP = 12

        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) =
                oldItem.category.id == newItem.category.id

            override fun areContentsTheSame(oldItem: Row, newItem: Row) =
                oldItem == newItem
        }
    }
}

package org.pictokeyboard.ime

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.pictokeyboard.R
import org.pictokeyboard.data.db.BoardEntity
import org.pictokeyboard.ime.PressFeedback.confirmPress
import org.pictokeyboard.ui.theme.CategoryColors

/**
 * The board tab strip along the top of the keyboard.
 *
 * Switching board is **navigation, not configuration**: at the doctor's, the
 * communicator changes situation from inside WhatsApp and the vocabulary is
 * already right. That is why it lives on the keyboard and stays reachable even
 * when the caregiver has set a PIN — the PIN protects the board's *contents*, not
 * the choice of which board to speak from.
 */
class BoardTabAdapter(
    private val onClick: (BoardEntity) -> Unit,
    /** A supplier for the reason given on [PictoAdapter]. */
    private val haptics: () -> Boolean = { true },
    private val palette: () -> KeyboardPalette? = { null },
) : ListAdapter<BoardTabAdapter.Row, BoardTabAdapter.VH>(DIFF) {

    /**
     * One tab as it should be drawn. Selection is folded into the item rather
     * than kept as adapter state, for the same reason the category chip folds it
     * in: as adapter state it is invisible to DiffUtil and has to be repainted
     * from `submitList`'s completion callback, which AsyncListDiffer drops when a
     * newer submit supersedes the diff — leaving the active tab drawn as inactive.
     */
    data class Row(val board: BoardEntity, val selected: Boolean, val iconModel: Any?)

    fun submit(boards: List<BoardEntity>, activeId: String?, iconModels: Map<String, Any?>) {
        submitList(boards.map { Row(it, it.id == activeId, iconModels[it.id]) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_board_tab, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: LinearLayout = view.findViewById(R.id.board_tab_root)
        private val border: View = view.findViewById(R.id.board_tab_border)
        private val name: TextView = view.findViewById(R.id.board_tab_name)
        private val icon: ImageView = view.findViewById(R.id.board_tab_icon)

        fun bind(item: Row) {
            val board = item.board
            val context = itemView.context
            val skin = palette()
            val paper = skin?.paper ?: ContextCompat.getColor(context, R.color.paper)
            name.text = board.name

            // 3dp on the active tab, 1dp and faded on the rest. Thickness
            // carries the state as well as colour does, so the strip still reads
            // in greyscale and to a colour-blind user.
            border.updateLayoutParams { height = dp(if (item.selected) ACTIVE_BORDER_DP else IDLE_BORDER_DP) }
            // outlineOn, not the raw hue, for the *active* border too. The
            // migrated board's colour is a navy picked against the light theme;
            // drawn straight onto the dark keyboard it is a line you cannot see,
            // which loses the one cue that says which board is speaking. This
            // keeps the board's hue and lifts it to 3:1 against whichever paper
            // is behind it.
            val hue = CategoryColors.outlineOn(board.colorArgb, paper)
            border.setBackgroundColor(hue)
            border.alpha = if (item.selected) 1f else IDLE_ALPHA
            // The active tab is a piece of the board's own surface, so it reads
            // as joined to what it opens; the rest are transparent and show the
            // strip's trough behind them. `paper` against the strip's `line`
            // works in both schemes, which a raised-card treatment would not:
            // `card` is *lighter* than `paper` in light mode and in dark, so an
            // inactive tab drawn on it would read as the raised one.
            root.setBackgroundColor(if (item.selected) paper else Color.TRANSPARENT)
            name.setTextColor(
                if (item.selected) {
                    skin?.ink ?: ContextCompat.getColor(context, R.color.ink)
                } else {
                    skin?.inkSoft ?: ContextCompat.getColor(context, R.color.ink_soft)
                },
            )

            val model = item.iconModel
            icon.visibility = if (model == null) View.GONE else View.VISIBLE
            if (model != null) {
                icon.load(model)
                icon.alpha = if (item.selected) 1f else IDLE_ALPHA
            }

            root.setOnClickListener {
                it.confirmPress(haptics())
                onClick(board)
            }
            describeAsTab(item.selected)
        }

        /**
         * Announces the tab as a tab.
         *
         * Without this a screen-reader user hears a row of unrelated buttons with
         * no indication of which board they are in — and "which situation am I
         * speaking from" is the one thing this strip exists to answer.
         */
        private fun describeAsTab(selected: Boolean) {
            root.isSelected = selected
            ViewCompat.setAccessibilityDelegate(
                root,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.roleDescription = host.context.getString(R.string.kb_board_tab_role)
                        info.isSelected = selected
                    }
                },
            )
        }

        private fun dp(value: Int): Int = (value * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ACTIVE_BORDER_DP = 3
        private const val IDLE_BORDER_DP = 1

        /** How far an inactive tab's colour and picto recede. */
        private const val IDLE_ALPHA = 0.6f

        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.board.id == newItem.board.id
            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}

package com.callagent.host.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.callagent.host.mesh.HostNode
import com.callagent.host.mesh.NodeHealth
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.R as MaterialR

/** One row of the server node list, in priority order. */
data class NodeRow(
    val key: String,
    val title: String,
    val detail: String,
    val active: Boolean,
    val healthy: Boolean?,
    val removable: Boolean,
    /** Recent availability, latency and call readiness, or null before any probe. */
    val quality: String? = null
)

object NodeRowPresenter {
    fun rows(nodes: List<HostNode>, health: Map<String, NodeHealth>, activeKey: String?): List<NodeRow> =
        nodes.mapIndexed { index, node ->
            val state = health[node.key]
            val active = node.key == activeKey
            val role = if (active) "使用中" else "备用"
            val status = when {
                state == null || state.checkedAt == 0L -> "尚未检测"
                state.healthy -> state.rttMillis?.let { "正常 · 延迟 $it ms" } ?: "正常"
                else -> state.issue ?: "不可用"
            }
            NodeRow(
                key = node.key,
                title = "${index + 1}. ${node.label}",
                detail = "$role · $status",
                active = active,
                healthy = state?.takeIf { it.checkedAt > 0L }?.healthy,
                removable = !active,
                quality = state?.let(::quality)
            )
        }

    private fun quality(state: NodeHealth): String? =
        if (state.checkedAt == 0L) null else if (state.callReady) "可通话" else "仅短信"

    /** Label/value pairs for the node detail dialog. */
    fun details(node: HostNode, state: NodeHealth?, active: Boolean, now: Long): List<Pair<String, String>> = buildList {
        add("地址" to node.apiBaseUrl)
        add("角色" to if (active) "正在使用" else "备用")
        if (state == null || state.checkedAt == 0L) {
            add("状态" to "尚未检测")
            return@buildList
        }
        add("状态" to if (state.healthy) "正常" else (state.issue ?: "不可用"))
        add("旧手机" to if (state.gatewayOnline) "在线" else "不在线")
        add("通话" to if (state.callReady) "可通话（旧手机已在该节点就绪）" else "仅短信（旧手机未在该节点就绪）")
        state.rttMillis?.let { add("延迟" to "$it ms") }
        add("检测时间" to "${((now - state.checkedAt) / 1000).coerceAtLeast(0)} 秒前")
    }
}

/**
 * Server nodes ordered by priority. Drag a row by its handle (or use the
 * accessibility actions) to change the order; [onReorder] receives the new
 * key order once a drag ends.
 */
@SuppressLint("ViewConstructor")
class NodeListView(
    context: Context,
    rows: List<NodeRow>,
    private val onReorder: (List<String>) -> Unit,
    private val onRemove: (NodeRow) -> Unit,
    private val onSelect: (NodeRow) -> Unit = {}
) : RecyclerView(context) {
    private val items = rows.toMutableList()
    private val touchHelper: ItemTouchHelper

    init {
        layoutManager = LinearLayoutManager(context)
        isNestedScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        adapter = RowAdapter()
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
                move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = true

            override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                commitOrder()
            }
        })
        touchHelper.attachToRecyclerView(this)
    }

    private var orderAtDragStart: List<String> = items.map { it.key }

    private fun move(from: Int, to: Int) {
        if (from == NO_POSITION || to == NO_POSITION || from == to) return
        val row = items.removeAt(from)
        items.add(to, row)
        adapter?.notifyItemMoved(from, to)
    }

    private fun commitOrder() {
        val order = items.map { it.key }
        if (order != orderAtDragStart) {
            orderAtDragStart = order
            onReorder(order)
        }
    }

    private inner class RowAdapter : Adapter<RowHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RowHolder(context)
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(items[position])
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class RowHolder(context: Context) : ViewHolder(MaterialCardView(context)) {
        private val card = itemView as MaterialCardView
        private val handle = MaterialTextView(context)
        private val title = MaterialTextView(context)
        private val detail = MaterialTextView(context)
        private val quality = MaterialTextView(context)
        private val remove = MaterialButton(context, null, MaterialR.attr.materialButtonOutlinedStyle)
        private val accessibilityActions = mutableListOf<Int>()

        init {
            card.layoutParams = MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4); bottomMargin = dp(4) }
            card.radius = dp(16).toFloat()
            card.cardElevation = 0f
            card.strokeWidth = dp(1)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(12), dp(10))
            }
            handle.text = "≡"
            handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            handle.gravity = Gravity.CENTER
            handle.minWidth = dp(40)
            handle.minHeight = dp(48)
            handle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    orderAtDragStart = items.map { it.key }
                    touchHelper.startDrag(this)
                }
                false
            }
            row.addView(handle)
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            title.setTextAppearance(MaterialR.style.TextAppearance_Material3_TitleSmall)
            detail.setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
            column.addView(title)
            column.addView(detail)
            quality.setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelSmall)
            column.addView(quality)
            row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            remove.text = "移除"
            remove.isAllCaps = false
            row.addView(remove)
            card.addView(row)
        }

        fun bind(item: NodeRow) {
            title.text = item.title
            detail.text = item.detail
            quality.text = item.quality.orEmpty()
            quality.visibility = if (item.quality == null) View.GONE else View.VISIBLE
            quality.setTextColor(color(MaterialR.attr.colorOnSurfaceVariant))
            card.isClickable = true
            card.setOnClickListener { onSelect(item) }
            title.setTextColor(color(MaterialR.attr.colorOnSurface))
            detail.setTextColor(color(when (item.healthy) {
                false -> androidx.appcompat.R.attr.colorError
                else -> MaterialR.attr.colorOnSurfaceVariant
            }))
            card.setCardBackgroundColor(color(
                if (item.active) MaterialR.attr.colorSecondaryContainer else MaterialR.attr.colorSurfaceContainerLow
            ))
            card.strokeColor = color(MaterialR.attr.colorOutlineVariant)
            remove.visibility = if (item.removable) View.VISIBLE else View.GONE
            remove.contentDescription = "移除 ${item.title}"
            remove.setOnClickListener { onRemove(item) }
            card.contentDescription = listOfNotNull(item.title, item.detail, item.quality).joinToString("，")
            accessibilityActions.forEach { ViewCompat.removeAccessibilityAction(card, it) }
            accessibilityActions.clear()
            val position = bindingAdapterPosition
            if (position > 0) {
                accessibilityActions += ViewCompat.addAccessibilityAction(card, "提高优先级") { _, _ -> moveByAccessibility(-1) }
            }
            if (position in 0 until items.size - 1) {
                accessibilityActions += ViewCompat.addAccessibilityAction(card, "降低优先级") { _, _ -> moveByAccessibility(1) }
            }
        }

        private fun moveByAccessibility(delta: Int): Boolean {
            val from = bindingAdapterPosition
            val to = from + delta
            if (from == NO_POSITION || to !in items.indices) return false
            orderAtDragStart = items.map { it.key }
            move(from, to)
            adapter?.notifyItemChanged(from)
            adapter?.notifyItemChanged(to)
            commitOrder()
            return true
        }
    }

    private fun color(attribute: Int): Int = MaterialColors.getColor(this, attribute)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

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
import com.callagent.host.push.NodePushStatus
import com.callagent.host.push.PushProbe
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
    fun rows(
        nodes: List<HostNode>,
        health: Map<String, NodeHealth>,
        activeKey: String?,
        push: Map<String, NodePushStatus> = emptyMap(),
        probes: Map<String, PushProbe.NodeProbe> = emptyMap(),
        /** Elapsed realtime for the probes' ages; read from the clock when omitted. */
        now: Long? = null
    ): List<NodeRow> =
        nodes.mapIndexed { index, node ->
            val state = health[node.key]
            val active = node.key == activeKey
            val role = if (active) "使用中" else "备用"
            // Line two: role, health, calls, and FCM when this node can wake the phone.
            val status = when {
                state == null || state.checkedAt == 0L -> listOf("尚未检测")
                state.healthy -> listOfNotNull(state.rttMillis?.let { "$it ms" } ?: "正常", quality(state))
                else -> listOf(state.issue ?: "不可用")
            }
            val fcm = push[node.key]?.let { it.kind == NodePushStatus.Kind.REGISTERED ||
                it.kind == NodePushStatus.Kind.RETRYING && it.stillRegistered } == true
            NodeRow(
                key = node.key,
                title = "${index + 1}. ${node.label}",
                detail = (listOf(role) + status + listOfNotNull("FCM".takeIf { fcm })).joinToString(" · "),
                active = active,
                healthy = state?.takeIf { it.checkedAt > 0L }?.healthy,
                removable = !active,
                // Line three: only how this node's pushes travel, once known.
                quality = probes[node.key]?.let { pushPath(it, now ?: android.os.SystemClock.elapsedRealtime()) }
            )
        }

    private fun quality(state: NodeHealth): String = if (state.callReady) "可通话" else "仅短信"

    /** Node to Kyubey, then Kyubey to this phone; null while nothing is known. */
    fun pushPath(probe: PushProbe.NodeProbe, now: Long): String? {
        if (!probe.supported) return null
        val relay = when (probe.relayOk) {
            true -> "Kyubey 正常"
            false -> if (probe.relayFailures > 1) "Kyubey 不通（${probe.relayFailures} 次）" else "Kyubey 不通"
            null -> null
        }
        val phone = when {
            probe.testPending -> "测试推送中"
            probe.testLost -> "本机未收到测试推送"
            probe.reachedAt > 0L -> "本机已收到 · ${ago(now - probe.reachedAt)}"
            else -> null
        }
        return listOfNotNull(relay, phone).joinToString(" · ").ifEmpty { null }
    }

    /** The push state in a few characters for a node row. */
    fun pushShort(status: NodePushStatus): String = when (status.kind) {
        NodePushStatus.Kind.UNAVAILABLE -> "无推送"
        NodePushStatus.Kind.NO_ENDPOINT, NodePushStatus.Kind.PENDING -> "推送等待登记"
        NodePushStatus.Kind.RELAY_RETRYING -> "推送中继不可达"
        NodePushStatus.Kind.REGISTERED -> "推送已登记"
        NodePushStatus.Kind.UNSUPPORTED -> "节点不支持推送"
        NodePushStatus.Kind.REFUSED -> "推送被拒绝"
        NodePushStatus.Kind.RETRYING -> if (status.stillRegistered) "推送已登记（复查失败）" else "推送登记失败"
    }

    /** The push state with its timing, for the node detail dialog and the single-node tile. */
    fun pushDetail(status: NodePushStatus, now: Long): String {
        fun checked(verb: String) = status.checkedAt.takeIf { it > 0L }?.let { "（${ago(now - it)}$verb）" }.orEmpty()
        val next = status.nextAttemptAt.takeIf { it > now }?.let { "，${later(it - now)}再试" } ?: "，稍后再试"
        return when (status.kind) {
            NodePushStatus.Kind.UNAVAILABLE -> "此版本未启用推送唤醒"
            NodePushStatus.Kind.NO_ENDPOINT -> "正在向 FCM 和推送中继取得推送地址"
            NodePushStatus.Kind.RELAY_RETRYING -> "暂时连不上推送中继（连续 ${status.failures} 次）$next"
            NodePushStatus.Kind.PENDING -> "等待向该节点登记"
            NodePushStatus.Kind.REGISTERED -> "已登记，该节点可在后台唤醒此手机${checked("确认")}"
            NodePushStatus.Kind.UNSUPPORTED -> "该节点的服务器不支持推送，请升级 SoulGem${checked("检查")}"
            NodePushStatus.Kind.REFUSED -> "该节点拒绝了登记${checked("检查")}，一天后再试"
            NodePushStatus.Kind.RETRYING -> (if (status.stillRegistered) "已登记，但最近一次复查失败" else "登记失败") +
                "（连续 ${status.failures} 次，可能是节点或其到推送中继的网络问题）$next"
        }
    }

    private fun ago(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "$minutes 分钟前"
            minutes < 48 * 60 -> "${minutes / 60} 小时前"
            else -> "${minutes / (24 * 60)} 天前"
        }
    }

    private fun later(millis: Long): String {
        val minutes = (millis + 59_999L) / 60_000L
        return if (minutes < 60) "约 $minutes 分钟后" else "约 ${(minutes + 59) / 60} 小时后"
    }

    /** Label/value pairs for the node detail dialog. */
    fun details(
        node: HostNode,
        state: NodeHealth?,
        active: Boolean,
        now: Long,
        push: NodePushStatus? = null,
        probe: PushProbe.NodeProbe? = null,
        elapsedNow: Long? = null
    ): List<Pair<String, String>> = buildList {
        add("地址" to node.apiBaseUrl)
        add("角色" to if (active) "正在使用" else "备用")
        push?.let { add("推送" to pushDetail(it, now)) }
        probe?.let { pushPath(it, elapsedNow ?: android.os.SystemClock.elapsedRealtime()) }?.let { add("推送通道" to it) }
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

package com.callagent.host.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.callagent.host.ui.GatewayStatusPresenter.GatewayStatus
import com.callagent.host.ui.GatewayStatusPresenter.Tone
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.R as MaterialR

/**
 * Remote old-phone status. [compact] is a one-glance strip for the phone and
 * messages tabs (tap opens settings); the full card lists every SIM.
 */
class GatewayStatusView(
    context: Context,
    status: GatewayStatus,
    compact: Boolean,
    onClick: (() -> Unit)? = null
) : MaterialCardView(context) {
    init {
        radius = dp(20).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        setStrokeColor(android.content.res.ColorStateList.valueOf(color(toneOutline(status.tone))))
        setCardBackgroundColor(color(MaterialR.attr.colorSurfaceContainerLow))
        contentDescription = status.accessibilitySummary
        if (onClick != null) {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(if (compact) 10 else 14), dp(16), dp(if (compact) 10 else 14))
            importantForAccessibility = if (compact) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(toneColor(status.tone)))
            }
        }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
        header.addView(text(status.headline, MaterialR.style.TextAppearance_Material3_TitleSmall,
            MaterialR.attr.colorOnSurface).apply { if (!compact) ViewCompat.setAccessibilityHeading(this, true) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (compact) {
            status.sims.forEach { sim ->
                header.addView(text(sim.slotLabel.removePrefix("SIM ").let { "卡$it" },
                    MaterialR.style.TextAppearance_Material3_LabelSmall, toneColor(sim.tone)),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = dp(10); marginEnd = dp(4) })
                header.addView(SignalBarsView(context, sim.signalLevel, color(toneColor(sim.tone)),
                    color(MaterialR.attr.colorOutlineVariant)), LinearLayout.LayoutParams(dp(18), dp(14)))
            }
        }
        column.addView(header)
        if (status.detail.isNotBlank()) {
            column.addView(text(status.detail, MaterialR.style.TextAppearance_Material3_BodySmall,
                MaterialR.attr.colorOnSurfaceVariant).apply {
                if (compact) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                setPadding(dp(20), dp(2), 0, 0)
            })
        }
        if (!compact) {
            if (status.sims.isEmpty()) {
                column.addView(text("暂无已绑定的 SIM。请在旧手机上同步并确认 SIM。",
                    MaterialR.style.TextAppearance_Material3_BodyMedium, MaterialR.attr.colorOnSurfaceVariant)
                    .apply { setPadding(0, dp(10), 0, 0) })
            }
            status.sims.forEach { sim -> column.addView(simRow(sim)) }
        }
        addView(column)
    }

    private fun simRow(sim: GatewayStatusPresenter.SimStatus): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            setPadding(0, dp(8), 0, 0)
            contentDescription = buildString {
                append(sim.slotLabel).append(" ").append(sim.title)
                append(if (sim.signalLevel != null) "，信号 ${sim.signalLevel} 格" else "，信号未知")
                if (sim.detail.isNotBlank()) append("，").append(sim.detail)
            }
        }
        row.addView(text(sim.slotLabel, MaterialR.style.TextAppearance_Material3_LabelLarge,
            MaterialR.attr.colorOnSecondaryContainer).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(color(MaterialR.attr.colorSecondaryContainer))
            }
            setPadding(dp(8), dp(4), dp(8), dp(4))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = dp(12) })
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        texts.addView(text(sim.title, MaterialR.style.TextAppearance_Material3_BodyLarge, MaterialR.attr.colorOnSurface)
            .apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        if (sim.detail.isNotBlank()) {
            texts.addView(text(sim.detail, MaterialR.style.TextAppearance_Material3_BodySmall,
                if (sim.tone == Tone.ERROR) androidx.appcompat.R.attr.colorError else MaterialR.attr.colorOnSurfaceVariant))
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(SignalBarsView(context, sim.signalLevel, color(toneColor(sim.tone)),
            color(MaterialR.attr.colorOutlineVariant)).apply {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(24), dp(18)).apply { marginStart = dp(12) })
        return row
    }

    private fun text(value: String, appearance: Int, colorAttr: Int) = MaterialTextView(context).apply {
        text = value
        setTextAppearance(appearance)
        setTextColor(color(colorAttr))
    }

    private fun toneColor(tone: Tone) = when (tone) {
        Tone.OK -> androidx.appcompat.R.attr.colorPrimary
        Tone.WARNING -> MaterialR.attr.colorTertiary
        Tone.ERROR -> androidx.appcompat.R.attr.colorError
        Tone.UNKNOWN -> MaterialR.attr.colorOutline
    }

    private fun toneOutline(tone: Tone) = when (tone) {
        Tone.ERROR -> androidx.appcompat.R.attr.colorError
        else -> MaterialR.attr.colorOutlineVariant
    }

    private fun color(attr: Int): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/** Four ascending bars; `level` null draws all bars empty to mean "unknown". */
class SignalBarsView(context: Context, private val level: Int?, private val activeColor: Int, private val idleColor: Int) :
    View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bars = 4
        val gap = width * 0.08f
        val barWidth = (width - gap * (bars - 1)) / bars
        val radius = barWidth / 3f
        for (index in 0 until bars) {
            val barHeight = height * (index + 1) / bars.toFloat()
            val left = index * (barWidth + gap)
            rect.set(left, height - barHeight, left + barWidth, height.toFloat())
            paint.color = if (level != null && index < level) activeColor else idleColor
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

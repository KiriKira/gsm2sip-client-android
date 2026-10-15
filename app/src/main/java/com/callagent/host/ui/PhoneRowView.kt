package com.callagent.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.callagent.host.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.R as MaterialR

/**
 * A call-history tile in the style of Google Phone's "Recents": avatar,
 * name (red when missed), a direction arrow with SIM and time, and a
 * trailing call button.
 */
class PhoneRowView(
    context: Context,
    name: String,
    detail: String,
    missed: Boolean = false,
    direction: Direction = Direction.OUTGOING,
    avatarName: String? = null,
    avatarKey: String = name,
    position: HostUi.Position = HostUi.Position.SINGLE,
    onOpen: () -> Unit,
    onCall: () -> Unit = onOpen,
) : LinearLayout(context) {
    enum class Direction { INCOMING, OUTGOING, MISSED }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(72)
        setPadding(dp(16), dp(10), dp(8), dp(10))
        isClickable = true
        isFocusable = true
        background = HostUi.tileBackground(context, position)
        layoutParams = HostUi.tileParams(context, position)
        setOnClickListener { onOpen() }

        addView(HostUi.avatar(context, avatarName, avatarKey), LayoutParams(dp(40), dp(40)))

        val labels = LinearLayout(context).apply { orientation = VERTICAL }
        labels.addView(MaterialTextView(context).apply {
            text = name
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
            setTextColor(HostUi.color(context, if (missed) androidx.appcompat.R.attr.colorError else MaterialR.attr.colorOnSurface))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-1, -2))
        val detailRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        val detailColor = if (missed) androidx.appcompat.R.attr.colorError else MaterialR.attr.colorOnSurfaceVariant
        detailRow.addView(ImageView(context).apply {
            setImageResource(when (direction) {
                Direction.INCOMING -> R.drawable.ic_call_received
                Direction.OUTGOING -> R.drawable.ic_call_made
                Direction.MISSED -> R.drawable.ic_call_missed
            })
            imageTintList = ColorStateList.valueOf(HostUi.color(context, detailColor))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) })
        detailRow.addView(MaterialTextView(context).apply {
            text = detail
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(HostUi.color(context, detailColor))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, -2, 1f))
        labels.addView(detailRow, LayoutParams(-1, -2))
        addView(labels, LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })

        addView(MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.call_ui_phone)
            iconTint = ColorStateList.valueOf(HostUi.color(context, MaterialR.attr.colorOnSurfaceVariant))
            iconSize = dp(24)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription = "呼叫 $name"
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(HostUi.color(context, MaterialR.attr.colorOnSurface) and 0x1FFFFFFF)
            cornerRadius = dp(24)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onCall() }
        }, LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
    }

    private fun dp(value: Int) = HostUi.dp(context, value)
}

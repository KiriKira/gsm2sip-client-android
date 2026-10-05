package com.callagent.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.callagent.host.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView

/** A history or local-contact row with one clear call action. */
class PhoneRowView(
    context: Context,
    name: String,
    detail: String,
    missed: Boolean = false,
    onCall: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(84)
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        val selectable = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        if (selectable.resourceId != 0) background = ContextCompat.getDrawable(context, selectable.resourceId)
        setOnClickListener { onCall() }

        addView(ImageView(context).apply {
            setImageResource(R.drawable.call_ui_person)
            imageTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnPrimaryContainer))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color(com.google.android.material.R.attr.colorPrimaryContainer))
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(48), dp(48)))

        val labels = LinearLayout(context).apply { orientation = VERTICAL }
        labels.addView(MaterialTextView(context).apply {
            text = name
            textSize = 18f
            setTextColor(color(com.google.android.material.R.attr.colorOnSurface))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-1, -2))
        labels.addView(MaterialTextView(context).apply {
            text = detail
            textSize = 14f
            setTextColor(color(if (missed) androidx.appcompat.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, dp(4), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-1, -2))
        addView(labels, LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })

        addView(MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.call_ui_phone)
            iconTint = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            iconSize = dp(24)
            iconPadding = 0
            contentDescription = "呼叫 $name"
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onCall() }
        }, LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
    }

    private fun color(attribute: Int) = MaterialColors.getColor(context, attribute, 0)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

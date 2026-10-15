package com.callagent.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import com.google.android.material.button.MaterialButton
import com.google.android.material.R as MaterialR

/** Rounded tonal telephone key with its conventional letter hints, as on the Google dial pad. */
class DialPadKeyView(context: Context, digit: String) : MaterialButton(
    context, null, androidx.appcompat.R.attr.borderlessButtonStyle,
) {
    init {
        val letters = when (digit) {
            "2" -> "ABC"
            "3" -> "DEF"
            "4" -> "GHI"
            "5" -> "JKL"
            "6" -> "MNO"
            "7" -> "PQRS"
            "8" -> "TUV"
            "9" -> "WXYZ"
            "0" -> "+"
            else -> " "
        }
        val hintColor = HostUi.color(context, MaterialR.attr.colorOnSurfaceVariant)
        text = SpannableString("$digit\n$letters").apply {
            setSpan(RelativeSizeSpan(0.36f), digit.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(hintColor), digit.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 30f
        isAllCaps = false
        maxLines = 2
        includeFontPadding = false
        setLineSpacing(0f, 0.9f)
        backgroundTintList = ColorStateList.valueOf(HostUi.color(context, MaterialR.attr.colorSurfaceContainerHigh))
        rippleColor = ColorStateList.valueOf(HostUi.color(context, MaterialR.attr.colorOnSurface) and 0x1FFFFFFF)
        setTextColor(HostUi.color(context, MaterialR.attr.colorOnSurface))
        cornerRadius = dp(40)
        strokeWidth = 0
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = dp((64f * resources.configuration.fontScale.coerceAtLeast(1f)).toInt())
        setPadding(0, dp(4), 0, dp(4))
        contentDescription = digit
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

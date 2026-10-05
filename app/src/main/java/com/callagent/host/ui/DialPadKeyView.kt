package com.callagent.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/** Large, borderless telephone key with its conventional letter hints. */
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
        text = SpannableString("$digit\n$letters").apply {
            setSpan(RelativeSizeSpan(0.34f), digit.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 36f
        isAllCaps = false
        maxLines = 2
        includeFontPadding = false
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        setTextColor(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK))
        strokeWidth = 0
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = dp((80f * resources.configuration.fontScale.coerceAtLeast(1f)).toInt())
        setPadding(0, dp(6), 0, dp(6))
        contentDescription = digit
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

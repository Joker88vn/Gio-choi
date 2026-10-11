package vn.daopham.giochoi

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.label(s: CharSequence, sizeSp: Float = 16f, color: Int? = null, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        textSize = sizeSp
        if (color != null) setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }

fun Context.button(s: String, onClick: () -> Unit): Button = Button(this).apply {
    text = s
    isAllCaps = false
    setOnClickListener { onClick() }
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

fun LinearLayout.add(
    v: View,
    width: Int = MATCH,
    height: Int = WRAP,
    weight: Float = 0f,
    topDp: Int = 0,
): LinearLayout {
    val lp = LinearLayout.LayoutParams(width, height, weight)
    lp.topMargin = context.dp(topDp)
    addView(v, lp)
    return this
}

fun Context.appLabel(pkg: String): String = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    pkg
}

fun fmtDuration(ms: Long): String {
    val m = (ms.coerceAtLeast(0) / 60_000).toInt()
    return when {
        m >= 60 && m % 60 == 0 -> "${m / 60} giờ"
        m >= 60 -> "${m / 60} giờ ${m % 60} phút"
        else -> "$m phút"
    }
}

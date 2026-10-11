package vn.daopham.giochoi

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Bảng màu chung. */
object Pal {
    val BG = 0xFFF2F4F6.toInt()
    val CARD = 0xFFFFFFFF.toInt()
    val INK = 0xFF14212B.toInt()
    val SUB = 0xFF6A7782.toInt()
    val LINE = 0xFFE5E9EC.toInt()
    val PRI = 0xFF1D7A66.toInt()
    val PRI_SOFT = 0xFFE0F1EC.toInt()
    val WARN = 0xFFD9772A.toInt()
    val WARN_SOFT = 0xFFFCEDE0.toInt()
    val BAD = 0xFFCF4141.toInt()
    val BAD_SOFT = 0xFFFAE6E6.toInt()
    val NIGHT_TOP = 0xFF0A1A24.toInt()
    val NIGHT_BOTTOM = 0xFF0F3A37.toInt()
    val GLASS = 0x1FFFFFFF
    val GLASS_LINE = 0x2EFFFFFF
}

val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
val SANS_BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
val SANS_LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

fun Context.rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
}

fun Context.oval(color: Int, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    if (stroke != null) setStroke(dp(strokeDp), stroke)
}

fun ripple(content: Drawable, mask: Drawable, rippleColor: Int): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)

fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
    (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)

fun Context.label(
    s: CharSequence,
    sizeSp: Float = 16f,
    color: Int = Pal.INK,
    face: Typeface = SANS,
): TextView = TextView(this).apply {
    text = s
    textSize = sizeSp
    setTextColor(color)
    typeface = face
    setLineSpacing(0f, 1.15f)
}

fun TextView.centered(): TextView = apply { gravity = Gravity.CENTER }

enum class Btn { FILLED, SOFT, OUTLINE, GLASS }

private data class Look(val fill: Int, val stroke: Int?, val fg: Int, val ripple: Int)

/** Nút bo tròn kiểu viên thuốc. */
fun Context.pill(text: String, style: Btn = Btn.FILLED, accent: Int = Pal.PRI, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = SANS_MEDIUM
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(22), dp(10), dp(22), dp(10))
        isClickable = true
        isFocusable = true
        val r = 999
        val look = when (style) {
            Btn.FILLED -> Look(accent, null, Color.WHITE, 0x40FFFFFF)
            Btn.SOFT -> Look(mix(accent, Color.WHITE, 0.86f), null, accent, mix(accent, Color.WHITE, 0.6f))
            Btn.OUTLINE -> Look(Pal.CARD, Pal.LINE, Pal.INK, 0x14000000)
            Btn.GLASS -> Look(Pal.GLASS, Pal.GLASS_LINE, Color.WHITE, 0x33FFFFFF)
        }
        setTextColor(look.fg)
        background = ripple(rounded(look.fill, r, look.stroke), rounded(Color.WHITE, r), look.ripple)
        setOnClickListener { onClick() }
    }

/** Nút tròn nhỏ (dùng cho − / +). */
fun Context.roundIcon(symbol: String, onClick: () -> Unit): TextView = TextView(this).apply {
    text = symbol
    textSize = 22f
    typeface = SANS_MEDIUM
    gravity = Gravity.CENTER
    setTextColor(Pal.PRI)
    isClickable = true
    background = ripple(oval(Pal.PRI_SOFT), oval(Color.WHITE), mix(Pal.PRI, Color.WHITE, 0.6f))
    setOnClickListener { onClick() }
}

fun Context.chip(text: String, fg: Int, bg: Int): TextView = TextView(this).apply {
    this.text = text
    textSize = 13f
    typeface = SANS_MEDIUM
    setTextColor(fg)
    gravity = Gravity.CENTER
    setPadding(dp(12), dp(5), dp(12), dp(5))
    background = rounded(bg, 999)
}

fun Context.card(bg: Int = Pal.CARD, stroke: Int? = Pal.LINE): LinearLayout = vbox().apply {
    background = rounded(bg, 22, stroke)
    setPadding(dp(22), dp(20), dp(22), dp(20))
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.space(wDp: Int, hDp: Int): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(dp(wDp), dp(hDp))
}

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

fun Context.appIcon(pkg: String): Drawable? = try {
    packageManager.getApplicationIcon(pkg)
} catch (e: Exception) {
    null
}

fun fmtDuration(ms: Long): String {
    val m = (ms.coerceAtLeast(0) / 60_000).toInt()
    return when {
        m >= 60 && m % 60 == 0 -> "${m / 60} giờ"
        m >= 60 -> "${m / 60} giờ ${m % 60} phút"
        else -> "$m phút"
    }
}

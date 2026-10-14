package vn.daopham.codmaster

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

/** Bảng màu tối kiểu phòng thu. */
object Pal {
    val BG = 0xFF0D1015.toInt()
    val CARD = 0xFF161A21.toInt()
    val CARD2 = 0xFF1D222B.toInt()
    val INK = 0xFFECEFF3.toInt()
    val SUB = 0xFF8C96A4.toInt()
    val LINE = 0xFF272D37.toInt()
    val ACC = 0xFFF5A623.toInt()          // hổ phách
    val ACC_SOFT = 0xFF3A2C14.toInt()
    val ACC_INK = 0xFF1A1206.toInt()
    val TEAL = 0xFF3CC8B4.toInt()
    val WAVE_ORIG = 0xCC9AA4B2.toInt()
    val GOOD = 0xFF4CC38A.toInt()
    val BAD = 0xFFE5534B.toInt()
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

enum class Btn { FILLED, SOFT, OUTLINE }

private data class Look(val fill: Int, val stroke: Int?, val fg: Int, val ripple: Int)

/** Nút bo tròn kiểu viên thuốc. */
fun Context.pill(text: String, style: Btn = Btn.FILLED, accent: Int = Pal.ACC, onClick: () -> Unit): TextView =
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
            Btn.FILLED -> Look(accent, null, Pal.ACC_INK, 0x40FFFFFF)
            Btn.SOFT -> Look(mix(accent, Pal.CARD, 0.82f), null, accent, mix(accent, Pal.CARD, 0.5f))
            Btn.OUTLINE -> Look(Pal.CARD2, Pal.LINE, Pal.INK, 0x22FFFFFF)
        }
        setTextColor(look.fg)
        background = ripple(rounded(look.fill, r, look.stroke), rounded(Color.WHITE, r), look.ripple)
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
    background = rounded(bg, 20, stroke)
    setPadding(dp(18), dp(18), dp(18), dp(18))
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

fun fmtTime(frames: Int, sr: Int): String {
    val sec = if (sr > 0) frames / sr else 0
    return "%d:%02d".format(sec / 60, sec % 60)
}

fun fmtDb(v: Double, unit: String): String =
    if (v <= -99) "—" else String.format(java.util.Locale.US, "%.1f %s", v, unit).replace('-', '−')

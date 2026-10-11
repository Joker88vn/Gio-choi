package vn.daopham.giochoi

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Vòng tròn hiển thị thời gian đã dùng / còn lại. */
class RingView(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var value = ""
        set(v) { field = v; invalidate() }
    var caption = ""
        set(v) { field = v; invalidate() }
    var trackColor = Pal.LINE
    var progressColor = Pal.PRI
    var valueColor = Pal.INK
    var captionColor = Pal.SUB
    var strokeDp = 14

    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val sw = context.dp(strokeDp).toFloat()
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2f + sw / 2f
        val top = (height - size) / 2f + sw / 2f
        rect.set(left, top, left + size - sw, top + size - sw)
        arc.strokeWidth = sw
        arc.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, arc)
        if (progress > 0f) {
            arc.color = progressColor
            canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        }
        val cx = width / 2f
        val cy = height / 2f
        txt.typeface = SANS_BOLD
        txt.textSize = context.sp(if (value.length > 4) 26f else 34f)
        txt.color = valueColor
        canvas.drawText(value, cx, cy + txt.textSize * 0.18f, txt)
        txt.typeface = SANS
        txt.textSize = context.sp(13f)
        txt.color = captionColor
        canvas.drawText(caption, cx, cy + context.sp(30f), txt)
    }
}

/** Bàn phím số nhập mật khẩu, nút tròn. dark = dùng trên nền tối. */
class PinPad(ctx: Context, private val dark: Boolean, private val onSubmit: (String) -> Unit) : LinearLayout(ctx) {

    private val pin = StringBuilder()
    private val dots = LinearLayout(ctx)
    private val fg = if (dark) Color.WHITE else Pal.INK
    private val normalStatus = if (dark) 0xBFFFFFFF.toInt() else Pal.SUB
    val status: TextView = ctx.label("", 15f, normalStatus).centered()
    var locked = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(status, LayoutParams(MATCH, WRAP))

        dots.orientation = HORIZONTAL
        dots.gravity = Gravity.CENTER
        addView(dots, LayoutParams(MATCH, ctx.dp(24)).apply { topMargin = ctx.dp(14) })

        val grid = LinearLayout(ctx).apply { orientation = VERTICAL }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        for (r in rows) {
            val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
            for (k in r) row.addView(key(k))
            grid.addView(row, LayoutParams(WRAP, WRAP))
        }
        addView(grid, LayoutParams(WRAP, WRAP).apply { topMargin = ctx.dp(14) })
        renderDots()
    }

    private fun key(k: String): View {
        val c = context
        val tv = TextView(c).apply {
            text = k
            gravity = Gravity.CENTER
            isClickable = true
            when (k) {
                "⌫" -> { textSize = 24f; typeface = SANS }
                "✓" -> { textSize = 26f; typeface = SANS_BOLD }
                else -> { textSize = 30f; typeface = SANS_LIGHT }
            }
            setTextColor(if (k == "✓") Color.WHITE else fg)
            val fill = when {
                k == "✓" -> Pal.PRI
                dark -> Pal.GLASS
                else -> Pal.CARD
            }
            val stroke = when {
                k == "✓" -> null
                dark -> Pal.GLASS_LINE
                else -> Pal.LINE
            }
            val rip = if (dark) 0x40FFFFFF else mix(Pal.PRI, Color.WHITE, 0.75f)
            background = ripple(c.oval(fill, stroke), c.oval(Color.WHITE), rip)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                press(k)
            }
        }
        val s = c.dp(74)
        tv.layoutParams = LayoutParams(s, s).apply { setMargins(c.dp(10), c.dp(8), c.dp(10), c.dp(8)) }
        return tv
    }

    fun setStatus(text: String, error: Boolean = false) {
        status.text = text
        status.setTextColor(if (error) (if (dark) 0xFFFF9A8F.toInt() else Pal.BAD) else normalStatus)
    }

    fun shake() {
        ObjectAnimator.ofFloat(dots, "translationX", 0f, 22f, -22f, 14f, -14f, 6f, 0f)
            .setDuration(420).start()
    }

    private fun press(k: String) {
        if (locked) return
        when (k) {
            "⌫" -> if (pin.isNotEmpty()) pin.setLength(pin.length - 1)
            "✓" -> if (pin.isNotEmpty()) {
                val p = pin.toString()
                pin.setLength(0)
                renderDots()
                onSubmit(p)
                return
            }
            else -> if (pin.length < 12) pin.append(k)
        }
        renderDots()
    }

    private fun renderDots() {
        dots.removeAllViews()
        val n = maxOf(4, pin.length)
        val c = context
        for (i in 0 until n) {
            val filled = i < pin.length
            val d = View(c)
            d.background = if (filled) c.oval(fg) else c.oval(Color.TRANSPARENT, if (dark) 0x80FFFFFF.toInt() else Pal.SUB, 2)
            dots.addView(d, LayoutParams(c.dp(14), c.dp(14)).apply { setMargins(c.dp(7), 0, c.dp(7), 0) })
        }
    }
}

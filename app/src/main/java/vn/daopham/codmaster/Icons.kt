package vn.daopham.codmaster

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Biểu tượng vẽ tay bằng Path — sắc nét mọi độ phân giải, không phụ thuộc font emoji. */
class Icon(private val kind: Kind, private val color: Int) : Drawable() {
    enum class Kind { PLAY, PAUSE, SAVE, MUSIC, REPLAY, CLOSE }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@Icon.color; style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@Icon.color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val ox = b.left + (b.width() - s) / 2f
        val oy = b.top + (b.height() - s) / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        line.strokeWidth = s * 0.09f
        path.reset()
        when (kind) {
            Kind.PLAY -> {
                path.moveTo(x(0.30f), y(0.18f)); path.lineTo(x(0.84f), y(0.50f)); path.lineTo(x(0.30f), y(0.82f)); path.close()
                c.drawPath(path, fill)
            }
            Kind.PAUSE -> {
                c.drawRoundRect(x(0.24f), y(0.18f), x(0.42f), y(0.82f), s * 0.04f, s * 0.04f, fill)
                c.drawRoundRect(x(0.58f), y(0.18f), x(0.76f), y(0.82f), s * 0.04f, s * 0.04f, fill)
            }
            Kind.SAVE -> {
                path.moveTo(x(0.5f), y(0.14f)); path.lineTo(x(0.5f), y(0.62f))
                path.moveTo(x(0.30f), y(0.44f)); path.lineTo(x(0.5f), y(0.64f)); path.lineTo(x(0.70f), y(0.44f))
                path.moveTo(x(0.18f), y(0.70f)); path.lineTo(x(0.18f), y(0.84f)); path.lineTo(x(0.82f), y(0.84f)); path.lineTo(x(0.82f), y(0.70f))
                c.drawPath(path, line)
            }
            Kind.MUSIC -> {
                path.moveTo(x(0.40f), y(0.72f)); path.lineTo(x(0.40f), y(0.22f)); path.lineTo(x(0.80f), y(0.14f)); path.lineTo(x(0.80f), y(0.62f))
                c.drawPath(path, line)
                c.drawCircle(x(0.30f), y(0.74f), s * 0.12f, fill)
                c.drawCircle(x(0.70f), y(0.64f), s * 0.12f, fill)
            }
            Kind.REPLAY -> {
                path.addArc(x(0.18f), y(0.18f), x(0.82f), y(0.82f), -60f, 300f)
                c.drawPath(path, line)
                path.reset()
                path.moveTo(x(0.70f), y(0.12f)); path.lineTo(x(0.70f), y(0.32f)); path.lineTo(x(0.50f), y(0.32f))
                c.drawPath(path, line)
            }
            Kind.CLOSE -> {
                path.moveTo(x(0.25f), y(0.25f)); path.lineTo(x(0.75f), y(0.75f))
                path.moveTo(x(0.75f), y(0.25f)); path.lineTo(x(0.25f), y(0.75f))
                c.drawPath(path, line)
            }
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; line.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf; line.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

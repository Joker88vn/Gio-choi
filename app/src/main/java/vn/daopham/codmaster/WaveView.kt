package vn.daopham.codmaster

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Dạng sóng: bản gốc (xám) chồng lên bản master (màu nhấn) + vạch vị trí phát. Chạm để tua. */
class WaveView(ctx: Context) : View(ctx) {
    companion object { const val BUCKETS = 600 }

    private var orig: FloatArray? = null
    private var master: FloatArray? = null
    var progress = 0f
        set(v) { field = v; invalidate() }
    var onSeek: ((Float) -> Unit)? = null

    private val pOrig = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.WAVE_ORIG }
    private val pMaster = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.ACC }
    private val pHead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Pal.INK; strokeWidth = ctx.dp(2).toFloat() }
    private val pMid = Paint().apply { color = Pal.LINE; strokeWidth = 1f }
    private val pEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.SUB; textSize = ctx.sp(13f); textAlign = Paint.Align.CENTER
    }
    private val r = RectF()

    fun setData(o: FloatArray?, m: FloatArray?) {
        orig = o; master = m; invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2
        c.drawLine(0f, mid, w, mid, pMid)
        val o = orig
        if (o == null) {
            c.drawText("Chưa có bài nhạc", w / 2, mid + pEmpty.textSize / 3, pEmpty)
            return
        }
        val m = master
        val bw = w / BUCKETS
        val gap = if (bw > 3) 1f else 0f
        fun bars(arr: FloatArray, p: Paint) {
            for (i in 0 until BUCKETS) {
                val a = (arr[i] * (h * 0.48f)).coerceAtLeast(1f)
                r.set(i * bw, mid - a, (i + 1) * bw - gap, mid + a)
                c.drawRect(r, p)
            }
        }
        if (m != null) bars(m, pMaster)
        bars(o, pOrig)
        val x = progress.coerceIn(0f, 1f) * w
        c.drawLine(x, 0f, x, h, pHead)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (orig == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                progress = (e.x / width).coerceIn(0f, 1f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onSeek?.invoke((e.x / width).coerceIn(0f, 1f))
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}

object Peaks {
    fun ofShorts(l: ShortArray, r: ShortArray, n: Int): FloatArray {
        val out = FloatArray(WaveView.BUCKETS)
        for (b in 0 until WaveView.BUCKETS) {
            val s = (n.toLong() * b / WaveView.BUCKETS).toInt()
            val e = (n.toLong() * (b + 1) / WaveView.BUCKETS).toInt()
            var mx = 0
            var i = s
            while (i < e) {
                mx = max(mx, max(abs(l[i].toInt()), abs(r[i].toInt())))
                i++
            }
            out[b] = mx / 32768f
        }
        return out
    }

    fun ofFloats(l: FloatArray, r: FloatArray, n: Int): FloatArray {
        val out = FloatArray(WaveView.BUCKETS)
        for (b in 0 until WaveView.BUCKETS) {
            val s = (n.toLong() * b / WaveView.BUCKETS).toInt()
            val e = (n.toLong() * (b + 1) / WaveView.BUCKETS).toInt()
            var mx = 0f
            var i = s
            while (i < e) {
                mx = max(mx, max(abs(l[i]), abs(r[i])))
                i++
            }
            out[b] = mx.coerceAtMost(1f)
        }
        return out
    }
}

package vn.daopham.giochoi

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Màn hình khoá phủ lên trên mọi thứ, có bàn phím số riêng để bố mẹ nhập mật khẩu.
 */
class LockOverlay(private val svc: GuardService) {

    private val ctx: Context = ContextThemeWrapper(svc, android.R.style.Theme_DeviceDefault)
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var shownKey: String? = null
    private var wrong = 0

    fun show(reason: Reason) {
        if (root != null && shownKey == reason.key) return
        hide()
        val view = build(reason)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(view, lp)
            root = view
            shownKey = reason.key
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(250).start()
        } catch (_: Exception) { }
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        root = null
        shownKey = null
    }

    private fun build(reason: Reason): View {
        val c = ctx
        val white = Color.WHITE
        val soft = 0xC7FFFFFF.toInt()
        val name = reason.pkg?.let { c.appLabel(it) } ?: ""

        val (title, msg) = when (reason) {
            is Reason.Total -> "Hết giờ dùng máy rồi" to
                "Hôm nay con đã chơi đủ giờ. Nghỉ ngơi, mai mình chơi tiếp nhé!"
            is Reason.AppLimit -> "Hết giờ $name hôm nay" to
                "Con đã dùng $name đủ thời gian cho phép. Chọn việc khác nhé!"
            is Reason.Blocked -> "$name chưa được mở" to
                "Ứng dụng này cần bố mẹ cho phép."
            is Reason.Protected -> "Khu vực của bố mẹ" to
                "Phần cài đặt này cần mật khẩu của bố mẹ."
        }

        val col = c.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }

        // --- Biểu tượng ---
        val icon = if (reason.pkg != null && reason !is Reason.Protected) c.appIcon(reason.pkg) else null
        if (icon != null) {
            val frame = FrameLayout(c).apply {
                background = c.rounded(Pal.GLASS, 28, Pal.GLASS_LINE)
                setPadding(c.dp(16), c.dp(16), c.dp(16), c.dp(16))
                addView(ImageView(c).apply { setImageDrawable(icon) })
            }
            col.add(frame, c.dp(100), c.dp(100))
        } else {
            val emoji = if (reason is Reason.Protected) "🔒" else "🌙"
            col.add(c.label(emoji, 44f, white).centered().apply {
                background = c.oval(Pal.GLASS, Pal.GLASS_LINE)
            }, c.dp(104), c.dp(104))
        }

        col.add(c.label(title, 30f, white, SANS_BOLD).centered(), topDp = 22)
        col.add(c.label(msg, 17f, soft).centered(), topDp = 8)

        if (reason is Reason.Total) {
            col.add(c.chip("Hôm nay đã dùng ${fmtDuration(Store.totalUsedMs(c))}", white, Pal.GLASS), WRAP, topDp = 16)
        }

        // --- Bàn phím mật khẩu + lựa chọn sau khi mở ---
        val grants = c.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        lateinit var pad: PinPad
        pad = PinPad(c, dark = true) { pin ->
            if (Store.checkPassword(c, pin)) {
                wrong = 0
                pad.visibility = View.GONE
                grants.visibility = View.VISIBLE
            } else {
                wrong++
                pad.shake()
                if (wrong >= 5) {
                    wrong = 0
                    pad.locked = true
                    pad.setStatus("Sai 5 lần. Đợi 30 giây rồi thử lại.", error = true)
                    handler.postDelayed({
                        pad.locked = false
                        pad.setStatus("Bố mẹ nhập mật khẩu để mở")
                    }, 30_000)
                } else {
                    pad.setStatus("Sai mật khẩu, thử lại nhé", error = true)
                }
            }
        }
        pad.setStatus("Bố mẹ nhập mật khẩu để mở")
        col.add(pad, WRAP, topDp = 30)

        fun grant(min: Int) {
            Store.grant(c, reason, min)
            hide()
            svc.reevaluate()
        }

        grants.add(c.label("Đã mở khoá · Cho thêm thời gian", 15f, soft).centered())
        val options = if (reason is Reason.Protected)
            listOf("10 phút" to 10, "30 phút" to 30)
        else
            listOf("+15 phút" to 15, "+30 phút" to 30, "+1 giờ" to 60)
        val row = c.hbox().apply { gravity = Gravity.CENTER }
        for ((text, min) in options) {
            val b = c.pill(text, Btn.GLASS) { grant(min) }.apply { textSize = 18f; minHeight = c.dp(60) }
            row.addView(b, LinearLayout.LayoutParams(c.dp(140), WRAP).apply { setMargins(c.dp(6), 0, c.dp(6), 0) })
        }
        grants.add(row, WRAP, topDp = 16)
        grants.add(c.pill("Mở không giới hạn cả hôm nay", Btn.FILLED) { grant(-1) }.apply {
            textSize = 17f; minHeight = c.dp(58)
        }, MATCH, topDp = 12)
        grants.add(c.pill("Khoá lại", Btn.GLASS) {
            grants.visibility = View.GONE
            pad.visibility = View.VISIBLE
            pad.setStatus("Bố mẹ nhập mật khẩu để mở")
        }, WRAP, topDp = 12)
        col.add(grants, MATCH, topDp = 30)

        if (reason !is Reason.Total) {
            col.add(c.pill("Về màn hình chính", Btn.GLASS) { svc.goHome() }, WRAP, topDp = 28)
        }

        // --- Khung ngoài: nền gradient, nội dung ở giữa ---
        val maxW = minOf(svc.resources.displayMetrics.widthPixels - c.dp(48), c.dp(560))
        val frame = FrameLayout(c).apply {
            setPadding(0, c.dp(48), 0, c.dp(48))
            addView(col, FrameLayout.LayoutParams(maxW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        return ScrollView(c).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.NIGHT_TOP, Pal.NIGHT_BOTTOM))
            addView(frame)
        }
    }
}

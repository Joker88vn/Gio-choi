package vn.daopham.giochoi

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Màn hình khoá phủ lên trên mọi thứ. Có bàn phím số riêng để nhập mật khẩu
 * (không phụ thuộc bàn phím hệ thống).
 */
class LockOverlay(private val svc: GuardService) {

    private val ctx: Context = ContextThemeWrapper(svc, android.R.style.Theme_DeviceDefault)
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var shownKey: String? = null
    private var wrong = 0
    private var waitUntil = 0L

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
        } catch (_: Exception) { }
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        root = null
        shownKey = null
    }

    private fun build(reason: Reason): View {
        val c = ctx
        val name = reason.pkg?.let { c.appLabel(it) } ?: ""
        val (title, msg) = when (reason) {
            is Reason.Total -> "Hết giờ dùng máy hôm nay" to
                "Hôm nay con đã dùng ${fmtDuration(Store.totalUsedMs(c))}. Mai mình chơi tiếp nhé!"
            is Reason.AppLimit -> "Hết giờ dùng $name" to
                "Hôm nay con đã dùng $name đủ thời gian cho phép. Con chọn việc khác nhé!"
            is Reason.Blocked -> "$name đang bị chặn" to "Ứng dụng này cần bố mẹ cho phép."
            is Reason.Protected -> "Cần mật khẩu của bố mẹ" to "Phần này chỉ dành cho bố mẹ."
        }

        val white = Color.WHITE
        val soft = 0xFFB0BEC5.toInt()

        val col = c.vbox().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(c.dp(24), c.dp(32), c.dp(24), c.dp(32))
        }
        col.add(c.label(if (reason is Reason.Protected) "🔒" else "⏰", 56f).centered())
        col.add(c.label(title, 26f, white, bold = true).centered(), topDp = 8)
        col.add(c.label(msg, 17f, 0xFFCFD8DC.toInt()).centered(), topDp = 8)

        val status = c.label("Bố mẹ nhập mật khẩu để mở", 15f, soft).centered()
        val dots = c.label(" ", 30f, white).centered()
        col.add(status, topDp = 28)
        col.add(dots, topDp = 4)

        val pin = StringBuilder()
        val keypad = c.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val grants = c.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }

        fun press(k: String) {
            val now = System.currentTimeMillis()
            if (now < waitUntil) {
                status.text = "Sai quá nhiều lần. Đợi ${(waitUntil - now) / 1000 + 1} giây."
                return
            }
            when (k) {
                "⌫" -> if (pin.isNotEmpty()) pin.setLength(pin.length - 1)
                "OK" -> {
                    if (Store.checkPassword(c, pin.toString())) {
                        wrong = 0
                        keypad.visibility = View.GONE
                        dots.visibility = View.GONE
                        status.text = "Đã mở khoá. Bố mẹ chọn:"
                        grants.visibility = View.VISIBLE
                    } else {
                        wrong++
                        status.text = "Sai mật khẩu"
                        if (wrong >= 5) {
                            wrong = 0
                            waitUntil = now + 30_000
                            status.text = "Sai 5 lần. Đợi 30 giây rồi thử lại."
                        }
                    }
                    pin.setLength(0)
                }
                else -> if (pin.length < 12) pin.append(k)
            }
            dots.text = if (pin.isEmpty()) " " else "●".repeat(pin.length)
        }

        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        for (rowKeys in keys) {
            val row = c.hbox().apply { gravity = Gravity.CENTER }
            for (k in rowKeys) {
                val b = c.button(k) { press(k) }.apply { textSize = 22f }
                val lp = LinearLayout.LayoutParams(c.dp(88), c.dp(68))
                lp.setMargins(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
                row.addView(b, lp)
            }
            keypad.add(row, WRAP)
        }
        col.add(keypad, WRAP, topDp = 8)

        fun grant(min: Int) {
            Store.grant(c, reason, min)
            hide()
            svc.reevaluate()
        }

        val options: List<Pair<String, Int>> = if (reason is Reason.Protected)
            listOf("10 phút" to 10, "30 phút" to 30, "Cả hôm nay" to -1)
        else
            listOf("+15 phút" to 15, "+30 phút" to 30, "+1 giờ" to 60, "Cả hôm nay" to -1)

        val gRow = c.hbox().apply { gravity = Gravity.CENTER }
        for ((text, min) in options) {
            val b = c.button(text) { grant(min) }.apply { textSize = 18f }
            val lp = LinearLayout.LayoutParams(WRAP, c.dp(64))
            lp.setMargins(c.dp(6), c.dp(6), c.dp(6), c.dp(6))
            gRow.addView(b, lp)
        }
        grants.add(gRow, WRAP)
        grants.add(c.button("Khoá lại") {
            grants.visibility = View.GONE
            keypad.visibility = View.VISIBLE
            dots.visibility = View.VISIBLE
            status.text = "Bố mẹ nhập mật khẩu để mở"
        }, WRAP, topDp = 8)
        col.add(grants, WRAP, topDp = 8)

        if (reason !is Reason.Total) {
            col.add(c.button("Về màn hình chính") { svc.goHome() }.apply { textSize = 18f }, WRAP, topDp = 24)
        }

        return ScrollView(c).apply {
            isFillViewport = true
            setBackgroundColor(0xF5101820.toInt())
            addView(col.apply { gravity = Gravity.CENTER })
        }
    }
}

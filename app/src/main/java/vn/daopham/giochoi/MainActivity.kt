package vn.daopham.giochoi

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/** Màn hình dành cho bố mẹ. */
class MainActivity : Activity() {

    private enum class Screen { MAIN, CHANGE_PW }

    private var screen = Screen.MAIN
    private var authUntil = 0L
    private var firstPin: String? = null
    private var scroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    override fun onResume() {
        super.onResume()
        if (authUntil > System.currentTimeMillis()) authUntil = Long.MAX_VALUE
        render()
    }

    override fun onStop() {
        super.onStop()
        // Rời app quá 2 phút thì phải nhập lại mật khẩu
        if (authUntil == Long.MAX_VALUE) authUntil = System.currentTimeMillis() + 120_000
    }

    private fun render() {
        val saved = scroll?.scrollY ?: 0
        val content = when {
            !Store.hasPassword(this) -> pinFlowScreen(isChange = false)
            System.currentTimeMillis() >= authUntil -> loginScreen()
            screen == Screen.CHANGE_PW -> pinFlowScreen(isChange = true)
            else -> mainScreen()
        }
        val sv = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Pal.BG)
            addView(content)
        }
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, saved) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun screenW() = resources.displayMetrics.widthPixels

    // ================= Màn hình mật khẩu =================

    private fun centered(maxDp: Int, build: (LinearLayout) -> Unit): View {
        val col = vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        build(col)
        val maxW = minOf(screenW() - dp(48), dp(maxDp))
        return FrameLayout(this).apply {
            setPadding(0, dp(40), 0, dp(40))
            addView(col, FrameLayout.LayoutParams(maxW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }

    private fun logo(): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_fg)
        background = oval(Pal.PRI)
    }

    private fun pinFlowScreen(isChange: Boolean): View = centered(480) { col ->
        col.add(logo(), dp(88), dp(88))
        col.add(label(if (isChange) "Đổi mật khẩu" else "Chào mừng đến Giờ Chơi", 26f, Pal.INK, SANS_BOLD).centered(), topDp = 20)
        col.add(label(
            if (isChange) "Nhập mật khẩu mới gồm 4–12 chữ số."
            else "Tạo mật khẩu phụ huynh. Mật khẩu dùng để mở khoá máy khi hết giờ và để vào phần cài đặt này.",
            15f, Pal.SUB,
        ).centered(), topDp = 6)

        lateinit var pad: PinPad
        pad = PinPad(this, dark = false) { p ->
            val first = firstPin
            when {
                first == null && p.length < 4 -> {
                    pad.shake(); pad.setStatus("Mật khẩu cần ít nhất 4 chữ số", error = true)
                }
                first == null -> {
                    firstPin = p; pad.setStatus("Nhập lại lần nữa để xác nhận")
                }
                p == first -> {
                    Store.setPassword(this, p)
                    firstPin = null
                    authUntil = Long.MAX_VALUE
                    screen = Screen.MAIN
                    toast(if (isChange) "Đã đổi mật khẩu" else "Đã tạo mật khẩu")
                    render()
                }
                else -> {
                    firstPin = null
                    pad.shake(); pad.setStatus("Hai lần không khớp, nhập lại từ đầu", error = true)
                }
            }
        }
        pad.setStatus(if (firstPin == null) "Nhập mật khẩu mới" else "Nhập lại lần nữa để xác nhận")
        col.add(pad, WRAP, topDp = 28)
        if (isChange) {
            col.add(pill("Huỷ", Btn.OUTLINE) { firstPin = null; screen = Screen.MAIN; render() }, WRAP, topDp = 20)
        }
    }

    private fun loginScreen(): View = centered(480) { col ->
        col.add(logo(), dp(88), dp(88))
        col.add(label("Giờ Chơi", 28f, Pal.INK, SANS_BOLD).centered(), topDp = 20)
        col.add(label("Khu vực của bố mẹ", 15f, Pal.SUB).centered(), topDp = 4)
        lateinit var pad: PinPad
        pad = PinPad(this, dark = false) { p ->
            if (Store.checkPassword(this, p)) {
                authUntil = Long.MAX_VALUE
                screen = Screen.MAIN
                render()
            } else {
                pad.shake(); pad.setStatus("Sai mật khẩu", error = true)
            }
        }
        pad.setStatus("Nhập mật khẩu")
        col.add(pad, WRAP, topDp = 28)
    }

    // ================= Màn hình chính =================

    private fun isServiceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(this, GuardService::class.java)
        val names = setOf(cn.flattenToString(), cn.flattenToShortString())
        return s.split(':').any { item -> names.any { it.equals(item, ignoreCase = true) } }
    }

    private fun openA11y() {
        Store.allowTemp(this, Store.PROTECTED_PKGS, 5)
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun mainScreen(): View {
        val page = vbox()

        val header = hbox()
        val ht = vbox()
        ht.add(label("Giờ Chơi", 30f, Pal.INK, SANS_BOLD))
        ht.add(label("Bảng điều khiển của bố mẹ", 15f, Pal.SUB), topDp = 2)
        header.add(ht, 0, WRAP, 1f)
        val on = isServiceEnabled()
        header.add(
            if (on) chip("●  Đang bảo vệ", Pal.PRI, Pal.PRI_SOFT) else chip("●  Chưa bật", Pal.BAD, Pal.BAD_SOFT),
            WRAP,
        )
        page.add(header)

        if (!on) {
            page.add(alertCard(
                "Chưa bật bảo vệ",
                "Vào Trợ năng → Ứng dụng đã cài đặt → Giờ Chơi → Bật.",
                "Bật ngay", Pal.BAD, Pal.BAD_SOFT,
            ) { openA11y() }, topDp = 20)
        }
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            page.add(alertCard(
                "Giữ Giờ Chơi luôn chạy",
                "Cho phép không tối ưu pin để Samsung không tự tắt app.",
                "Cho phép", Pal.WARN, Pal.WARN_SOFT,
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) { toast("Anh vào Cài đặt → Pin để chỉnh") }
            }, topDp = 16)
        }

        page.add(todayCard(), topDp = 20)
        page.add(limitsCard(), topDp = 16)
        page.add(appsCard(), topDp = 16)
        page.add(protectCard(), topDp = 16)
        page.add(accountCard(), topDp = 16)
        page.add(label("Giờ Chơi 1.1", 12f, Pal.SUB).centered(), topDp = 24)

        val maxW = minOf(screenW() - dp(32), dp(760))
        return FrameLayout(this).apply {
            setPadding(0, dp(28), 0, dp(40))
            addView(page, FrameLayout.LayoutParams(maxW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        }
    }

    private fun cardTitle(card: LinearLayout, title: String, sub: String? = null) {
        card.add(label(title, 18f, Pal.INK, SANS_BOLD))
        if (sub != null) card.add(label(sub, 14f, Pal.SUB), topDp = 2)
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(Pal.LINE) }

    private fun alertCard(title: String, body: String, action: String, accent: Int, bg: Int, onClick: () -> Unit): View {
        val c = card(bg, null)
        val row = hbox()
        val t = vbox()
        t.add(label(title, 16f, accent, SANS_BOLD))
        t.add(label(body, 14f, Pal.INK), topDp = 2)
        row.add(t, 0, WRAP, 1f)
        row.addView(space(12, 1))
        row.add(pill(action, Btn.FILLED, accent, onClick), WRAP)
        c.add(row)
        return c
    }

    private fun statLine(name: String, value: String): View {
        val r = hbox()
        r.add(label(name, 15f, Pal.SUB), 0, WRAP, 1f)
        r.add(label(value, 16f, Pal.INK, SANS_MEDIUM), WRAP)
        return r
    }

    private fun todayCard(): View {
        val c = card()
        val row = hbox()
        val ring = RingView(this)
        val used = Store.totalUsedMs(this)
        val lim = Store.todayLimitMin(this)
        val free = Store.isFreeToday(this)
        var limitText = "Không giới hạn"
        when {
            free -> { ring.value = "∞"; ring.caption = "mở cả hôm nay"; limitText = "Mở cả hôm nay" }
            lim <= 0 -> { ring.value = "∞"; ring.caption = "không giới hạn" }
            else -> {
                val rem = Store.remainingTotalMs(this).coerceAtLeast(0)
                val frac = if (used + rem > 0) used.toFloat() / (used + rem) else 1f
                ring.progress = frac
                ring.progressColor = when {
                    frac < 0.75f -> Pal.PRI
                    frac < 1f -> Pal.WARN
                    else -> Pal.BAD
                }
                val m = rem / 60_000
                if (m >= 60) {
                    ring.value = "%d:%02d".format(m / 60, m % 60); ring.caption = "giờ còn lại"
                } else {
                    ring.value = "$m"; ring.caption = "phút còn lại"
                }
                limitText = fmtDuration(lim * 60_000L)
            }
        }
        row.add(ring, dp(176), dp(176))

        val right = vbox().apply { setPadding(dp(28), 0, 0, 0) }
        right.add(label("Hôm nay", 18f, Pal.INK, SANS_BOLD))
        right.add(statLine("Đã dùng", fmtDuration(used)), topDp = 12)
        right.add(statLine("Giới hạn", limitText), topDp = 8)
        val btns = hbox()
        btns.add(pill("+30 phút", Btn.SOFT) {
            Store.grant(this, Reason.Total, 30); toast("Đã thêm 30 phút cho hôm nay"); render()
        }, WRAP)
        btns.addView(space(10, 1))
        btns.add(pill("Đặt lại", Btn.OUTLINE) {
            AlertDialog.Builder(this)
                .setTitle("Đặt lại hôm nay?")
                .setMessage("Xoá thời gian đã dùng và các lần cho thêm giờ của hôm nay.")
                .setPositiveButton("Đặt lại") { _, _ -> Store.resetToday(this); render() }
                .setNegativeButton("Huỷ", null)
                .show()
        }, WRAP)
        right.add(btns, topDp = 18)
        row.add(right, 0, WRAP, 1f)
        c.add(row)
        return c
    }

    private fun stepper(title: String, value: Int, onChange: (Int) -> Unit): View {
        val row = hbox().apply { setPadding(0, dp(12), 0, dp(12)) }
        row.add(label(title, 16f, Pal.INK, SANS_MEDIUM), 0, WRAP, 1f)
        row.add(roundIcon("−") { onChange((value - 15).coerceAtLeast(0)) }, dp(44), dp(44))
        row.add(label(if (value == 0) "Không giới hạn" else fmtDuration(value * 60_000L), 16f, Pal.INK, SANS_MEDIUM).centered(), dp(150))
        row.add(roundIcon("+") { onChange((value + 15).coerceAtMost(720)) }, dp(44), dp(44))
        return row
    }

    private fun limitsCard(): View {
        val c = card()
        cardTitle(c, "Thời gian mỗi ngày", "Tổng thời gian được dùng máy. Mỗi lần bấm là 15 phút.")
        val wd = Store.weekdayLimit(this)
        val we = Store.weekendLimit(this)
        c.add(stepper("Thứ 2 – Thứ 6", wd) { v -> Store.setLimits(this, v, we); render() }, topDp = 8)
        c.add(divider(), MATCH, dp(1))
        c.add(stepper("Thứ 7 – Chủ nhật", we) { v -> Store.setLimits(this, wd, v); render() })
        return c
    }

    private data class AppInfo(val pkg: String, val name: String, val icon: Drawable?)

    private fun launcherApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedWith(compareBy<AppInfo>(
                { Store.rule(this, it.pkg).mode == MODE_ALLOW },
                { -Store.appUsedMs(this, it.pkg) },
                { it.name.lowercase() },
            ))
    }

    private fun appsCard(): View {
        val c = card()
        cardTitle(c, "Ứng dụng", "Chạm vào app để cho phép, giới hạn thời gian hoặc chặn.")
        val apps = launcherApps()
        apps.forEachIndexed { i, app ->
            if (i > 0) {
                val d = divider()
                c.addView(d, LinearLayout.LayoutParams(MATCH, dp(1)).apply { leftMargin = dp(62) })
            }
            c.add(appRow(app), topDp = if (i == 0) 8 else 0)
        }
        return c
    }

    private fun appRow(app: AppInfo): View {
        val r = Store.rule(this, app.pkg)
        val used = Store.appUsedMs(this, app.pkg)
        val sub = when (r.mode) {
            MODE_LIMIT -> "Hôm nay: ${fmtDuration(used)} / ${fmtDuration(r.minutes * 60_000L)}"
            MODE_BLOCK -> "Con không mở được app này"
            else -> if (used > 0) "Hôm nay: ${fmtDuration(used)}" else "Chưa dùng hôm nay"
        }
        val badge = when (r.mode) {
            MODE_LIMIT -> chip("${fmtDuration(r.minutes * 60_000L)}/ngày", Pal.WARN, Pal.WARN_SOFT)
            MODE_BLOCK -> chip("Chặn", Pal.BAD, Pal.BAD_SOFT)
            else -> chip("Cho phép", Pal.SUB, Pal.BG)
        }
        val row = hbox().apply {
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            background = ripple(rounded(Color.TRANSPARENT, 12), rounded(Color.WHITE, 12), 0x12000000)
            setOnClickListener { ruleDialog(app) }
        }
        row.add(ImageView(this).apply { setImageDrawable(app.icon) }, dp(46), dp(46))
        val t = vbox().apply { setPadding(dp(16), 0, dp(12), 0) }
        t.add(label(app.name, 16f, Pal.INK, SANS_MEDIUM))
        t.add(label(sub, 13f, Pal.SUB), topDp = 1)
        row.add(t, 0, WRAP, 1f)
        row.add(badge, WRAP)
        return row
    }

    private fun ruleDialog(app: AppInfo) {
        val presets = listOf(15, 30, 45, 60, 90, 120)
        val items = mutableListOf("Cho phép (không giới hạn riêng)")
        presets.forEach { items += "Giới hạn ${fmtDuration(it * 60_000L)} mỗi ngày" }
        items += "Số phút khác…"
        items += "Chặn hoàn toàn"
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> { Store.setRule(this, app.pkg, AppRule(MODE_ALLOW, 0)); render() }
                    which <= presets.size -> { Store.setRule(this, app.pkg, AppRule(MODE_LIMIT, presets[which - 1])); render() }
                    which == presets.size + 1 -> minutesDialog(app)
                    else -> { Store.setRule(this, app.pkg, AppRule(MODE_BLOCK, 0)); render() }
                }
            }
            .show()
    }

    private fun minutesDialog(app: AppInfo) {
        val cur = Store.rule(this, app.pkg)
        val f = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((if (cur.mode == MODE_LIMIT) cur.minutes else 30).toString())
        }
        val box = vbox().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        box.add(f)
        AlertDialog.Builder(this)
            .setTitle("${app.name}: số phút mỗi ngày")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val n = f.text.toString().toIntOrNull()
                if (n == null || n <= 0 || n > 1440) toast("Số phút không hợp lệ")
                else { Store.setRule(this, app.pkg, AppRule(MODE_LIMIT, n)); render() }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun protectCard(): View {
        val c = card()
        val row = hbox()
        val t = vbox()
        t.add(label("Khoá phần Cài đặt", 16f, Pal.INK, SANS_MEDIUM))
        t.add(label("Cần mật khẩu khi con mở Cài đặt hoặc cài/gỡ ứng dụng, để con không tắt được Giờ Chơi. Nên bật.", 13f, Pal.SUB), topDp = 2)
        row.add(t, 0, WRAP, 1f)
        val sw = Switch(this).apply {
            isChecked = Store.protectSettings(this@MainActivity)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(Pal.PRI, Color.WHITE))
            trackTintList = ColorStateList(states, intArrayOf(mix(Pal.PRI, Color.WHITE, 0.55f), Pal.LINE))
            setOnCheckedChangeListener { _, v -> Store.setProtect(this@MainActivity, v) }
        }
        row.addView(space(12, 1))
        row.add(sw, WRAP)
        c.add(row)
        return c
    }

    private fun accountCard(): View {
        val c = card()
        cardTitle(c, "Tài khoản phụ huynh")
        val row = hbox()
        row.add(pill("Đổi mật khẩu", Btn.OUTLINE) { firstPin = null; screen = Screen.CHANGE_PW; render() }, WRAP)
        row.addView(space(10, 1))
        row.add(pill("Thoát và khoá", Btn.FILLED) { authUntil = 0; finish() }, WRAP)
        c.add(row, topDp = 14)
        return c
    }
}

package vn.daopham.giochoi

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Màn hình dành cho bố mẹ: đặt mật khẩu, giới hạn thời gian, chặn app. */
class MainActivity : Activity() {

    private var authUntil = 0L
    private var scroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
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
            !Store.hasPassword(this) -> setupView()
            System.currentTimeMillis() >= authUntil -> loginView()
            else -> settingsView()
        }
        val sv = ScrollView(this).apply { addView(content) }
        scroll = sv
        setContentView(sv)
        sv.post { sv.scrollTo(0, saved) }
    }

    private fun page(): LinearLayout = vbox().apply { setPadding(dp(24), dp(24), dp(24), dp(48)) }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun numberField(value: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(value.toString())
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun validPin(s: String) = s.length in 4..12 && s.all { it.isDigit() }

    // ---------------- Đặt mật khẩu lần đầu ----------------
    private fun setupView(): View {
        val v = page()
        v.add(label("Giờ Chơi", 30f, bold = true))
        v.add(label("Đặt mật khẩu phụ huynh (4–12 chữ số). Mật khẩu này dùng để mở khoá máy khi hết giờ và để vào phần cài đặt này.", 16f), topDp = 8)
        val p1 = pinField("Mật khẩu")
        val p2 = pinField("Nhập lại mật khẩu")
        v.add(p1, topDp = 16)
        v.add(p2, topDp = 8)
        v.add(button("Lưu mật khẩu") {
            val a = p1.text.toString()
            when {
                !validPin(a) -> toast("Mật khẩu phải gồm 4–12 chữ số")
                a != p2.text.toString() -> toast("Hai lần nhập không khớp")
                else -> {
                    Store.setPassword(this, a)
                    authUntil = Long.MAX_VALUE
                    render()
                }
            }
        }, WRAP, topDp = 16)
        return v
    }

    // ---------------- Đăng nhập ----------------
    private fun loginView(): View {
        val v = page()
        v.add(label("Giờ Chơi", 30f, bold = true))
        v.add(label("Phần dành cho bố mẹ. Nhập mật khẩu để tiếp tục.", 16f), topDp = 8)
        val p = pinField("Mật khẩu")
        v.add(p, topDp = 16)
        v.add(button("Mở") {
            if (Store.checkPassword(this, p.text.toString())) {
                authUntil = Long.MAX_VALUE
                render()
            } else {
                p.setText("")
                toast("Sai mật khẩu")
            }
        }, WRAP, topDp = 12)
        return v
    }

    // ---------------- Cài đặt ----------------
    private fun section(v: LinearLayout, t: String) {
        v.add(label(t, 20f, 0xFF2E7D6B.toInt(), bold = true), topDp = 28)
    }

    private fun isServiceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val cn = ComponentName(this, GuardService::class.java)
        val names = setOf(cn.flattenToString(), cn.flattenToShortString())
        return s.split(':').any { item -> names.any { it.equals(item, ignoreCase = true) } }
    }

    private fun settingsView(): View {
        val v = page()
        v.add(label("Giờ Chơi", 30f, bold = true))

        // --- Trạng thái ---
        section(v, "Trạng thái")
        if (isServiceEnabled()) {
            v.add(label("✅ Đang bảo vệ", 17f, 0xFF2E7D32.toInt(), bold = true), topDp = 6)
        } else {
            v.add(label("⚠️ CHƯA BẬT. Bấm nút dưới, chọn “Ứng dụng đã cài đặt” → “Giờ Chơi” → Bật.", 17f, 0xFFC62828.toInt(), bold = true), topDp = 6)
        }
        v.add(button("Mở cài đặt Trợ năng") {
            Store.allowTemp(this, Store.PROTECTED_PKGS, 5)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, WRAP, topDp = 6)

        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
            v.add(button("Không tối ưu pin cho Giờ Chơi (nên bấm)") {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (_: Exception) { toast("Không mở được, anh vào Cài đặt → Pin để chỉnh") }
            }, WRAP, topDp = 4)
        }

        // --- Hôm nay ---
        section(v, "Hôm nay")
        val used = Store.totalUsedMs(this)
        val lim = Store.todayLimitMin(this)
        val todayText = when {
            Store.isFreeToday(this) -> "Đã dùng ${fmtDuration(used)} · Đang mở không giới hạn cả hôm nay"
            lim <= 0 -> "Đã dùng ${fmtDuration(used)} · Không giới hạn"
            else -> "Đã dùng ${fmtDuration(used)} / ${fmtDuration(lim * 60_000L)} · Còn lại ${fmtDuration(Store.remainingTotalMs(this))}"
        }
        v.add(label(todayText, 16f), topDp = 6)
        val todayRow = hbox()
        todayRow.add(button("+30 phút hôm nay") {
            Store.grant(this, Reason.Total, 30); toast("Đã thêm 30 phút"); render()
        }, WRAP)
        todayRow.add(button("Đặt lại hôm nay") {
            AlertDialog.Builder(this)
                .setMessage("Xoá toàn bộ thời gian đã dùng và các lần gia hạn của hôm nay?")
                .setPositiveButton("Đặt lại") { _, _ -> Store.resetToday(this); render() }
                .setNegativeButton("Huỷ", null)
                .show()
        }, WRAP)
        v.add(todayRow, topDp = 4)

        // --- Giới hạn ---
        section(v, "Tổng thời gian mỗi ngày")
        v.add(label("Tính bằng phút. Nhập 0 nếu không giới hạn.", 14f, 0xFF607D8B.toInt()), topDp = 4)
        val wd = numberField(Store.weekdayLimit(this))
        val we = numberField(Store.weekendLimit(this))
        val r1 = hbox(); r1.add(label("Thứ 2 – Thứ 6:", 16f), dp(160)); r1.add(wd, dp(120))
        val r2 = hbox(); r2.add(label("Thứ 7 – Chủ nhật:", 16f), dp(160)); r2.add(we, dp(120))
        v.add(r1, topDp = 6); v.add(r2)
        v.add(button("Lưu giới hạn") {
            val a = wd.text.toString().toIntOrNull()
            val b = we.text.toString().toIntOrNull()
            if (a == null || b == null || a < 0 || b < 0 || a > 1440 || b > 1440) toast("Số phút không hợp lệ")
            else { Store.setLimits(this, a, b); toast("Đã lưu"); render() }
        }, WRAP, topDp = 4)

        val cb = CheckBox(this).apply {
            text = "Khoá phần Cài đặt và cài/gỡ app bằng mật khẩu (nên bật, để con không tắt được Giờ Chơi)"
            isChecked = Store.protectSettings(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Store.setProtect(this@MainActivity, on) }
        }
        v.add(cb, topDp = 12)

        // --- Ứng dụng ---
        section(v, "Ứng dụng")
        v.add(label("Chạm vào một app để chọn: Cho phép · Giới hạn số phút mỗi ngày · Chặn hẳn.", 14f, 0xFF607D8B.toInt()), topDp = 4)
        for (app in launcherApps()) v.add(appRow(app), topDp = 2)

        // --- Khác ---
        section(v, "Khác")
        val other = hbox()
        other.add(button("Đổi mật khẩu") { changePasswordDialog() }, WRAP)
        other.add(button("Thoát và khoá") { authUntil = 0; finish() }, WRAP)
        v.add(other, topDp = 4)
        return v
    }

    private data class AppInfo(val pkg: String, val name: String, val icon: Drawable?)

    private fun launcherApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedWith(compareBy<AppInfo>({ Store.rule(this, it.pkg).mode == MODE_ALLOW }, { it.name.lowercase() }))
    }

    private fun appRow(app: AppInfo): View {
        val r = Store.rule(this, app.pkg)
        val used = fmtDuration(Store.appUsedMs(this, app.pkg))
        val (desc, color) = when (r.mode) {
            MODE_BLOCK -> "⛔ Chặn" to 0xFFC62828.toInt()
            MODE_LIMIT -> "⏳ Giới hạn ${fmtDuration(r.minutes * 60_000L)}/ngày · hôm nay đã dùng $used" to 0xFFE65100.toInt()
            else -> "Cho phép · hôm nay đã dùng $used" to 0xFF607D8B.toInt()
        }
        val row = hbox().apply {
            setPadding(0, dp(8), 0, dp(8))
            isClickable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { ruleDialog(app) }
        }
        row.add(ImageView(this).apply { setImageDrawable(app.icon) }, dp(44), dp(44))
        val info = vbox().apply { setPadding(dp(14), 0, 0, 0) }
        info.add(label(app.name, 17f, bold = true))
        info.add(label(desc, 14f, color))
        row.add(info, 0, WRAP, 1f)
        return row
    }

    private fun ruleDialog(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(arrayOf("Cho phép (không giới hạn riêng)", "Giới hạn số phút mỗi ngày…", "Chặn hoàn toàn")) { _, which ->
                when (which) {
                    0 -> { Store.setRule(this, app.pkg, AppRule(MODE_ALLOW, 0)); render() }
                    1 -> minutesDialog(app)
                    2 -> { Store.setRule(this, app.pkg, AppRule(MODE_BLOCK, 0)); render() }
                }
            }
            .show()
    }

    private fun minutesDialog(app: AppInfo) {
        val cur = Store.rule(this, app.pkg)
        val f = numberField(if (cur.mode == MODE_LIMIT) cur.minutes else 30)
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

    private fun changePasswordDialog() {
        val p1 = pinField("Mật khẩu mới")
        val p2 = pinField("Nhập lại")
        val box = vbox().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        box.add(p1); box.add(p2)
        AlertDialog.Builder(this)
            .setTitle("Đổi mật khẩu")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val a = p1.text.toString()
                when {
                    !validPin(a) -> toast("Mật khẩu phải gồm 4–12 chữ số")
                    a != p2.text.toString() -> toast("Hai lần nhập không khớp")
                    else -> { Store.setPassword(this, a); toast("Đã đổi mật khẩu") }
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}

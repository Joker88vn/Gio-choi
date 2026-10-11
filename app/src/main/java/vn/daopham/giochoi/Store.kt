package vn.daopham.giochoi

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val MODE_ALLOW = 0
const val MODE_LIMIT = 1
const val MODE_BLOCK = 2

data class AppRule(val mode: Int, val minutes: Int)

/** Lý do phải khoá màn hình. */
sealed class Reason(val pkg: String?) {
    object Total : Reason(null)                       // hết tổng thời gian trong ngày
    class AppLimit(pkg: String) : Reason(pkg)         // app hết thời gian riêng
    class Blocked(pkg: String) : Reason(pkg)          // app bị chặn
    class Protected(pkg: String) : Reason(pkg)        // Cài đặt / trình cài app

    val key: String get() = javaClass.simpleName + ":" + (pkg ?: "")
}

object Store {
    /** Các màn hình hệ thống cần mật khẩu (để con không tắt được app). */
    val PROTECTED_PKGS = setOf(
        "com.android.settings",
        "com.samsung.accessibility",
        "com.samsung.android.lool",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    private fun cfg(c: Context): SharedPreferences = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun st(c: Context): SharedPreferences = c.getSharedPreferences("state", Context.MODE_PRIVATE)

    private fun json(p: SharedPreferences, key: String): JSONObject =
        try { JSONObject(p.getString(key, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    // ---------- Mật khẩu ----------
    fun hasPassword(c: Context) = cfg(c).contains("pw_hash")

    fun setPassword(c: Context, pw: String) {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { "%02x".format(it) }
        cfg(c).edit().putString("pw_salt", salt).putString("pw_hash", sha(salt + pw)).apply()
    }

    fun checkPassword(c: Context, pw: String): Boolean {
        val p = cfg(c)
        val salt = p.getString("pw_salt", null) ?: return false
        return sha(salt + pw) == p.getString("pw_hash", null)
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---------- Cấu hình ----------
    fun weekdayLimit(c: Context) = cfg(c).getInt("lim_wd", 120)
    fun weekendLimit(c: Context) = cfg(c).getInt("lim_we", 180)
    fun setLimits(c: Context, weekday: Int, weekend: Int) =
        cfg(c).edit().putInt("lim_wd", weekday).putInt("lim_we", weekend).apply()

    fun protectSettings(c: Context) = cfg(c).getBoolean("protect", true)
    fun setProtect(c: Context, v: Boolean) = cfg(c).edit().putBoolean("protect", v).apply()

    fun todayLimitMin(c: Context): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        return if (weekend) weekendLimit(c) else weekdayLimit(c)
    }

    fun rule(c: Context, pkg: String): AppRule {
        val o = json(cfg(c), "rules").optJSONObject(pkg) ?: return AppRule(MODE_ALLOW, 0)
        return AppRule(o.optInt("m", MODE_ALLOW), o.optInt("min", 0))
    }

    fun setRule(c: Context, pkg: String, r: AppRule) {
        val p = cfg(c)
        val all = json(p, "rules")
        if (r.mode == MODE_ALLOW) all.remove(pkg)
        else all.put(pkg, JSONObject().put("m", r.mode).put("min", r.minutes))
        p.edit().putString("rules", all.toString()).apply()
    }

    // ---------- Trạng thái trong ngày ----------
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDay(c: Context) {
        val s = st(c)
        val t = today()
        if (s.getString("day", "") != t) s.edit().clear().putString("day", t).apply()
    }

    fun resetToday(c: Context) = st(c).edit().clear().putString("day", today()).apply()

    fun totalUsedMs(c: Context): Long { ensureDay(c); return st(c).getLong("total", 0) }

    fun appUsedMs(c: Context, pkg: String): Long { ensureDay(c); return json(st(c), "apps").optLong(pkg, 0) }

    fun addUsage(c: Context, pkg: String, ms: Long) {
        if (ms <= 0) return
        ensureDay(c)
        val s = st(c)
        val apps = json(s, "apps")
        apps.put(pkg, apps.optLong(pkg, 0) + ms)
        s.edit().putLong("total", s.getLong("total", 0) + ms).putString("apps", apps.toString()).apply()
    }

    fun isFreeToday(c: Context): Boolean { ensureDay(c); return st(c).getBoolean("free_day", false) }

    fun remainingTotalMs(c: Context): Long {
        val lim = todayLimitMin(c)
        if (lim <= 0) return Long.MAX_VALUE
        return lim * 60_000L + st(c).getLong("bonus_total", 0) - totalUsedMs(c)
    }

    fun remainingAppMs(c: Context, pkg: String): Long {
        val r = rule(c, pkg)
        if (r.mode != MODE_LIMIT) return Long.MAX_VALUE
        return r.minutes * 60_000L + json(st(c), "bonus_apps").optLong(pkg, 0) - appUsedMs(c, pkg)
    }

    private fun tempAllowed(c: Context, pkg: String) =
        json(st(c), "temp").optLong(pkg, 0) > System.currentTimeMillis()

    /** Trả về lý do khoá nếu app [pkg] đang ở màn hình mà không được phép dùng. */
    fun check(c: Context, pkg: String?): Reason? {
        ensureDay(c)
        if (pkg != null && pkg in PROTECTED_PKGS && protectSettings(c) && !tempAllowed(c, pkg))
            return Reason.Protected(pkg)
        if (isFreeToday(c)) return null
        if (remainingTotalMs(c) <= 0) return Reason.Total
        if (pkg == null || pkg == c.packageName || tempAllowed(c, pkg)) return null
        val r = rule(c, pkg)
        if (r.mode == MODE_BLOCK) return Reason.Blocked(pkg)
        if (r.mode == MODE_LIMIT && remainingAppMs(c, pkg) <= 0) return Reason.AppLimit(pkg)
        return null
    }

    private fun endOfDay(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
    }.timeInMillis

    /** Cho thêm thời gian sau khi nhập đúng mật khẩu. minutes < 0 nghĩa là "cả hôm nay". */
    fun grant(c: Context, reason: Reason, minutes: Int) {
        ensureDay(c)
        val s = st(c)
        val e = s.edit()
        val now = System.currentTimeMillis()
        val pkg = reason.pkg
        if (reason is Reason.Total) {
            if (minutes < 0) {
                e.putBoolean("free_day", true)
            } else {
                e.putLong("bonus_total", s.getLong("bonus_total", 0) + minutes * 60_000L)
            }
        } else if (pkg != null) {
            if (reason is Reason.AppLimit && minutes >= 0) {
                val b = json(s, "bonus_apps")
                val add: Long = minutes * 60_000L
                b.put(pkg, b.optLong(pkg, 0) + add)
                e.putString("bonus_apps", b.toString())
            } else {
                // App bị chặn / Cài đặt / app hết giờ "cả hôm nay": cho phép tạm đến một thời điểm
                val until: Long = if (minutes < 0) endOfDay() else now + minutes * 60_000L
                val t = json(s, "temp")
                t.put(pkg, until)
                e.putString("temp", t.toString())
            }
        }
        e.apply()
    }

    /** Tạm mở các app [pkgs] trong [minutes] phút (dùng khi bố mẹ bấm mở Cài đặt từ app). */
    fun allowTemp(c: Context, pkgs: Collection<String>, minutes: Int) {
        ensureDay(c)
        val s = st(c)
        val t = json(s, "temp")
        val until = System.currentTimeMillis() + minutes * 60_000L
        pkgs.forEach { t.put(it, until) }
        s.edit().putString("temp", t.toString()).apply()
    }
}

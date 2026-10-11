package vn.daopham.giochoi

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Dịch vụ Trợ năng: biết app nào đang mở, cộng dồn thời gian dùng,
 * và hiện màn hình khoá khi hết giờ / app bị chặn.
 */
class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastTick = SystemClock.elapsedRealtime()
    private var overlay: LockOverlay? = null
    private val ignored = HashSet<String>()
    private var warned5 = false
    private var warned1 = false
    private var receiverOn = false

    private val tick = object : Runnable {
        override fun run() {
            account()
            evaluate()
            handler.postDelayed(this, 5_000)
        }
    }

    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            account()
            lastTick = SystemClock.elapsedRealtime()
            evaluate()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = LockOverlay(this)
        ignored += packageName
        ignored += "com.android.systemui"
        try {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList?.forEach { ignored += it.packageName }
        } catch (_: Exception) { }

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenRx, f)
        receiverOn = true

        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        if (event.className?.toString()?.startsWith("android.widget.Toast") == true) return
        if (pkg != current) {
            account()
            current = pkg
        }
        evaluate()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (receiverOn) try { unregisterReceiver(screenRx) } catch (_: Exception) { }
        receiverOn = false
        overlay?.hide()
        super.onDestroy()
    }

    /** Máy đang sáng màn hình và đã mở khoá màn hình hệ thống. */
    private fun isActive(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return pm?.isInteractive == true && km?.isKeyguardLocked != true
    }

    private fun account() {
        val now = SystemClock.elapsedRealtime()
        // Giới hạn mỗi lần cộng tối đa 15 giây để không cộng nhầm lúc máy ngủ
        val delta = (now - lastTick).coerceIn(0L, 15_000L)
        lastTick = now
        val pkg = current ?: return
        if (isActive()) Store.addUsage(this, pkg, delta)
    }

    private fun evaluate() {
        if (!isActive()) return
        val reason = Store.check(this, current)
        if (reason != null) overlay?.show(reason) else overlay?.hide()
        warn()
    }

    private fun warn() {
        val rem = Store.remainingTotalMs(this)
        when {
            Store.isFreeToday(this) || rem > 5 * 60_000L -> { warned5 = false; warned1 = false }
            rem in 1..60_000L && !warned1 -> {
                warned1 = true; warned5 = true
                toast("Còn 1 phút nữa là hết giờ dùng máy hôm nay")
            }
            rem in 60_001L..300_000L && !warned5 -> {
                warned5 = true
                toast("Còn 5 phút nữa là hết giờ dùng máy hôm nay")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun reevaluate() {
        account()
        evaluate()
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}

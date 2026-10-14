package vn.daopham.codmaster

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import vn.daopham.codmaster.dsp.Mastering
import kotlin.math.pow

/** Trạng thái sống ngoài Activity (xoay màn hình / mở lại không mất bài). */
object App {
    var decoded: Decoded? = null
    var name: String = ""
    var settings: Mastering.Settings = Mastering.Settings().also { Presets.all[0].applyTo(it) }
    var presetIdx = 0
    var masterL: FloatArray? = null
    var masterR: FloatArray? = null
    var result: Mastering.Result? = null
    var masteredWith: Mastering.Settings? = null
    var origPeaks: FloatArray? = null
    var masterPeaks: FloatArray? = null
    var exportBits = 24
    var matchLevel = false
    var busy = false
    var onEnded: (() -> Unit)? = null
    val player = Player { onEnded?.invoke() }
}

class MainActivity : Activity() {
    companion object {
        private const val REQ_OPEN = 1
        private const val REQ_SAVE = 2
    }

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var cancel = false

    private lateinit var fileName: TextView
    private lateinit var fileInfo: TextView
    private lateinit var presetGrid: GridLayout
    private val presetViews = ArrayList<TextView>()
    private val paramBars = ArrayList<SeekBar>()
    private val paramVals = ArrayList<TextView>()
    private lateinit var masterBtn: TextView
    private lateinit var dirtyNote: TextView
    private lateinit var busyBox: LinearLayout
    private lateinit var busyBar: ProgressBar
    private lateinit var busyText: TextView
    private lateinit var wave: WaveView
    private lateinit var timeText: TextView
    private lateinit var playBtn: TextView
    private lateinit var abOrig: TextView
    private lateinit var abMaster: TextView
    private lateinit var matchSwitch: Switch
    private lateinit var stats: GridLayout
    private lateinit var bits24: TextView
    private lateinit var bits16: TextView
    private lateinit var saveBtn: TextView
    private val cardsNeedingFile = ArrayList<View>()
    private var suppressParam = false

    private val ticker = object : Runnable {
        override fun run() {
            updatePlayhead()
            ui.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.BG
        buildUi()
        App.onEnded = { ui.post { refreshPlayer() } }
        refreshAll()
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33)
                i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> i.data
            else -> null
        }
        if (uri != null) loadFile(uri)
    }

    // ───────────────────────────── giao diện ─────────────────────────────

    private fun buildUi() {
        val root = vbox().apply { setPadding(dp(16), dp(20), dp(16), dp(36)) }

        // Đầu trang
        val head = hbox()
        val logo = TextView(this).apply {
            text = "◉"
            textSize = 22f
            setTextColor(Pal.ACC_INK)
            gravity = Gravity.CENTER
            background = oval(Pal.ACC)
        }
        head.add(logo, dp(44), dp(44))
        val titles = vbox().apply { setPadding(dp(14), 0, 0, 0) }
        titles.add(label("COD Master", 22f, Pal.INK, SANS_BOLD))
        titles.add(label("Master âm thanh chất lượng cao ngay trên máy", 13f, Pal.SUB))
        head.add(titles, 0, WRAP, 1f)
        root.add(head)

        // 1. Bài nhạc
        val c1 = card()
        c1.add(sectionTitle("1", "Bài nhạc"))
        fileName = label("Chưa chọn bài", 16f, Pal.INK, SANS_MEDIUM)
        fileInfo = label("MP3, M4A, WAV, FLAC, OGG… · có thể chia sẻ file từ app khác vào đây", 13f, Pal.SUB)
        c1.add(fileName, topDp = 12)
        c1.add(fileInfo, topDp = 4)
        c1.add(pill("Chọn file nhạc") { pickFile() }, topDp = 14)
        root.add(c1, topDp = 18)

        // 2. Phong cách
        val c2 = card()
        c2.add(sectionTitle("2", "Phong cách"))
        presetGrid = GridLayout(this).apply { columnCount = 2 }
        Presets.all.forEachIndexed { i, p ->
            val t = TextView(this).apply {
                text = "${p.name}\n${p.desc}"
                textSize = 13.5f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setLineSpacing(0f, 1.1f)
                isClickable = true
                setOnClickListener { choosePreset(i) }
            }
            presetViews.add(t)
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f)
            ).apply {
                width = 0
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            presetGrid.addView(t, lp)
        }
        c2.add(presetGrid, topDp = 10)
        root.add(c2, topDp = 14)

        // 3. Tinh chỉnh
        val c3 = card()
        c3.add(sectionTitle("3", "Tinh chỉnh"))
        Params.all.forEachIndexed { idx, prm ->
            val row = hbox()
            row.add(label(prm.title, 15f, Pal.INK, SANS_MEDIUM), 0, WRAP, 1f)
            val v = label("", 14f, Pal.ACC, SANS_MEDIUM)
            paramVals.add(v)
            row.add(v, WRAP, WRAP)
            c3.add(row, topDp = if (idx == 0) 10 else 16)
            c3.add(label(prm.hint, 12f, Pal.SUB), topDp = 1)
            val sb = SeekBar(this).apply {
                max = prm.steps
                progressTintList = ColorStateList.valueOf(Pal.ACC)
                thumbTintList = ColorStateList.valueOf(Pal.ACC)
                progressBackgroundTintList = ColorStateList.valueOf(Pal.LINE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                        val value = prm.fromIndex(p)
                        paramVals[idx].text = prm.fmt(value)
                        if (fromUser && !suppressParam) {
                            prm.setter(App.settings, value)
                            App.presetIdx = -1
                            refreshPresets()
                            refreshDirty()
                        }
                    }
                    override fun onStartTrackingTouch(s: SeekBar) {}
                    override fun onStopTrackingTouch(s: SeekBar) {}
                })
            }
            paramBars.add(sb)
            c3.add(sb)
        }
        root.add(c3, topDp = 14)

        // Nút master + tiến trình
        dirtyNote = label("", 13f, Pal.ACC).centered()
        root.add(dirtyNote, topDp = 16)
        masterBtn = pill("MASTER NGAY") { startMaster() }.apply {
            textSize = 17f
            typeface = SANS_BOLD
            minHeight = dp(56)
        }
        root.add(masterBtn, topDp = 8)
        busyBox = vbox().apply { visibility = View.GONE }
        busyText = label("", 14f, Pal.INK)
        busyBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Pal.ACC)
            progressBackgroundTintList = ColorStateList.valueOf(Pal.LINE)
        }
        busyBox.add(busyText)
        busyBox.add(busyBar, topDp = 8)
        busyBox.add(pill("Huỷ", Btn.OUTLINE) { cancel = true }, topDp = 10)
        root.add(busyBox, topDp = 10)

        // 4. Nghe & so sánh
        val c4 = card()
        c4.add(sectionTitle("4", "Nghe & so sánh"))
        wave = WaveView(this).apply {
            onSeek = { f ->
                val p = App.player
                p.seek((f * p.frames).toInt())
                updatePlayhead()
            }
        }
        c4.add(wave, MATCH, dp(110), topDp = 12)
        val trow = hbox()
        timeText = label("0:00 / 0:00", 13f, Pal.SUB)
        trow.add(timeText, 0, WRAP, 1f)
        val legend = label("▮ Gốc   ", 12f, Pal.WAVE_ORIG)
        val legend2 = label("▮ Master", 12f, Pal.ACC)
        trow.add(legend, WRAP, WRAP)
        trow.add(legend2, WRAP, WRAP)
        c4.add(trow, topDp = 6)

        val prow = hbox()
        playBtn = pill("▶  Phát") { togglePlay() }
        prow.add(playBtn, 0, WRAP, 1f)
        prow.add(space(10, 1), WRAP, WRAP)
        abOrig = pill("Gốc", Btn.OUTLINE) { setAB(false) }
        abMaster = pill("Master", Btn.OUTLINE) { setAB(true) }
        prow.add(abOrig, 0, WRAP, 0.8f)
        prow.add(space(6, 1), WRAP, WRAP)
        prow.add(abMaster, 0, WRAP, 0.9f)
        c4.add(prow, topDp = 12)

        val mrow = hbox()
        val mtexts = vbox()
        mtexts.add(label("Cân âm lượng khi so sánh", 14f, Pal.INK, SANS_MEDIUM))
        mtexts.add(label("Hạ bản master bằng độ lớn bản gốc để nghe công bằng — nghe khác biệt về màu tiếng, không bị lừa vì to hơn", 12f, Pal.SUB))
        mrow.add(mtexts, 0, WRAP, 1f)
        matchSwitch = Switch(this).apply {
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Pal.ACC, Pal.SUB)
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Pal.ACC_SOFT, Pal.LINE)
            )
            setOnCheckedChangeListener { _, b ->
                App.matchLevel = b
                applyMatch()
            }
        }
        mrow.add(matchSwitch, WRAP, WRAP)
        c4.add(mrow, topDp = 14)

        stats = GridLayout(this).apply { columnCount = 3 }
        c4.add(stats, topDp = 14)
        root.add(c4, topDp = 14)
        cardsNeedingFile.add(c4)

        // 5. Xuất file
        val c5 = card()
        c5.add(sectionTitle("5", "Xuất file"))
        val brow = hbox()
        bits24 = pill("WAV 24-bit", Btn.OUTLINE) { App.exportBits = 24; refreshExport() }
        bits16 = pill("WAV 16-bit", Btn.OUTLINE) { App.exportBits = 16; refreshExport() }
        brow.add(bits24, 0, WRAP, 1f)
        brow.add(space(8, 1), WRAP, WRAP)
        brow.add(bits16, 0, WRAP, 1f)
        c5.add(brow, topDp = 12)
        c5.add(label("24-bit: chất lượng cao nhất, để lưu trữ / dựng video.  16-bit: tương thích mọi nơi, có dither chống mất chi tiết.", 12f, Pal.SUB), topDp = 8)
        saveBtn = pill("Lưu file master") { saveFile() }
        c5.add(saveBtn, topDp = 14)
        root.add(c5, topDp = 14)
        cardsNeedingFile.add(c5)

        root.add(label("Chuỗi xử lý: lọc siêu trầm → EQ → nén glue → bão hoà → stereo → limiter true-peak, đo theo chuẩn EBU R128 / ITU BS.1770.", 11.5f, Pal.SUB).centered(), topDp = 18)

        val sv = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            isFillViewport = true
            addView(root)
        }
        setContentView(sv)
    }

    private fun sectionTitle(num: String, title: String): View {
        val r = hbox()
        val n = TextView(this).apply {
            text = num
            textSize = 12f
            typeface = SANS_BOLD
            gravity = Gravity.CENTER
            setTextColor(Pal.ACC)
            background = oval(Pal.ACC_SOFT)
        }
        r.add(n, dp(24), dp(24))
        r.add(space(10, 1), WRAP, WRAP)
        r.add(label(title, 17f, Pal.INK, SANS_BOLD), 0, WRAP, 1f)
        return r
    }

    // ───────────────────────────── làm mới giao diện ─────────────────────────────

    private fun refreshAll() {
        refreshFile()
        refreshPresets()
        refreshParams()
        refreshDirty()
        refreshPlayer()
        refreshStats()
        refreshExport()
        wave.setData(App.origPeaks, App.masterPeaks)
        matchSwitch.isChecked = App.matchLevel
        setBusy(App.busy, null, 0f)
    }

    private fun refreshFile() {
        val d = App.decoded
        if (d == null) {
            fileName.text = "Chưa chọn bài"
        } else {
            fileName.text = App.name
            val chs = if (d.srcChannels == 1) "Mono" else "Stereo"
            val khz = String.format(java.util.Locale.US, "%.1f kHz", d.sampleRate / 1000.0)
            fileInfo.text = "$khz · $chs · ${fmtTime(d.frames, d.sampleRate)}"
        }
        val has = d != null
        for (v in cardsNeedingFile) v.alpha = if (has) 1f else 0.45f
    }

    private fun refreshPresets() {
        presetViews.forEachIndexed { i, t ->
            val sel = i == App.presetIdx
            t.setTextColor(if (sel) Pal.ACC_INK else Pal.INK)
            t.typeface = if (sel) SANS_MEDIUM else SANS
            t.background = ripple(
                rounded(if (sel) Pal.ACC else Pal.CARD2, 14, if (sel) null else Pal.LINE),
                rounded(Color.WHITE, 14), 0x30FFFFFF
            )
        }
    }

    private fun refreshParams() {
        suppressParam = true
        Params.all.forEachIndexed { i, p ->
            val v = p.getter(App.settings)
            paramBars[i].progress = p.toIndex(v)
            paramVals[i].text = p.fmt(v)
        }
        suppressParam = false
    }

    private fun settingsChanged(): Boolean {
        val a = App.masteredWith ?: return true
        val b = App.settings
        return a.targetLufs != b.targetLufs || a.ceilingDb != b.ceilingDb || a.bassDb != b.bassDb ||
            a.mudDb != b.mudDb || a.presenceDb != b.presenceDb || a.airDb != b.airDb ||
            a.warmth != b.warmth || a.comp != b.comp || a.width != b.width
    }

    private fun refreshDirty() {
        val has = App.decoded != null
        val mastered = App.masterL != null
        val changed = mastered && settingsChanged()
        dirtyNote.text = when {
            !has -> "Chọn bài nhạc để bắt đầu"
            changed -> "Cài đặt đã đổi — bấm để master lại"
            mastered -> "Đã master xong ✓"
            else -> ""
        }
        dirtyNote.visibility = if (dirtyNote.text.isEmpty()) View.GONE else View.VISIBLE
        masterBtn.text = if (mastered) "MASTER LẠI" else "MASTER NGAY"
        masterBtn.alpha = if (has && !App.busy) 1f else 0.4f
        masterBtn.isEnabled = has && !App.busy
    }

    private fun refreshPlayer() {
        val p = App.player
        val has = App.decoded != null
        playBtn.text = if (p.isPlaying) "❚❚  Dừng" else "▶  Phát"
        playBtn.isEnabled = has
        val m = p.useMaster && App.masterL != null
        styleToggle(abOrig, !m)
        styleToggle(abMaster, m)
        abMaster.isEnabled = App.masterL != null
        abMaster.alpha = if (App.masterL != null) 1f else 0.4f
        updatePlayhead()
    }

    private fun styleToggle(t: TextView, on: Boolean) {
        t.setTextColor(if (on) Pal.ACC else Pal.INK)
        t.background = ripple(
            rounded(if (on) Pal.ACC_SOFT else Pal.CARD2, 999, if (on) Pal.ACC else Pal.LINE),
            rounded(Color.WHITE, 999), 0x30FFFFFF
        )
    }

    private fun refreshExport() {
        styleToggle(bits24, App.exportBits == 24)
        styleToggle(bits16, App.exportBits == 16)
        val ok = App.masterL != null && !App.busy
        saveBtn.isEnabled = ok
        saveBtn.alpha = if (ok) 1f else 0.4f
    }

    private fun refreshStats() {
        stats.removeAllViews()
        val r = App.result ?: run {
            stats.visibility = View.GONE
            return
        }
        stats.visibility = View.VISIBLE
        fun cell(s: String, color: Int, bold: Boolean, col: Int, row: Int, alignEnd: Boolean) {
            val t = label(s, 13.5f, color, if (bold) SANS_MEDIUM else SANS)
            if (alignEnd) t.gravity = Gravity.END
            val lp = GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(col, if (col == 0) 1.4f else 1f)).apply {
                width = 0
                setMargins(0, dp(3), 0, dp(3))
            }
            stats.addView(t, lp)
        }
        cell("", Pal.SUB, false, 0, 0, false)
        cell("Gốc", Pal.SUB, true, 1, 0, true)
        cell("Master", Pal.ACC, true, 2, 0, true)
        cell("Độ lớn", Pal.INK, false, 0, 1, false)
        cell(fmtDb(r.inLufs, "LUFS"), Pal.INK, false, 1, 1, true)
        cell(fmtDb(r.outLufs, "LUFS"), Pal.INK, true, 2, 1, true)
        cell("Đỉnh thật", Pal.INK, false, 0, 2, false)
        cell(fmtDb(r.inTpDb, "dBTP"), if (r.inTpDb > 0) Pal.BAD else Pal.INK, false, 1, 2, true)
        cell(fmtDb(r.outTpDb, "dBTP"), Pal.INK, true, 2, 2, true)
        cell("Limiter nén tối đa", Pal.INK, false, 0, 3, false)
        cell("", Pal.INK, false, 1, 3, true)
        cell(fmtDb(r.limiterGrDb, "dB"), Pal.INK, true, 2, 3, true)
    }

    private fun updatePlayhead() {
        val p = App.player
        val n = p.frames
        if (n <= 0) {
            timeText.text = "0:00 / 0:00"
            return
        }
        val pos = p.position()
        wave.progress = pos / n.toFloat()
        timeText.text = "${fmtTime(pos, p.sampleRate)} / ${fmtTime(n, p.sampleRate)}"
        val playing = p.isPlaying
        val want = if (playing) "❚❚  Dừng" else "▶  Phát"
        if (playBtn.text != want) playBtn.text = want
    }

    private fun setBusy(on: Boolean, text: String?, f: Float) {
        App.busy = on
        busyBox.visibility = if (on) View.VISIBLE else View.GONE
        if (text != null) busyText.text = text
        busyBar.progress = (f * 1000).toInt()
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshDirty()
        refreshExport()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ───────────────────────────── hành động ─────────────────────────────

    private fun choosePreset(i: Int) {
        App.presetIdx = i
        val s = App.settings
        Presets.all[i].applyTo(s)
        refreshPresets()
        refreshParams()
        refreshDirty()
    }

    private fun pickFile() {
        if (App.busy) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg", "video/mp4"))
        }
        try {
            startActivityForResult(i, REQ_OPEN)
        } catch (e: Exception) {
            toast("Không mở được trình chọn file")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_OPEN -> loadFile(uri)
            REQ_SAVE -> writeFile(uri)
        }
    }

    private fun loadFile(uri: Uri) {
        if (App.busy) {
            toast("Đang xử lý, đợi chút nhé")
            return
        }
        App.player.setSource(null)
        App.decoded = null
        App.masterL = null; App.masterR = null
        App.result = null; App.masteredWith = null
        App.origPeaks = null; App.masterPeaks = null
        wave.setData(null, null)
        refreshAll()
        System.gc()
        cancel = false
        val name = AudioIO.displayName(this, uri)
        setBusy(true, "Đang đọc “$name”…", 0f)
        Thread {
            try {
                var last = 0L
                val d = AudioIO.decode(this, uri) { f ->
                    val now = System.currentTimeMillis()
                    if (now - last > 100) {
                        last = now
                        ui.post { if (App.busy) busyBar.progress = (f * 1000).toInt() }
                    }
                    !cancel
                }
                val peaks = Peaks.ofShorts(d.left, d.right, d.frames)
                ui.post {
                    App.decoded = d
                    App.name = name
                    App.origPeaks = peaks
                    App.player.setSource(d)
                    App.player.useMaster = true
                    setBusy(false, null, 0f)
                    wave.setData(peaks, null)
                    refreshAll()
                }
            } catch (e: CancelledException) {
                ui.post { setBusy(false, null, 0f); refreshAll() }
            } catch (e: OutOfMemoryError) {
                ui.post { setBusy(false, null, 0f); refreshAll(); toast("Bài quá dài so với bộ nhớ máy") }
            } catch (e: Exception) {
                ui.post { setBusy(false, null, 0f); refreshAll(); toast(e.message ?: "Lỗi đọc file") }
            }
        }.start()
    }

    private fun startMaster() {
        val d = App.decoded ?: return
        if (App.busy) return
        val p = App.player
        p.pause()
        // giải phóng bản master cũ trước khi cấp bộ nhớ mới
        p.setMaster(null, null)
        App.masterL = null; App.masterR = null
        App.masterPeaks = null
        wave.setData(App.origPeaks, null)
        System.gc()
        cancel = false
        val settings = App.settings.copy()
        setBusy(true, "Chuẩn bị…", 0f)
        refreshPlayer()
        Thread {
            try {
                val n = d.frames
                val l = FloatArray(n)
                val r = FloatArray(n)
                val sc = 1f / 32768f
                val dl = d.left
                val dr = d.right
                for (i in 0 until n) {
                    l[i] = dl[i] * sc
                    r[i] = dr[i] * sc
                }
                var last = 0L
                val t0 = System.currentTimeMillis()
                val res = Mastering.process(l, r, n, d.sampleRate.toDouble(), settings) { f, stage ->
                    val now = System.currentTimeMillis()
                    if (now - last > 100) {
                        last = now
                        val pct = (f * 100).toInt()
                        ui.post {
                            if (App.busy) {
                                busyText.text = "$stage… $pct%"
                                busyBar.progress = (f * 1000).toInt()
                            }
                        }
                    }
                    !cancel
                }
                val peaks = Peaks.ofFloats(l, r, n)
                val secs = (System.currentTimeMillis() - t0) / 1000.0
                ui.post {
                    App.masterL = l
                    App.masterR = r
                    App.result = res
                    App.masteredWith = settings
                    App.masterPeaks = peaks
                    p.setMaster(l, r)
                    p.useMaster = true
                    applyMatch()
                    setBusy(false, null, 1f)
                    wave.setData(App.origPeaks, peaks)
                    refreshAll()
                    toast(String.format(java.util.Locale.US, "Xong trong %.0f giây — bấm Phát để nghe", secs))
                }
            } catch (e: Mastering.CancelledException) {
                ui.post { setBusy(false, null, 0f); refreshAll() }
            } catch (e: OutOfMemoryError) {
                ui.post { setBusy(false, null, 0f); refreshAll(); toast("Không đủ bộ nhớ để xử lý bài này") }
            } catch (e: Exception) {
                ui.post { setBusy(false, null, 0f); refreshAll(); toast(e.message ?: "Lỗi khi master") }
            }
        }.start()
    }

    private fun applyMatch() {
        val r = App.result
        App.player.masterGain = if (App.matchLevel && r != null && r.inLufs > -60)
            10.0.pow((r.inLufs - r.outLufs) / 20.0).toFloat().coerceAtMost(1f)
        else 1f
    }

    private fun togglePlay() {
        val p = App.player
        if (App.decoded == null || App.busy) return
        if (p.isPlaying) p.pause() else p.play()
        refreshPlayer()
    }

    private fun setAB(master: Boolean) {
        if (master && App.masterL == null) return
        App.player.useMaster = master
        refreshPlayer()
    }

    private fun saveFile() {
        if (App.masterL == null || App.busy) return
        val base = App.name.substringBeforeLast('.').ifBlank { "bai-nhac" }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "$base (Master).wav")
        }
        try {
            startActivityForResult(i, REQ_SAVE)
        } catch (e: Exception) {
            toast("Không mở được trình lưu file")
        }
    }

    private fun writeFile(uri: Uri) {
        val l = App.masterL ?: return
        val r = App.masterR ?: return
        val d = App.decoded ?: return
        val bits = App.exportBits
        setBusy(true, "Đang lưu WAV $bits-bit…", 0f)
        Thread {
            try {
                val os = contentResolver.openOutputStream(uri, "w") ?: throw Exception("Không ghi được file")
                os.use {
                    var last = 0L
                    AudioIO.writeWav(it, l, r, d.frames, d.sampleRate, bits) { f ->
                        val now = System.currentTimeMillis()
                        if (now - last > 100) {
                            last = now
                            ui.post { busyBar.progress = (f * 1000).toInt() }
                        }
                    }
                }
                ui.post {
                    setBusy(false, null, 0f)
                    toast("Đã lưu file master ✓")
                }
            } catch (e: Exception) {
                ui.post {
                    setBusy(false, null, 0f)
                    toast("Lưu thất bại: ${e.message}")
                }
            }
        }.start()
    }
}

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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
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

    // track
    private lateinit var fileName: TextView
    private lateinit var fileInfo: TextView
    private lateinit var pickBtn: TextView
    // monitor
    private lateinit var wave: WaveView
    private lateinit var timeText: TextView
    private lateinit var nowText: TextView
    private lateinit var abOrig: TextView
    private lateinit var abMaster: TextView
    private lateinit var matchChip: TextView
    private val playButtons = ArrayList<ImageView>()
    private val tileVal = ArrayList<TextView>()
    private val tileSub = ArrayList<TextView>()
    // style / tune
    private val presetViews = ArrayList<TextView>()
    private val paramBars = ArrayList<SeekBar>()
    private val paramVals = ArrayList<TextView>()
    private var suppressParam = false
    // export
    private lateinit var bits24: TextView
    private lateinit var bits16: TextView
    // bottom bar
    private lateinit var dirtyNote: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var masterBtn: TextView
    private lateinit var saveBtn: ImageView
    private lateinit var busyBox: LinearLayout
    private lateinit var busyBar: ProgressBar
    private lateinit var busyText: TextView
    private val needFile = ArrayList<View>()

    private val ticker = object : Runnable {
        override fun run() {
            updatePlayhead()
            ui.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Pal.BG
        window.navigationBarColor = Pal.CARD
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

    // ───────────────────────────── thành phần giao diện ─────────────────────────────

    private fun caps(s: String, color: Int = Pal.SUB): TextView = label(s.uppercase(), 11f, color, SANS_MEDIUM).apply {
        letterSpacing = 0.12f
    }

    private fun compactCard(): LinearLayout = vbox().apply {
        background = rounded(Pal.CARD, 18, Pal.LINE)
        setPadding(dp(14), dp(14), dp(14), dp(14))
    }

    private fun iconButton(kind: Icon.Kind, sizeDp: Int, fill: Int, fg: Int, stroke: Int?, onClick: () -> Unit) =
        ImageView(this).apply {
            setImageDrawable(Icon(kind, fg))
            val pad = (sizeDp * 0.28f).toInt()
            setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
            background = ripple(oval(fill, stroke), oval(Color.WHITE), 0x40FFFFFF)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** Nút gạt hai lựa chọn kiểu phòng thu. */
    private fun segmented(a: String, b: String, onA: () -> Unit, onB: () -> Unit): Triple<LinearLayout, TextView, TextView> {
        val box = hbox().apply {
            background = rounded(Pal.CARD2, 999, Pal.LINE)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        fun seg(t: String, f: () -> Unit) = TextView(this).apply {
            text = t
            textSize = 12.5f
            typeface = SANS_MEDIUM
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            isClickable = true
            setOnClickListener { f() }
        }
        val ta = seg(a, onA)
        val tb = seg(b, onB)
        box.add(ta, WRAP, WRAP)
        box.add(tb, WRAP, WRAP)
        return Triple(box, ta, tb)
    }

    private fun styleSeg(t: TextView, on: Boolean) {
        t.setTextColor(if (on) Pal.ACC_INK else Pal.SUB)
        t.background = if (on) rounded(Pal.ACC, 999) else null
    }

    private fun tile(title: String): LinearLayout {
        val v = vbox().apply {
            background = rounded(Pal.CARD2, 14)
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }
        v.add(caps(title).apply { textSize = 10f })
        val value = label("—", 17f, Pal.INK, SANS_BOLD).apply { maxLines = 1 }
        val sub = label("", 11f, Pal.SUB).apply { maxLines = 1 }
        v.add(value, topDp = 3)
        v.add(sub, topDp = 1)
        tileVal.add(value)
        tileSub.add(sub)
        return v
    }

    // ───────────────────────────── dựng giao diện ─────────────────────────────

    private fun buildUi() {
        val content = vbox().apply { setPadding(dp(14), dp(12), dp(14), dp(18)) }

        // Thanh tiêu đề
        val top = hbox()
        val logo = ImageView(this).apply {
            setImageDrawable(Icon(Icon.Kind.MUSIC, Pal.ACC_INK))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Pal.ACC, 11)
        }
        top.add(logo, dp(38), dp(38))
        top.gapW(10)
        val tt = vbox()
        tt.add(label("COD Master", 18f, Pal.INK, SANS_BOLD))
        tt.add(caps("Mastering studio", Pal.ACC).apply { textSize = 10f })
        top.add(tt, 0, WRAP, 1f)
        top.add(chip("EBU R128 · True Peak", Pal.SUB, Pal.CARD2).apply { textSize = 10.5f }, WRAP, WRAP)
        content.add(top)

        // Bài nhạc
        val track = compactCard()
        val trow = hbox()
        val note = ImageView(this).apply {
            setImageDrawable(Icon(Icon.Kind.MUSIC, Pal.ACC))
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(Pal.ACC_SOFT, 12)
        }
        trow.add(note, dp(46), dp(46))
        trow.gapW(12)
        val tinfo = vbox()
        fileName = label("Chưa chọn bài", 15f, Pal.INK, SANS_MEDIUM).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        fileInfo = label("MP3 · M4A · WAV · FLAC · OGG", 12f, Pal.SUB).apply { maxLines = 1 }
        tinfo.add(fileName)
        tinfo.add(fileInfo, topDp = 2)
        trow.add(tinfo, 0, WRAP, 1f)
        trow.gapW(8)
        pickBtn = pill("Chọn bài", Btn.SOFT) { pickFile() }.apply {
            textSize = 13f; minHeight = dp(38); setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        trow.add(pickBtn, WRAP, WRAP)
        track.add(trow)
        content.add(track, topDp = 14)

        // Monitor: sóng âm + phát + A/B + số đo
        val mon = compactCard()
        val mhead = hbox()
        mhead.add(caps("Nghe thử"), 0, WRAP, 1f)
        val seg = segmented("GỐC", "MASTER", { setAB(false) }, { setAB(true) })
        abOrig = seg.second
        abMaster = seg.third
        mhead.add(seg.first, WRAP, WRAP)
        mon.add(mhead)

        wave = WaveView(this).apply {
            onSeek = { f ->
                val p = App.player
                p.seek((f * p.frames).toInt())
                updatePlayhead()
            }
        }
        mon.add(wave, MATCH, dp(92), topDp = 12)

        val tline = hbox()
        timeText = label("0:00 / 0:00", 11.5f, Pal.SUB)
        tline.add(timeText, 0, WRAP, 1f)
        tline.add(label("▮ Gốc", 11f, Pal.WAVE_ORIG), WRAP, WRAP)
        tline.gapW(10)
        tline.add(label("▮ Master", 11f, Pal.ACC), WRAP, WRAP)
        mon.add(tline, topDp = 4)

        val transport = hbox()
        val play1 = iconButton(Icon.Kind.PLAY, 54, Pal.ACC, Pal.ACC_INK, null) { togglePlay() }
        playButtons.add(play1)
        transport.add(play1, dp(54), dp(54))
        transport.gapW(12)
        val nowBox = vbox()
        nowText = label("Chưa có bài", 14f, Pal.INK, SANS_MEDIUM).apply { maxLines = 1 }
        nowBox.add(nowText)
        nowBox.add(label("Chạm vào sóng âm để tua", 11.5f, Pal.SUB), topDp = 1)
        transport.add(nowBox, 0, WRAP, 1f)
        matchChip = TextView(this).apply {
            text = "Cân âm lượng"
            textSize = 12f
            typeface = SANS_MEDIUM
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            isClickable = true
            setOnClickListener {
                App.matchLevel = !App.matchLevel
                applyMatch()
                refreshPlayer()
                if (App.matchLevel) toast("Bản master được hạ bằng độ lớn bản gốc — so sánh màu tiếng công bằng")
            }
        }
        transport.add(matchChip, WRAP, WRAP)
        mon.add(transport, topDp = 12)

        val tiles = hbox()
        tiles.add(tile("Độ lớn"), 0, WRAP, 1f)
        tiles.gapW(8)
        tiles.add(tile("Đỉnh thật"), 0, WRAP, 1f)
        tiles.gapW(8)
        tiles.add(tile("Limiter"), 0, WRAP, 1f)
        mon.add(tiles, topDp = 12)
        content.add(mon, topDp = 10)
        needFile.add(mon)

        // Phong cách: hàng chip vuốt ngang
        content.add(caps("Phong cách"), topDp = 18)
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val chips = hbox()
        Presets.all.forEachIndexed { i, p ->
            val t = TextView(this).apply {
                text = "${p.name}\n${p.desc}"
                textSize = 12.5f
                setLineSpacing(0f, 1.1f)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                isClickable = true
                setOnClickListener { choosePreset(i) }
            }
            presetViews.add(t)
            chips.add(t, WRAP, WRAP)
            if (i < Presets.all.size - 1) chips.gapW(8)
        }
        hs.addView(chips)
        content.add(hs, topDp = 8)

        // Tinh chỉnh: mỗi thông số một dòng
        val tune = compactCard()
        val thead = hbox()
        thead.add(caps("Tinh chỉnh"), 0, WRAP, 1f)
        thead.add(label("Chạm tên để xem giải thích", 11f, Pal.SUB), WRAP, WRAP)
        tune.add(thead)
        Params.all.forEachIndexed { idx, prm ->
            val row = hbox()
            val name = label(prm.title, 12.5f, Pal.INK).apply {
                maxLines = 2
                isClickable = true
                setOnClickListener { toast("${prm.title}: ${prm.hint}") }
            }
            row.add(name, dp(96), WRAP)
            val sb = SeekBar(this).apply {
                max = prm.steps
                progressTintList = ColorStateList.valueOf(Pal.ACC)
                thumbTintList = ColorStateList.valueOf(Pal.ACC)
                progressBackgroundTintList = ColorStateList.valueOf(Pal.LINE)
                setPadding(dp(12), dp(10), dp(12), dp(10))
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
            row.add(sb, 0, WRAP, 1f)
            val v = label("", 12f, Pal.ACC, SANS_MEDIUM).apply { gravity = Gravity.END; maxLines = 1 }
            paramVals.add(v)
            row.add(v, dp(74), WRAP)
            tune.add(row, topDp = if (idx == 0) 8 else 2)
        }
        content.add(tune, topDp = 14)

        // Xuất file
        val exp = compactCard()
        val ehead = hbox()
        val etx = vbox()
        etx.add(caps("Xuất file WAV"))
        etx.add(label("24-bit: lưu trữ, dựng video · 16-bit: có dither", 11.5f, Pal.SUB), topDp = 3)
        ehead.add(etx, 0, WRAP, 1f)
        val bseg = segmented("24-bit", "16-bit",
            { App.exportBits = 24; refreshExport() }, { App.exportBits = 16; refreshExport() })
        bits24 = bseg.second
        bits16 = bseg.third
        ehead.add(bseg.first, WRAP, WRAP)
        exp.add(ehead)
        content.add(exp, topDp = 10)
        needFile.add(exp)

        content.add(label("Lọc siêu trầm → EQ → nén glue → bão hoà → stereo M/S → limiter true-peak", 10.5f, Pal.SUB).centered(), topDp = 14)

        val sv = ScrollView(this).apply {
            setBackgroundColor(Pal.BG)
            addView(content)
        }

        // Thanh điều khiển cố định ở đáy
        val bar = vbox().apply {
            background = rounded(Pal.CARD, 0, null)
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val topLine = View(this).apply { setBackgroundColor(Pal.LINE) }
        dirtyNote = label("", 11.5f, Pal.SUB).centered()
        bar.add(dirtyNote)
        actionRow = hbox()
        val play2 = iconButton(Icon.Kind.PLAY, 52, Pal.CARD2, Pal.ACC, Pal.ACC) { togglePlay() }
        playButtons.add(play2)
        actionRow.add(play2, dp(52), dp(52))
        actionRow.gapW(10)
        masterBtn = pill("MASTER") { startMaster() }.apply {
            textSize = 16f
            typeface = SANS_BOLD
            letterSpacing = 0.08f
            minHeight = dp(52)
        }
        actionRow.add(masterBtn, 0, dp(52), 1f)
        actionRow.gapW(10)
        saveBtn = iconButton(Icon.Kind.SAVE, 52, Pal.CARD2, Pal.INK, Pal.LINE) { saveFile() }
        actionRow.add(saveBtn, dp(52), dp(52))
        bar.add(actionRow, topDp = 6)

        busyBox = vbox().apply { visibility = View.GONE }
        val brow = hbox()
        busyText = label("", 13.5f, Pal.INK, SANS_MEDIUM).apply { maxLines = 1 }
        brow.add(busyText, 0, WRAP, 1f)
        brow.add(iconButton(Icon.Kind.CLOSE, 34, Pal.CARD2, Pal.SUB, Pal.LINE) { cancel = true }, dp(34), dp(34))
        busyBox.add(brow)
        busyBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Pal.ACC)
            progressBackgroundTintList = ColorStateList.valueOf(Pal.LINE)
        }
        busyBox.add(busyBar, topDp = 8)
        bar.add(busyBox, topDp = 6)

        val root = vbox().apply { setBackgroundColor(Pal.BG) }
        root.add(sv, MATCH, 0, 1f)
        root.add(topLine, MATCH, 1)
        root.add(bar)
        setContentView(root)
    }

    // ───────────────────────────── làm mới giao diện ─────────────────────────────

    private fun refreshAll() {
        refreshFile()
        refreshPresets()
        refreshParams()
        refreshPlayer()
        refreshStats()
        refreshExport()
        wave.setData(App.origPeaks, App.masterPeaks)
        setBusy(App.busy, null, 0f)
    }

    private fun refreshFile() {
        val d = App.decoded
        if (d == null) {
            fileName.text = "Chưa chọn bài"
            fileInfo.text = "MP3 · M4A · WAV · FLAC · OGG"
            pickBtn.text = "Chọn bài"
        } else {
            fileName.text = App.name
            val chs = if (d.srcChannels == 1) "Mono" else "Stereo"
            val khz = String.format(java.util.Locale.US, "%.1f kHz", d.sampleRate / 1000.0)
            fileInfo.text = "$khz · $chs · ${fmtTime(d.frames, d.sampleRate)}"
            pickBtn.text = "Đổi bài"
        }
        for (v in needFile) v.alpha = if (d != null) 1f else 0.5f
    }

    private fun refreshPresets() {
        presetViews.forEachIndexed { i, t ->
            val sel = i == App.presetIdx
            t.setTextColor(if (sel) Pal.ACC_INK else Pal.INK)
            t.typeface = if (sel) SANS_MEDIUM else SANS
            t.background = ripple(
                rounded(if (sel) Pal.ACC else Pal.CARD, 14, if (sel) null else Pal.LINE),
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
            changed -> "Cài đặt đã đổi — bấm MASTER LẠI để áp dụng"
            mastered -> "✓ Đã master · bấm ▶ để nghe, ⤓ để lưu"
            else -> "Chọn phong cách rồi bấm MASTER"
        }
        dirtyNote.setTextColor(if (changed) Pal.ACC else Pal.SUB)
        masterBtn.text = if (mastered) "MASTER LẠI" else "MASTER"
        masterBtn.alpha = if (has && !App.busy) 1f else 0.4f
        masterBtn.isEnabled = has && !App.busy
    }

    private fun refreshPlayer() {
        val p = App.player
        val has = App.decoded != null
        val playing = p.isPlaying
        for (b in playButtons) {
            val main = b === playButtons[0]
            b.setImageDrawable(Icon(if (playing) Icon.Kind.PAUSE else Icon.Kind.PLAY, if (main) Pal.ACC_INK else Pal.ACC))
            b.isEnabled = has
            b.alpha = if (has) 1f else 0.4f
        }
        val m = p.useMaster && App.masterL != null
        styleSeg(abOrig, !m)
        styleSeg(abMaster, m)
        abMaster.alpha = if (App.masterL != null) 1f else 0.4f
        nowText.text = when {
            !has -> "Chưa có bài"
            m -> if (App.matchLevel) "Đang nghe: Master (đã cân)" else "Đang nghe: Master"
            else -> "Đang nghe: Bản gốc"
        }
        val on = App.matchLevel
        matchChip.setTextColor(if (on) Pal.ACC else Pal.SUB)
        matchChip.background = rounded(if (on) Pal.ACC_SOFT else Pal.CARD2, 999, if (on) Pal.ACC else Pal.LINE)
        updatePlayhead()
    }

    private fun refreshExport() {
        styleSeg(bits24, App.exportBits == 24)
        styleSeg(bits16, App.exportBits == 16)
        val ok = App.masterL != null && !App.busy
        saveBtn.isEnabled = ok
        saveBtn.alpha = if (ok) 1f else 0.4f
        saveBtn.setImageDrawable(Icon(Icon.Kind.SAVE, if (ok) Pal.ACC else Pal.INK))
    }

    private fun refreshStats() {
        val r = App.result
        if (r == null) {
            for (i in 0..2) { tileVal[i].text = "—"; tileSub[i].text = "" }
            tileSub[0].text = "LUFS"
            tileSub[1].text = "dBTP"
            tileSub[2].text = "giảm tối đa"
            return
        }
        fun n(v: Double) = if (v <= -99) "—" else String.format(java.util.Locale.US, "%.1f", v).replace('-', '−')
        tileVal[0].text = n(r.outLufs)
        tileSub[0].text = "LUFS · gốc ${n(r.inLufs)}"
        tileVal[1].text = n(r.outTpDb)
        tileSub[1].text = "dBTP · gốc ${n(r.inTpDb)}"
        tileVal[2].text = n(r.limiterGrDb)
        tileSub[2].text = "dB giảm tối đa"
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
        if (playButtons.isNotEmpty() && (playButtons[0].tag as? Boolean) != playing) {
            playButtons[0].tag = playing
            refreshPlayer()
        }
    }

    private fun setBusy(on: Boolean, text: String?, f: Float) {
        App.busy = on
        busyBox.visibility = if (on) View.VISIBLE else View.GONE
        actionRow.visibility = if (on) View.GONE else View.VISIBLE
        dirtyNote.visibility = if (on) View.GONE else View.VISIBLE
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

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
import vn.daopham.codmaster.dsp.StemMix
import kotlin.math.pow

/** Trạng thái sống ngoài Activity (xoay màn hình / mở lại không mất bài). */
object App {
    // bản gốc đang dùng (1 file, hoặc tổng thô của 2 stem)
    var decoded: Decoded? = null
    var name: String = ""
    // chế độ 1 file
    var single: Decoded? = null
    var singleName: String = ""
    // chế độ 2 stem
    var stemMode = false
    var vocal: Decoded? = null
    var beat: Decoded? = null
    var vocalName = ""
    var beatName = ""
    val stem: StemMix.StemSettings = StemMix.StemSettings().also { StemDefs.all[0].applyTo(it) }
    var masteredStem: StemMix.StemSettings? = null
    var stemRes: StemMix.Result? = null

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
    var tuneTab = 0
    var onEnded: (() -> Unit)? = null
    val player = Player { onEnded?.invoke() }
}

class MainActivity : Activity() {
    companion object {
        private const val REQ_OPEN = 1
        private const val REQ_SAVE = 2
        private const val REQ_VOCAL = 3
        private const val REQ_BEAT = 4
        private const val REQ_STEMS = 5
    }

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var cancel = false

    // track
    private lateinit var fileName: TextView
    private lateinit var fileInfo: TextView
    private lateinit var pickBtn: TextView
    private lateinit var modeSeg: List<TextView>
    private lateinit var singleBox: LinearLayout
    private lateinit var stemBox: LinearLayout
    private lateinit var vocalSlot: Slot
    private lateinit var beatSlot: Slot
    // monitor
    private lateinit var wave: WaveView
    private lateinit var timeText: TextView
    private lateinit var nowText: TextView
    private lateinit var ab2Box: LinearLayout
    private lateinit var ab2: List<TextView>
    private lateinit var ab4Box: LinearLayout
    private lateinit var ab4: List<TextView>
    private lateinit var matchChip: TextView
    private val playButtons = ArrayList<ImageView>()
    private val tileVal = ArrayList<TextView>()
    private val tileSub = ArrayList<TextView>()
    // style / tune
    private val presetViews = ArrayList<TextView>()
    private val refreshers = ArrayList<() -> Unit>()
    private var suppressParam = false
    private lateinit var tabBox: LinearLayout
    private lateinit var tabs: List<TextView>
    private val pages = ArrayList<LinearLayout>()
    private lateinit var deverbInfo: TextView
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

    private class Slot(val root: LinearLayout, val name: TextView, val info: TextView, val btn: TextView)

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

    @Suppress("DEPRECATION")
    private fun handleIntent(i: Intent?) {
        i ?: return
        when (i.action) {
            Intent.ACTION_SEND_MULTIPLE -> {
                val list: List<Uri> = (if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: arrayListOf()
                if (list.size >= 2) loadTwo(list[0], list[1]) else if (list.size == 1) loadFile(list[0])
            }
            Intent.ACTION_SEND -> {
                val u = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (u != null) loadFile(u)
            }
            Intent.ACTION_VIEW -> i.data?.let { loadFile(it) }
        }
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

    /** Nút gạt nhiều lựa chọn kiểu phòng thu. fill = các ô chia đều toàn bộ chiều ngang. */
    private fun segmented(items: List<String>, fill: Boolean, textSp: Float = 12.5f, onPick: (Int) -> Unit): Pair<LinearLayout, List<TextView>> {
        val box = hbox().apply {
            background = rounded(Pal.CARD2, 999, Pal.LINE)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val views = items.mapIndexed { i, t ->
            TextView(this).apply {
                text = t
                textSize = textSp
                typeface = SANS_MEDIUM
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(dp(if (fill) 4 else 14), dp(6), dp(if (fill) 4 else 14), dp(6))
                isClickable = true
                setOnClickListener { onPick(i) }
            }
        }
        for (v in views) {
            if (fill) box.add(v, 0, WRAP, 1f) else box.add(v, WRAP, WRAP)
        }
        return Pair(box, views)
    }

    private fun styleSeg(t: TextView, on: Boolean, enabled: Boolean = true) {
        t.setTextColor(if (on) Pal.ACC_INK else if (enabled) Pal.SUB else Pal.LINE)
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

    /** Một hàng chỉnh: tên (chạm để xem giải thích) | thanh kéo | giá trị. */
    private fun paramRow(
        title: String, hint: String, steps: Int, fromIndex: (Int) -> Double, toIndex: (Double) -> Int,
        fmt: (Double) -> String, get: () -> Double, set: (Double) -> Unit,
    ): LinearLayout {
        val row = hbox()
        val name = label(title, 12.5f, Pal.INK).apply {
            maxLines = 2
            isClickable = true
            setOnClickListener { toast("$title: $hint") }
        }
        row.add(name, dp(100), WRAP)
        val v = label("", 12f, Pal.ACC, SANS_MEDIUM).apply { gravity = Gravity.END; maxLines = 1 }
        val sb = SeekBar(this).apply {
            max = steps
            progressTintList = ColorStateList.valueOf(Pal.ACC)
            thumbTintList = ColorStateList.valueOf(Pal.ACC)
            progressBackgroundTintList = ColorStateList.valueOf(Pal.LINE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val value = fromIndex(p)
                    v.text = fmt(value)
                    if (fromUser && !suppressParam) {
                        set(value)
                        App.presetIdx = -1
                        refreshPresets()
                        refreshDirty()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        row.add(sb, 0, WRAP, 1f)
        row.add(v, dp(72), WRAP)
        refreshers.add {
            val x = get()
            sb.progress = toIndex(x)
            v.text = fmt(x)
        }
        return row
    }

    private fun slot(title: String, color: Int, onPick: () -> Unit): Slot {
        val row = hbox()
        val ic = ImageView(this).apply {
            setImageDrawable(Icon(Icon.Kind.MUSIC, color))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded((color and 0x00FFFFFF) or 0x30000000, 12)
        }
        row.add(ic, dp(42), dp(42))
        row.gapW(12)
        val col = vbox()
        col.add(caps(title, color).apply { textSize = 10f })
        val name = label("Chưa chọn", 14f, Pal.SUB, SANS_MEDIUM).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        val info = label("", 11f, Pal.SUB).apply { maxLines = 1 }
        col.add(name, topDp = 1)
        col.add(info)
        row.add(col, 0, WRAP, 1f)
        row.gapW(8)
        val btn = pill("Chọn", Btn.OUTLINE) { onPick() }.apply {
            textSize = 12.5f; minHeight = dp(34); setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        row.add(btn, WRAP, WRAP)
        return Slot(row, name, info, btn)
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

        // Bài nhạc: 1 file hoặc 2 stem
        val track = compactCard()
        val thead = hbox()
        thead.add(caps("Bài nhạc"), 0, WRAP, 1f)
        val ms = segmented(listOf("1 FILE", "2 STEM"), false, 11.5f) { setStemMode(it == 1) }
        modeSeg = ms.second
        thead.add(ms.first, WRAP, WRAP)
        track.add(thead)

        singleBox = vbox()
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
        singleBox.add(trow)
        track.add(singleBox, topDp = 12)

        stemBox = vbox().apply { visibility = View.GONE }
        vocalSlot = slot("Giọng hát", Pal.TEAL) { pickStem(REQ_VOCAL) }
        beatSlot = slot("Nhạc nền", Pal.ACC) { pickStem(REQ_BEAT) }
        stemBox.add(vocalSlot.root)
        stemBox.add(beatSlot.root, topDp = 10)
        stemBox.add(pill("Chọn cùng lúc 2 file Vocals + Instrumental", Btn.SOFT) { pickStem(REQ_STEMS) }.apply {
            textSize = 12.5f; minHeight = dp(38)
        }, topDp = 12)
        stemBox.add(label("Tự nhận file có chữ “Vocals” là giọng, “Instrumental” là nhạc nền", 11f, Pal.SUB), topDp = 6)
        track.add(stemBox, topDp = 12)
        content.add(track, topDp = 14)

        // Monitor: sóng âm + phát + A/B + số đo
        val mon = compactCard()
        val mhead = hbox()
        mhead.add(caps("Nghe thử"), 0, WRAP, 1f)
        val s2 = segmented(listOf("GỐC", "MASTER"), false) { setAB(it) }
        ab2Box = s2.first
        ab2 = s2.second
        mhead.add(ab2Box, WRAP, WRAP)
        mon.add(mhead)
        val s4 = segmented(listOf("Gốc", "Master", "Giọng gốc", "Giọng mới"), true, 11.5f) { setAB(it) }
        ab4Box = s4.first.apply { visibility = View.GONE }
        ab4 = s4.second
        mon.add(ab4Box, topDp = 10)

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
        nowText = label("Chưa có bài", 14f, Pal.INK, SANS_MEDIUM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
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

        // Tinh chỉnh: TỔNG / GIỌNG / NHẠC NỀN
        val tune = compactCard()
        val tuneHead = hbox()
        tuneHead.add(caps("Tinh chỉnh"), 0, WRAP, 1f)
        tuneHead.add(label("Chạm tên để xem giải thích", 11f, Pal.SUB), WRAP, WRAP)
        tune.add(tuneHead)
        val tb = segmented(listOf("TỔNG", "GIỌNG", "NHẠC NỀN"), true, 12f) { showTab(it) }
        tabBox = tb.first.apply { visibility = View.GONE }
        tabs = tb.second
        tune.add(tabBox, topDp = 10)
        repeat(3) { pages.add(vbox()) }
        Params.all.forEachIndexed { idx, prm ->
            val row = paramRow(prm.title, prm.hint, prm.steps, { prm.fromIndex(it) }, { prm.toIndex(it) }, prm.fmt,
                { prm.getter(App.settings) }, { prm.setter(App.settings, it) })
            pages[0].add(row, topDp = if (idx == 0) 0 else 2)
        }
        StemParams.all.forEach { prm ->
            val pg = pages[prm.tab]
            val row = paramRow(prm.title, prm.hint, prm.steps, { prm.fromIndex(it) }, { prm.toIndex(it) }, prm.fmt,
                { prm.getter(App.stem) }, { prm.setter(App.stem, it) })
            pg.add(row, topDp = if (pg.childCount == 0) 0 else 2)
        }
        deverbInfo = label("", 11.5f, Pal.SUB)
        pages[1].add(deverbInfo, topDp = 8)
        for (pg in pages) tune.add(pg, topDp = 8)
        content.add(tune, topDp = 14)

        // Xuất file
        val exp = compactCard()
        val ehead = hbox()
        val etx = vbox()
        etx.add(caps("Xuất file WAV"))
        etx.add(label("24-bit: lưu trữ, dựng video · 16-bit: có dither", 11.5f, Pal.SUB), topDp = 3)
        ehead.add(etx, 0, WRAP, 1f)
        val bseg = segmented(listOf("24-bit", "16-bit"), false) {
            App.exportBits = if (it == 0) 24 else 16
            refreshExport()
        }
        bits24 = bseg.second[0]
        bits16 = bseg.second[1]
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
        refreshMode()
        refreshFile()
        refreshPresets()
        refreshParams()
        refreshPlayer()
        refreshStats()
        refreshExport()
        wave.setData(App.origPeaks, App.masterPeaks)
        setBusy(App.busy, null, 0f)
    }

    private fun refreshMode() {
        val st = App.stemMode
        modeSeg.forEachIndexed { i, t -> styleSeg(t, (i == 1) == st) }
        singleBox.visibility = if (st) View.GONE else View.VISIBLE
        stemBox.visibility = if (st) View.VISIBLE else View.GONE
        ab2Box.visibility = if (st) View.GONE else View.VISIBLE
        ab4Box.visibility = if (st) View.VISIBLE else View.GONE
        tabBox.visibility = if (st) View.VISIBLE else View.GONE
        if (!st) App.tuneTab = 0
        showTab(App.tuneTab)
    }

    private fun showTab(i: Int) {
        App.tuneTab = i
        pages.forEachIndexed { k, p -> p.visibility = if (k == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { k, t -> styleSeg(t, k == i) }
    }

    private fun fillSlot(s: Slot, d: Decoded?, n: String) {
        if (d == null) {
            s.name.text = "Chưa chọn"
            s.name.setTextColor(Pal.SUB)
            s.info.text = ""
        } else {
            s.name.text = n
            s.name.setTextColor(Pal.INK)
            s.info.text = String.format(java.util.Locale.US, "%.1f kHz · %s", d.sampleRate / 1000.0, fmtTime(d.frames, d.sampleRate))
        }
        s.btn.text = if (d == null) "Chọn" else "Đổi"
    }

    private fun refreshFile() {
        val d = App.single
        if (d == null) {
            fileName.text = "Chưa chọn bài"
            fileInfo.text = "MP3 · M4A · WAV · FLAC · OGG"
            pickBtn.text = "Chọn bài"
        } else {
            fileName.text = App.singleName
            val chs = if (d.srcChannels == 1) "Mono" else "Stereo"
            val khz = String.format(java.util.Locale.US, "%.1f kHz", d.sampleRate / 1000.0)
            fileInfo.text = "$khz · $chs · ${fmtTime(d.frames, d.sampleRate)}"
            pickBtn.text = "Đổi bài"
        }
        fillSlot(vocalSlot, App.vocal, App.vocalName)
        fillSlot(beatSlot, App.beat, App.beatName)
        for (v in needFile) v.alpha = if (App.decoded != null) 1f else 0.5f
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
        for (r in refreshers) r()
        suppressParam = false
    }

    private fun settingsChanged(): Boolean {
        val a = App.masteredWith ?: return true
        val b = App.settings
        if (App.stemMode && !App.stem.sameAs(App.masteredStem)) return true
        return a.targetLufs != b.targetLufs || a.ceilingDb != b.ceilingDb || a.bassDb != b.bassDb ||
            a.mudDb != b.mudDb || a.presenceDb != b.presenceDb || a.airDb != b.airDb ||
            a.warmth != b.warmth || a.comp != b.comp || a.width != b.width
    }

    private fun refreshDirty() {
        val has = App.decoded != null
        val mastered = App.masterL != null
        val changed = mastered && settingsChanged()
        dirtyNote.text = when {
            !has && App.stemMode -> when {
                App.vocal == null && App.beat == null -> "Nạp 2 stem: giọng hát + nhạc nền"
                App.vocal == null -> "Còn thiếu stem giọng hát"
                else -> "Còn thiếu stem nhạc nền"
            }
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
        val eff = if (has) p.effective() else Player.ORIG
        ab2.forEachIndexed { i, t -> styleSeg(t, i == eff, i == 0 || p.has(i)) }
        ab4.forEachIndexed { i, t -> styleSeg(t, i == eff, i == 0 || p.has(i)) }
        nowText.text = when {
            !has -> "Chưa có bài"
            eff == Player.MASTER -> if (App.matchLevel) "Đang nghe: Master (đã cân)" else "Đang nghe: Master"
            eff == Player.VOCAL_ORIG -> "Đang nghe: Giọng gốc"
            eff == Player.VOCAL_NEW -> "Đang nghe: Giọng mới"
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

    private fun n1(v: Double) = if (v <= -99) "—" else String.format(java.util.Locale.US, "%.1f", v).replace('-', '−')

    private fun refreshStats() {
        val da = App.stemRes?.deverb
        deverbInfo.text = if (da == null || (App.stem.deReverb <= 0.01 && App.stem.deDelay <= 0.01)) {
            "Vang/delay có sẵn của Suno được đo khi bấm MASTER"
        } else {
            val rv = if (da.dryLike) "giọng khô sẵn" else (if (da.reverbMeasured) "" else "~") +
                String.format(java.util.Locale.US, "vang ~%.1f s", da.t60)
            val dl = if (da.delayFound) String.format(java.util.Locale.US, "delay %.0f ms", da.delayMs) else "không có delay"
            "Giọng gốc: $rv · $dl\nSo sánh: Giọng gốc ↔ Giọng mới"
        }
        val r = App.result
        if (r == null) {
            for (i in 0..2) { tileVal[i].text = "—"; tileSub[i].text = "" }
            tileSub[0].text = "LUFS"
            tileSub[1].text = "dBTP"
            tileSub[2].text = "giảm tối đa"
            return
        }
        tileVal[0].text = n1(r.outLufs)
        tileSub[0].text = "LUFS · gốc ${n1(r.inLufs)}"
        tileVal[1].text = n1(r.outTpDb)
        tileSub[1].text = "dBTP · gốc ${n1(r.inTpDb)}"
        tileVal[2].text = n1(r.limiterGrDb)
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

    // ───────────────────────────── trạng thái ─────────────────────────────

    private fun clearMaster() {
        val p = App.player
        p.setMaster(null, null)
        p.setVocalNew(null, null)
        App.masterL = null; App.masterR = null
        App.result = null; App.masteredWith = null
        App.masterPeaks = null
        App.stemRes = null; App.masteredStem = null
    }

    /** Chọn "bản gốc" đang dùng: 1 file, hoặc tổng thô của 2 stem. */
    private fun rebuildActive() {
        val d: Decoded?
        if (!App.stemMode) {
            d = App.single
            App.name = App.singleName
        } else {
            val v = App.vocal
            val b = App.beat
            d = if (v != null && b != null) StemIO.mix(v, b) else null
            App.name = if (d != null) StemIO.cleanBase(App.vocalName) else ""
        }
        App.decoded = d
        App.origPeaks = d?.let { Peaks.ofShorts(it.left, it.right, it.frames) }
        App.player.setSource(d)
        if (App.stemMode && d != null) App.player.setVocalOrig(App.vocal)
    }

    private fun setStemMode(on: Boolean) {
        if (App.busy || on == App.stemMode) { refreshMode(); return }
        App.stemMode = on
        App.player.setSource(null)
        clearMaster()
        rebuildActive()
        App.player.sel = Player.MASTER
        refreshAll()
    }

    // ───────────────────────────── hành động ─────────────────────────────

    private fun choosePreset(i: Int) {
        App.presetIdx = i
        Presets.all[i].applyTo(App.settings)
        StemDefs.all[i].applyTo(App.stem)
        refreshPresets()
        refreshParams()
        refreshDirty()
    }

    private fun openPicker(req: Int, multiple: Boolean) {
        if (App.busy) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg", "video/mp4"))
            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(i, req)
        } catch (e: Exception) {
            toast("Không mở được trình chọn file")
        }
    }

    private fun pickFile() = openPicker(REQ_OPEN, false)

    private fun pickStem(req: Int) = openPicker(req, req == REQ_STEMS)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_STEMS -> {
                val uris = ArrayList<Uri>()
                val clip = data.clipData
                if (clip != null) for (k in 0 until clip.itemCount) clip.getItemAt(k).uri?.let { uris.add(it) }
                else data.data?.let { uris.add(it) }
                when {
                    uris.size >= 2 -> loadTwo(uris[0], uris[1])
                    uris.size == 1 -> {
                        val n = AudioIO.displayName(this, uris[0])
                        val asVocal = if (StemIO.looksVocal(n)) true else if (StemIO.looksBeat(n)) false else App.vocal == null
                        loadStem(asVocal, uris[0], null)
                    }
                }
            }
            REQ_OPEN -> data.data?.let { loadFile(it) }
            REQ_VOCAL -> data.data?.let { loadStem(true, it, null) }
            REQ_BEAT -> data.data?.let { loadStem(false, it, null) }
            REQ_SAVE -> data.data?.let { writeFile(it) }
        }
    }

    private fun loadFile(uri: Uri) {
        if (App.busy) {
            toast("Đang xử lý, đợi chút nhé")
            return
        }
        if (App.stemMode) App.stemMode = false
        App.player.setSource(null)
        App.single = null
        App.vocal = null; App.beat = null          // giải phóng bộ nhớ của 2 stem
        clearMaster()
        rebuildActive()
        wave.setData(null, null)
        refreshAll()
        System.gc()
        cancel = false
        val name = AudioIO.displayName(this, uri)
        setBusy(true, "Đang đọc “$name”…", 0f)
        Thread {
            try {
                val d = decodeWithProgress(uri)
                ui.post {
                    App.single = d
                    App.singleName = name
                    rebuildActive()
                    App.player.sel = Player.MASTER
                    setBusy(false, null, 0f)
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

    private fun decodeWithProgress(uri: Uri): Decoded {
        var last = 0L
        return AudioIO.decode(this, uri) { f ->
            val now = System.currentTimeMillis()
            if (now - last > 100) {
                last = now
                ui.post { if (App.busy) busyBar.progress = (f * 1000).toInt() }
            }
            !cancel
        }
    }

    private fun loadTwo(a: Uri, b: Uri) {
        val na = AudioIO.displayName(this, a)
        val nb = AudioIO.displayName(this, b)
        val aIsVocal = StemIO.looksVocal(na) || StemIO.looksBeat(nb) || !(StemIO.looksVocal(nb) || StemIO.looksBeat(na))
        val v = if (aIsVocal) a else b
        val bt = if (aIsVocal) b else a
        loadStem(true, v) { loadStem(false, bt, null) }
    }

    private fun loadStem(isVocal: Boolean, uri: Uri, then: (() -> Unit)?) {
        if (App.busy) {
            toast("Đang xử lý, đợi chút nhé")
            return
        }
        if (!App.stemMode) App.stemMode = true
        App.player.setSource(null)
        App.single = null                           // giải phóng bộ nhớ bài 1 file
        clearMaster()
        if (isVocal) App.vocal = null else App.beat = null
        rebuildActive()
        refreshAll()
        System.gc()
        cancel = false
        val name = AudioIO.displayName(this, uri)
        setBusy(true, "Đang đọc ${if (isVocal) "giọng hát" else "nhạc nền"}: “$name”…", 0f)
        Thread {
            try {
                var d = decodeWithProgress(uri)
                val other = if (isVocal) App.beat else App.vocal
                if (other != null && other.sampleRate != d.sampleRate) d = StemIO.resample(d, other.sampleRate)
                ui.post {
                    if (isVocal) { App.vocal = d; App.vocalName = name } else { App.beat = d; App.beatName = name }
                    rebuildActive()
                    App.player.sel = Player.MASTER
                    setBusy(false, null, 0f)
                    refreshAll()
                    then?.invoke()
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
        clearMaster()
        wave.setData(App.origPeaks, null)
        System.gc()
        cancel = false
        val settings = App.settings.copy()
        val stemSnap = if (App.stemMode) App.stem.copy().also { it.keepVocal = true } else null
        val v = App.vocal
        val b = App.beat
        if (stemSnap != null && (v == null || b == null)) return
        setBusy(true, "Chuẩn bị…", 0f)
        refreshPlayer()
        Thread {
            try {
                var last = 0L
                val t0 = System.currentTimeMillis()
                val prog = Mastering.Progress { f, stage ->
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
                val n = d.frames
                val sc = 1f / 32768f
                val outL: FloatArray
                val outR: FloatArray
                val res: Mastering.Result
                var sres: StemMix.Result? = null
                if (stemSnap != null && v != null && b != null) {
                    val vL = FloatArray(n); val vR = FloatArray(n)
                    for (i in 0 until v.frames) { vL[i] = v.left[i] * sc; vR[i] = v.right[i] * sc }
                    val bL = FloatArray(n); val bR = FloatArray(n)
                    for (i in 0 until b.frames) { bL[i] = b.left[i] * sc; bR[i] = b.right[i] * sc }
                    sres = StemMix.processInPlace(vL, vR, bL, bR, n, d.sampleRate.toDouble(), stemSnap, settings, prog)
                    res = sres.master
                    outL = bL
                    outR = bR
                } else {
                    outL = FloatArray(n)
                    outR = FloatArray(n)
                    val dl = d.left
                    val dr = d.right
                    for (i in 0 until n) {
                        outL[i] = dl[i] * sc
                        outR[i] = dr[i] * sc
                    }
                    res = Mastering.process(outL, outR, n, d.sampleRate.toDouble(), settings, prog)
                }
                val peaks = Peaks.ofFloats(outL, outR, n)
                val secs = (System.currentTimeMillis() - t0) / 1000.0
                val fs = sres
                ui.post {
                    App.masterL = outL
                    App.masterR = outR
                    App.result = res
                    App.masteredWith = settings
                    App.masterPeaks = peaks
                    App.stemRes = fs
                    App.masteredStem = stemSnap
                    p.setMaster(outL, outR)
                    if (fs?.vocalL != null) p.setVocalNew(fs.vocalL, fs.vocalR)
                    p.sel = Player.MASTER
                    applyMatch()
                    setBusy(false, null, 1f)
                    wave.setData(App.origPeaks, peaks)
                    refreshAll()
                    toast(String.format(java.util.Locale.US, "Xong trong %.0f giây — bấm Phát để nghe", secs))
                }
            } catch (e: Mastering.CancelledException) {
                ui.post { setBusy(false, null, 0f); refreshAll() }
            } catch (e: OutOfMemoryError) {
                ui.post {
                    clearMaster(); setBusy(false, null, 0f); refreshAll()
                    toast("Không đủ bộ nhớ để xử lý bài này")
                }
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

    private fun setAB(idx: Int) {
        if (!App.player.has(idx)) {
            if (idx == Player.VOCAL_NEW || idx == Player.MASTER) toast("Bấm MASTER trước để có bản này")
            refreshPlayer()
            return
        }
        App.player.sel = idx
        refreshPlayer()
    }

    private fun saveFile() {
        if (App.masterL == null || App.busy) return
        val base = if (App.stemMode) StemIO.cleanBase(App.vocalName) else App.name.substringBeforeLast('.').ifBlank { "bai-nhac" }
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

package vn.daopham.codmaster

import vn.daopham.codmaster.dsp.Mastering
import vn.daopham.codmaster.dsp.StemMix

class Preset(val name: String, val desc: String, val applyTo: Mastering.Settings.() -> Unit)

object Presets {
    val all = listOf(
        Preset("Streaming", "YouTube · Spotify · −14 LUFS") {
            targetLufs = -14.0; bassDb = 1.0; mudDb = -1.0; presenceDb = 1.0; airDb = 1.5
            warmth = 0.2; comp = 0.35; width = 1.1
        },
        Preset("Rock Ballad", "Dày, ấm, giọng nổi · −11") {
            targetLufs = -11.0; bassDb = 2.0; mudDb = -1.5; presenceDb = 1.5; airDb = 2.0
            warmth = 0.4; comp = 0.55; width = 1.15
        },
        Preset("Bolero", "Giọng ấm, mượt, nổi lời · −13") {
            targetLufs = -13.0; bassDb = 1.0; mudDb = -1.5; presenceDb = 2.0; airDb = 0.5
            warmth = 0.55; comp = 0.45; width = 1.0
        },
        Preset("Trữ tình / Quê hương", "Tròn, sâu lắng, tự nhiên · −14") {
            targetLufs = -14.0; bassDb = 1.5; mudDb = -1.0; presenceDb = 1.5; airDb = 1.0
            warmth = 0.45; comp = 0.35; width = 1.1
        },
        Preset("Ballad Việt", "Sáng, rộng, giọng trong · −12") {
            targetLufs = -12.0; bassDb = 1.5; mudDb = -1.5; presenceDb = 2.0; airDb = 2.0
            warmth = 0.3; comp = 0.45; width = 1.15
        },
        Preset("Giọng hát rõ", "Đẩy lời hát lên trước · −13") {
            targetLufs = -13.0; bassDb = 0.0; mudDb = -2.0; presenceDb = 2.5; airDb = 1.5
            warmth = 0.2; comp = 0.4; width = 1.05
        },
        Preset("Ấm / Acoustic", "Mộc, tròn, tự nhiên · −14") {
            targetLufs = -14.0; bassDb = 1.5; mudDb = -1.0; presenceDb = 0.0; airDb = 0.5
            warmth = 0.5; comp = 0.3; width = 1.0
        },
        Preset("Remix / Vinahouse", "Bass dồn, to, sôi động · −8.5") {
            targetLufs = -8.5; bassDb = 3.5; mudDb = -1.5; presenceDb = 1.0; airDb = 2.5
            warmth = 0.45; comp = 0.75; width = 1.2
        },
        Preset("To & đậm", "Pop / EDM, lớn tối đa · −9") {
            targetLufs = -9.0; bassDb = 2.5; mudDb = -1.0; presenceDb = 1.0; airDb = 2.0
            warmth = 0.5; comp = 0.7; width = 1.2
        },
        Preset("Nhẹ nhàng", "Giữ độ động, ít can thiệp · −16") {
            targetLufs = -16.0; bassDb = 0.5; mudDb = -0.5; presenceDb = 0.5; airDb = 1.0
            warmth = 0.1; comp = 0.15; width = 1.0
        },
    )
}

/** Một thanh chỉnh: ánh xạ SeekBar (số nguyên) ↔ giá trị thực trong Settings. */
class Param(
    val title: String,
    val hint: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val getter: Mastering.Settings.() -> Double,
    val setter: Mastering.Settings.(Double) -> Unit,
    val fmt: (Double) -> String,
) {
    val steps get() = Math.round((max - min) / step).toInt()
    fun toIndex(v: Double) = Math.round((v.coerceIn(min, max) - min) / step).toInt()
    fun fromIndex(i: Int) = min + i * step
}

private fun signed(v: Double, unit: String): String {
    val s = String.format(java.util.Locale.US, "%.1f", v)
    return (if (v > 0.04) "+$s" else s.replace('-', '−')) + " " + unit
}

private fun pct(v: Double) = "${Math.round(v * 100)}%"

object Params {
    val all = listOf(
        Param("Độ lớn mục tiêu", "YouTube/Spotify chuẩn −14; to hơn thì giảm độ động", -16.0, -8.0, 0.5,
            { targetLufs }, { targetLufs = it }, { String.format(java.util.Locale.US, "%.1f LUFS", it).replace('-', '−') }),
        Param("Trầm (bass)", "Kệ thấp 100 Hz — độ dày, lực trống", -4.0, 6.0, 0.5,
            { bassDb }, { bassDb = it }, { signed(it, "dB") }),
        Param("Gọn trung trầm", "Cắt quanh 300 Hz — bớt đục, bớt ù", -4.0, 0.0, 0.5,
            { mudDb }, { mudDb = it }, { signed(it, "dB") }),
        Param("Rõ giọng", "Chuông 3 kHz — lời hát, guitar nổi", -3.0, 5.0, 0.5,
            { presenceDb }, { presenceDb = it }, { signed(it, "dB") }),
        Param("Độ sáng (air)", "Kệ cao 10 kHz — trong, thoáng", -3.0, 5.0, 0.5,
            { airDb }, { airDb = it }, { signed(it, "dB") }),
        Param("Độ ấm", "Bão hoà kiểu băng từ — tròn, dày, analog", 0.0, 1.0, 0.05,
            { warmth }, { warmth = it }, ::pct),
        Param("Nén / độ dính", "Gắn kết các nhạc cụ, đều tiếng", 0.0, 1.0, 0.05,
            { comp }, { comp = it }, ::pct),
        Param("Độ rộng stereo", "Bass luôn giữ ở giữa cho chắc", 0.5, 1.6, 0.05,
            { width }, { width = it }, ::pct),
        Param("Trần đỉnh thật", "−1 dBTP an toàn khi nén lại MP3/AAC", -2.0, -0.3, 0.1,
            { ceilingDb }, { ceilingDb = it }, { String.format(java.util.Locale.US, "%.1f dBTP", it).replace('-', '−') }),
    )
}

// ───────────────────────────── chế độ 2 stem ─────────────────────────────

/** Thông số stem mặc định cho từng phong cách (cùng thứ tự với Presets.all). */
class StemDef(
    val vocalDb: Double, val hpf: Double, val comp: Double, val deEss: Double, val pres: Double, val air: Double,
    val mud: Double, val reverb: Double, val beatDb: Double, val pocket: Double, val beatBass: Double, val beatWidth: Double,
) {
    fun applyTo(s: StemMix.StemSettings) {
        s.vocalDb = vocalDb; s.vocalHpf = hpf; s.vocalComp = comp; s.deEss = deEss; s.vocalPresence = pres
        s.vocalAir = air; s.vocalMud = mud; s.reverb = reverb; s.beatDb = beatDb; s.pocket = pocket
        s.beatBass = beatBass; s.beatWidth = beatWidth
    }
}

object StemDefs {
    val all = listOf(
        StemDef(0.0, 90.0, 0.45, 0.40, 1.5, 1.5, -1.5, 0.10, 0.0, 2.0, 0.0, 1.10),    // Streaming
        StemDef(0.5, 100.0, 0.55, 0.45, 2.0, 2.0, -2.0, 0.15, 0.0, 2.5, 1.0, 1.15),   // Rock Ballad
        StemDef(1.0, 90.0, 0.50, 0.50, 2.0, 1.0, -2.0, 0.30, 0.0, 2.5, 0.5, 1.0),     // Bolero
        StemDef(1.0, 85.0, 0.45, 0.45, 1.5, 1.0, -1.5, 0.25, 0.0, 2.0, 0.5, 1.05),    // Trữ tình
        StemDef(0.5, 100.0, 0.50, 0.50, 2.0, 2.5, -2.0, 0.20, 0.0, 2.5, 0.5, 1.15),   // Ballad Việt
        StemDef(1.5, 110.0, 0.55, 0.55, 3.0, 2.0, -2.5, 0.10, -0.5, 3.5, 0.0, 1.10),  // Giọng hát rõ
        StemDef(0.5, 80.0, 0.40, 0.35, 1.0, 0.5, -1.0, 0.20, 0.0, 1.5, 0.5, 1.0),     // Ấm / Acoustic
        StemDef(-0.5, 120.0, 0.65, 0.50, 2.0, 2.5, -2.0, 0.10, 0.5, 2.0, 1.5, 1.2),   // Remix
        StemDef(0.0, 110.0, 0.65, 0.50, 2.0, 2.5, -2.0, 0.12, 0.5, 2.5, 1.0, 1.2),    // To & đậm
        StemDef(0.5, 80.0, 0.30, 0.35, 1.0, 1.0, -1.0, 0.18, 0.0, 1.5, 0.0, 1.0),     // Nhẹ nhàng
    )
}

/** Thanh chỉnh cho stem. tab: 1 = giọng, 2 = nhạc nền. */
class SParam(
    val tab: Int,
    val title: String,
    val hint: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val getter: StemMix.StemSettings.() -> Double,
    val setter: StemMix.StemSettings.(Double) -> Unit,
    val fmt: (Double) -> String,
) {
    val steps get() = Math.round((max - min) / step).toInt()
    fun toIndex(v: Double) = Math.round((v.coerceIn(min, max) - min) / step).toInt()
    fun fromIndex(i: Int) = min + i * step
}

object StemParams {
    val all = listOf(
        SParam(1, "Khử vang Suno", "Hạ đuôi vang có sẵn trong giọng (tự đo độ dài vang). Mạnh quá giọng sẽ khô, hơi lạo xạo", 0.0, 1.0, 0.05,
            { deReverb }, { deReverb = it }, ::pct),
        SParam(1, "Khử delay Suno", "Tự dò tiếng dội lặp lại và hạ xuống. Bài không có delay thì không tác động", 0.0, 1.0, 0.05,
            { deDelay }, { deDelay = it }, ::pct),
        SParam(1, "Giọng to/nhỏ", "So với cân bằng gốc giữa giọng và nhạc", -6.0, 6.0, 0.5,
            { vocalDb }, { vocalDb = it }, { signed(it, "dB") }),
        SParam(1, "Cắt trầm giọng", "Lọc bỏ tiếng ù, hơi thở trầm dưới tần số này", 60.0, 200.0, 5.0,
            { vocalHpf }, { vocalHpf = it }, { "${Math.round(it)} Hz" }),
        SParam(1, "Gọn đục giọng", "Cắt quanh 280 Hz — bớt ồm, bớt mũi", -6.0, 0.0, 0.5,
            { vocalMud }, { vocalMud = it }, { signed(it, "dB") }),
        SParam(1, "Nén giọng", "Câu nhỏ rõ hơn, câu lớn không gắt", 0.0, 1.0, 0.05,
            { vocalComp }, { vocalComp = it }, ::pct),
        SParam(1, "Khử xì (s, x)", "Tự hạ tiếng xì chói 5–10 kHz, chỉ khi có xì", 0.0, 1.0, 0.05,
            { deEss }, { deEss = it }, ::pct),
        SParam(1, "Rõ lời", "Chuông 3.2 kHz trên giọng — lời hát nổi", -3.0, 6.0, 0.5,
            { vocalPresence }, { vocalPresence = it }, { signed(it, "dB") }),
        SParam(1, "Sáng giọng", "Kệ cao 11 kHz trên giọng — trong, có hơi", -3.0, 6.0, 0.5,
            { vocalAir }, { vocalAir = it }, { signed(it, "dB") }),
        SParam(1, "Độ vang mới", "Vang plate sạch thêm vào sau khi khử — bolero, trữ tình nên có", 0.0, 1.0, 0.05,
            { reverb }, { reverb = it }, ::pct),
        SParam(2, "Nhạc to/nhỏ", "So với cân bằng gốc", -6.0, 6.0, 0.5,
            { beatDb }, { beatDb = it }, { signed(it, "dB") }),
        SParam(2, "Nhường chỗ giọng", "Khi giọng cất lên, nhạc tự hạ vùng 1–4.5 kHz tối đa bấy nhiêu dB", 0.0, 6.0, 0.5,
            { pocket }, { pocket = it }, { String.format(java.util.Locale.US, "%.1f dB", it) }),
        SParam(2, "Trầm nhạc nền", "Kệ thấp 95 Hz chỉ trên nhạc nền", -4.0, 6.0, 0.5,
            { beatBass }, { beatBass = it }, { signed(it, "dB") }),
        SParam(2, "Độ rộng nhạc", "Mở rộng stereo nhạc nền, giọng vẫn ở giữa", 0.5, 1.6, 0.05,
            { beatWidth }, { beatWidth = it }, ::pct),
    )
}

package vn.daopham.codmaster

import vn.daopham.codmaster.dsp.Mastering

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

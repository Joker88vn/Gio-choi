package vn.daopham.codmaster.desktop;

import vn.daopham.codmaster.dsp.Mastering.Settings;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

/** Phong cách và thông số — giống hệt bản Android. */
final class Presets {
    private Presets() {}

    record Preset(String name, String desc, double lufs, double bass, double mud, double pres, double air,
                  double warmth, double comp, double width) {
        void applyTo(Settings s) {
            s.targetLufs = lufs; s.bassDb = bass; s.mudDb = mud; s.presenceDb = pres; s.airDb = air;
            s.warmth = warmth; s.comp = comp; s.width = width;
        }
    }

    static final List<Preset> ALL = List.of(
            new Preset("Streaming", "YouTube · Spotify · −14 LUFS", -14, 1, -1, 1, 1.5, 0.2, 0.35, 1.1),
            new Preset("Rock Ballad", "Dày, ấm, giọng nổi · −11", -11, 2, -1.5, 1.5, 2, 0.4, 0.55, 1.15),
            new Preset("Bolero", "Giọng ấm, mượt, nổi lời · −13", -13, 1, -1.5, 2, 0.5, 0.55, 0.45, 1.0),
            new Preset("Trữ tình / Quê hương", "Tròn, sâu lắng, tự nhiên · −14", -14, 1.5, -1, 1.5, 1, 0.45, 0.35, 1.1),
            new Preset("Ballad Việt", "Sáng, rộng, giọng trong · −12", -12, 1.5, -1.5, 2, 2, 0.3, 0.45, 1.15),
            new Preset("Giọng hát rõ", "Đẩy lời hát lên trước · −13", -13, 0, -2, 2.5, 1.5, 0.2, 0.4, 1.05),
            new Preset("Ấm / Acoustic", "Mộc, tròn, tự nhiên · −14", -14, 1.5, -1, 0, 0.5, 0.5, 0.3, 1.0),
            new Preset("Remix / Vinahouse", "Bass dồn, to, sôi động · −8.5", -8.5, 3.5, -1.5, 1, 2.5, 0.45, 0.75, 1.2),
            new Preset("To & đậm", "Pop / EDM, lớn tối đa · −9", -9, 2.5, -1, 1, 2, 0.5, 0.7, 1.2),
            new Preset("Nhẹ nhàng", "Giữ độ động, ít can thiệp · −16", -16, 0.5, -0.5, 0.5, 1, 0.1, 0.15, 1.0)
    );

    record Param(String title, String hint, double min, double max, double step,
                 ToDoubleFunction<Settings> get, BiConsumer<Settings, Double> set, DoubleFunction<String> fmt) {}

    static String signed(double v, String unit) {
        String s = String.format(Locale.US, "%.1f", v);
        return (v > 0.04 ? "+" + s : s.replace('-', '−')) + " " + unit;
    }

    static String pct(double v) { return Math.round(v * 100) + "%"; }

    static final List<Param> PARAMS = List.of(
            new Param("Độ lớn", "Độ lớn mục tiêu. YouTube/Spotify chuẩn −14 LUFS; to hơn thì giảm độ động", -16, -8, 0.5,
                    s -> s.targetLufs, (s, v) -> s.targetLufs = v, v -> String.format(Locale.US, "%.1f LUFS", v).replace('-', '−')),
            new Param("Trầm (bass)", "Kệ thấp 100 Hz — độ dày, lực trống", -4, 6, 0.5,
                    s -> s.bassDb, (s, v) -> s.bassDb = v, v -> signed(v, "dB")),
            new Param("Gọn trung trầm", "Cắt quanh 300 Hz — bớt đục, bớt ù", -4, 0, 0.5,
                    s -> s.mudDb, (s, v) -> s.mudDb = v, v -> signed(v, "dB")),
            new Param("Rõ giọng", "Chuông 3 kHz — lời hát, guitar nổi", -3, 5, 0.5,
                    s -> s.presenceDb, (s, v) -> s.presenceDb = v, v -> signed(v, "dB")),
            new Param("Độ sáng (air)", "Kệ cao 10 kHz — trong, thoáng", -3, 5, 0.5,
                    s -> s.airDb, (s, v) -> s.airDb = v, v -> signed(v, "dB")),
            new Param("Độ ấm", "Bão hoà kiểu băng từ — tròn, dày, analog", 0, 1, 0.05,
                    s -> s.warmth, (s, v) -> s.warmth = v, Presets::pct),
            new Param("Nén / độ dính", "Gắn kết các nhạc cụ, đều tiếng", 0, 1, 0.05,
                    s -> s.comp, (s, v) -> s.comp = v, Presets::pct),
            new Param("Độ rộng stereo", "Bass luôn giữ ở giữa cho chắc", 0.5, 1.6, 0.05,
                    s -> s.width, (s, v) -> s.width = v, Presets::pct),
            new Param("Trần đỉnh thật", "−1 dBTP an toàn khi nén lại MP3/AAC", -2, -0.3, 0.1,
                    s -> s.ceilingDb, (s, v) -> s.ceilingDb = v, v -> String.format(Locale.US, "%.1f dBTP", v).replace('-', '−'))
    );
}

package vn.daopham.codmaster.desktop;

import vn.daopham.codmaster.dsp.Mastering.Settings;
import vn.daopham.codmaster.dsp.StemMix.StemSettings;

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

    // ───── chế độ 2 stem ─────

    /** Thông số stem mặc định cho từng phong cách (cùng thứ tự với ALL). */
    record StemDef(double vocalDb, double hpf, double comp, double deEss, double pres, double air, double mud,
                   double reverb, double beatDb, double pocket, double beatBass, double beatWidth) {
        void applyTo(StemSettings s) {
            s.vocalDb = vocalDb; s.vocalHpf = hpf; s.vocalComp = comp; s.deEss = deEss; s.vocalPresence = pres;
            s.vocalAir = air; s.vocalMud = mud; s.reverb = reverb; s.beatDb = beatDb; s.pocket = pocket;
            s.beatBass = beatBass; s.beatWidth = beatWidth;
        }
    }

    static final List<StemDef> STEM = List.of(
            new StemDef(0, 90, 0.45, 0.40, 1.5, 1.5, -1.5, 0.10, 0, 2.0, 0, 1.10),     // Streaming
            new StemDef(0.5, 100, 0.55, 0.45, 2, 2, -2, 0.15, 0, 2.5, 1, 1.15),        // Rock Ballad
            new StemDef(1, 90, 0.50, 0.50, 2, 1, -2, 0.30, 0, 2.5, 0.5, 1.0),          // Bolero
            new StemDef(1, 85, 0.45, 0.45, 1.5, 1, -1.5, 0.25, 0, 2.0, 0.5, 1.05),     // Trữ tình
            new StemDef(0.5, 100, 0.50, 0.50, 2, 2.5, -2, 0.20, 0, 2.5, 0.5, 1.15),    // Ballad Việt
            new StemDef(1.5, 110, 0.55, 0.55, 3, 2, -2.5, 0.10, -0.5, 3.5, 0, 1.10),   // Giọng hát rõ
            new StemDef(0.5, 80, 0.40, 0.35, 1, 0.5, -1, 0.20, 0, 1.5, 0.5, 1.0),      // Ấm / Acoustic
            new StemDef(-0.5, 120, 0.65, 0.50, 2, 2.5, -2, 0.10, 0.5, 2.0, 1.5, 1.2),  // Remix
            new StemDef(0, 110, 0.65, 0.50, 2, 2.5, -2, 0.12, 0.5, 2.5, 1, 1.2),       // To & đậm
            new StemDef(0.5, 80, 0.30, 0.35, 1, 1, -1, 0.18, 0, 1.5, 0, 1.0)           // Nhẹ nhàng
    );

    /** tab: 1 = giọng, 2 = nhạc nền */
    record SParam(int tab, String title, String hint, double min, double max, double step,
                  ToDoubleFunction<StemSettings> get, BiConsumer<StemSettings, Double> set, DoubleFunction<String> fmt) {}

    static final List<SParam> STEM_PARAMS = List.of(
            new SParam(1, "Khử vang Suno", "Hạ đuôi vang có sẵn trong giọng (tự đo độ dài vang). Mạnh quá giọng sẽ khô, hơi 'lạo xạo'", 0, 1, 0.05,
                    s -> s.deReverb, (s, v) -> s.deReverb = v, Presets::pct),
            new SParam(1, "Khử delay Suno", "Tự dò tiếng dội lặp lại và hạ nó xuống. Nếu bài không có delay thì không tác động", 0, 1, 0.05,
                    s -> s.deDelay, (s, v) -> s.deDelay = v, Presets::pct),
            new SParam(1, "Giọng to / nhỏ", "So với cân bằng gốc giữa giọng và nhạc", -6, 6, 0.5,
                    s -> s.vocalDb, (s, v) -> s.vocalDb = v, v -> signed(v, "dB")),
            new SParam(1, "Cắt trầm giọng", "Lọc bỏ tiếng ù, tiếng thở trầm dưới tần số này", 60, 200, 5,
                    s -> s.vocalHpf, (s, v) -> s.vocalHpf = v, v -> Math.round(v) + " Hz"),
            new SParam(1, "Gọn đục giọng", "Cắt quanh 280 Hz — giọng bớt ồm, bớt mũi", -6, 0, 0.5,
                    s -> s.vocalMud, (s, v) -> s.vocalMud = v, v -> signed(v, "dB")),
            new SParam(1, "Nén giọng", "Giữ giọng đều: câu nhỏ rõ hơn, câu lớn không gắt", 0, 1, 0.05,
                    s -> s.vocalComp, (s, v) -> s.vocalComp = v, Presets::pct),
            new SParam(1, "Khử xì (s, x)", "Tự hạ tiếng xì chói ở 5–10 kHz, chỉ khi có xì", 0, 1, 0.05,
                    s -> s.deEss, (s, v) -> s.deEss = v, Presets::pct),
            new SParam(1, "Rõ lời", "Chuông 3.2 kHz trên giọng — lời hát nổi", -3, 6, 0.5,
                    s -> s.vocalPresence, (s, v) -> s.vocalPresence = v, v -> signed(v, "dB")),
            new SParam(1, "Sáng giọng", "Kệ cao 11 kHz trên giọng — trong, có hơi", -3, 6, 0.5,
                    s -> s.vocalAir, (s, v) -> s.vocalAir = v, v -> signed(v, "dB")),
            new SParam(1, "Độ vang", "Vang kiểu plate cho giọng — bolero, trữ tình nên có", 0, 1, 0.05,
                    s -> s.reverb, (s, v) -> s.reverb = v, Presets::pct),
            new SParam(2, "Nhạc nền to / nhỏ", "So với cân bằng gốc", -6, 6, 0.5,
                    s -> s.beatDb, (s, v) -> s.beatDb = v, v -> signed(v, "dB")),
            new SParam(2, "Nhường chỗ giọng", "Khi giọng cất lên, nhạc tự hạ vùng 1–4.5 kHz tối đa bấy nhiêu dB", 0, 6, 0.5,
                    s -> s.pocket, (s, v) -> s.pocket = v, v -> String.format(Locale.US, "%.1f dB", v)),
            new SParam(2, "Trầm nhạc nền", "Kệ thấp 95 Hz chỉ trên nhạc nền", -4, 6, 0.5,
                    s -> s.beatBass, (s, v) -> s.beatBass = v, v -> signed(v, "dB")),
            new SParam(2, "Độ rộng nhạc nền", "Mở rộng stereo nhạc nền, giọng vẫn ở giữa", 0.5, 1.6, 0.05,
                    s -> s.beatWidth, (s, v) -> s.beatWidth = v, Presets::pct)
    );
}

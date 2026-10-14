package vn.daopham.codmaster.dsp;

/**
 * Chuỗi master: chuẩn hoá đầu vào → lọc cắt siêu trầm → EQ (bass, bớt đục, rõ giọng, độ sáng)
 * → nén "glue" → bão hoà ấm (kiểu băng từ) → độ rộng stereo (giữ bass ở giữa)
 * → đưa lên độ lớn mục tiêu (LUFS) → limiter true-peak.
 * Xử lý tại chỗ trên mảng L/R (float, ±1.0).
 */
public final class Mastering {

    public static final class Settings {
        public double targetLufs = -14;
        public double ceilingDb = -1.0;
        public double bassDb = 1;       // kệ thấp 100 Hz
        public double mudDb = -1;       // chuông 300 Hz
        public double presenceDb = 1;   // chuông 3 kHz
        public double airDb = 1.5;      // kệ cao 10 kHz
        public double warmth = 0.2;     // 0..1
        public double comp = 0.35;      // 0..1
        public double width = 1.1;      // 0.5..1.6

        public Settings copy() {
            Settings s = new Settings();
            s.targetLufs = targetLufs; s.ceilingDb = ceilingDb; s.bassDb = bassDb; s.mudDb = mudDb;
            s.presenceDb = presenceDb; s.airDb = airDb; s.warmth = warmth; s.comp = comp; s.width = width;
            return s;
        }
    }

    public static final class Result {
        public double inLufs, inTpDb, outLufs, outTpDb, limiterGrDb;
    }

    public interface Progress {
        /** @return false để huỷ */
        boolean onProgress(double fraction, String stage);
    }

    public static final class CancelledException extends RuntimeException {
        public CancelledException() { super("Đã huỷ"); }
    }

    public static final class SilentException extends RuntimeException {
        public SilentException() { super("Bài nhạc gần như im lặng, không thể master."); }
    }

    private static final int STEP = 65536;

    private static void tick(Progress p, double f, String s) {
        if (p != null && !p.onProgress(Math.min(1, Math.max(0, f)), s)) throw new CancelledException();
    }

    /** Đo LUFS + true peak của một cặp mảng. */
    public static double[] measure(float[] L, float[] R, int n, double fs, Progress p, double p0, double p1, String stage) {
        LoudnessMeter m = new LoudnessMeter(fs);
        for (int i = 0; i < n; i++) {
            m.add(L[i], R[i]);
            if ((i & (STEP - 1)) == 0) tick(p, p0 + (p1 - p0) * i / (double) n, stage);
        }
        return new double[]{m.integrated(), m.truePeakDb()};
    }

    public static Result process(float[] L, float[] R, int n, double fs, Settings s, Progress p) {
        Result res = new Result();

        // 1) Đo bài gốc
        double[] in = measure(L, R, n, fs, p, 0.0, 0.12, "Đo bài gốc");
        res.inLufs = in[0];
        res.inTpDb = in[1];
        if (in[0] < -60) throw new SilentException();

        // 2) Chuỗi xử lý màu sắc, ở mức -18 LUFS để các ngưỡng hoạt động ổn định
        double pre = Math.pow(10, (-18 - in[0]) / 20);
        Biquad[] hp1 = {Biquad.highPass(fs, 28, 0.5412), Biquad.highPass(fs, 28, 0.5412)};
        Biquad[] hp2 = {Biquad.highPass(fs, 28, 1.3066), Biquad.highPass(fs, 28, 1.3066)};
        Biquad[] bass = {Biquad.lowShelf(fs, 100, 0.7, s.bassDb), Biquad.lowShelf(fs, 100, 0.7, s.bassDb)};
        Biquad[] mud = {Biquad.peak(fs, 300, 1.0, s.mudDb), Biquad.peak(fs, 300, 1.0, s.mudDb)};
        Biquad[] pres = {Biquad.peak(fs, 3000, 0.9, s.presenceDb), Biquad.peak(fs, 3000, 0.9, s.presenceDb)};
        double airF = Math.min(10000, fs * 0.42);
        Biquad[] air = {Biquad.highShelf(fs, airF, 0.7, s.airDb), Biquad.highShelf(fs, airF, 0.7, s.airDb)};

        // nén glue (liên kết stereo)
        boolean useComp = s.comp > 0.01;
        double ratio = 1 + 2.5 * s.comp;
        double thr = -6 - 10 * s.comp;
        double knee = 6;
        double aAtt = Math.exp(-1.0 / (fs * 0.015));
        double aRel = Math.exp(-1.0 / (fs * 0.180));
        double env = 0;

        // bão hoà
        boolean useSat = s.warmth > 0.01;
        double drive = 1.0 + 1.5 * s.warmth;
        double bias = 0.12;
        double th0 = Math.tanh(drive * bias);
        double norm = 1.0 / (drive * (1 - th0 * th0));
        double mix = Math.min(1, s.warmth);
        // chặn DC sau bão hoà (bậc 1, 8 Hz)
        double dcR = Math.exp(-2 * Math.PI * 8 / fs);
        double dcXl = 0, dcYl = 0, dcXr = 0, dcYr = 0;

        // stereo: lọc thông cao bậc 1 cho kênh Side (giữ bass mono dưới ~110 Hz)
        double sideA = Math.exp(-2 * Math.PI * 110 / fs);
        double sX = 0, sY = 0;

        for (int i = 0; i < n; i++) {
            double l = L[i] * pre, r = R[i] * pre;
            l = air[0].process(pres[0].process(mud[0].process(bass[0].process(hp2[0].process(hp1[0].process(l))))));
            r = air[1].process(pres[1].process(mud[1].process(bass[1].process(hp2[1].process(hp1[1].process(r))))));

            if (useComp) {
                double x = Math.max(Math.abs(l), Math.abs(r));
                double xdb = 20 * Math.log10(x + 1e-9);
                double over = xdb - thr;
                double gr;
                if (over <= -knee / 2) gr = 0;
                else if (over >= knee / 2) gr = over * (1 / ratio - 1);
                else { double t = over + knee / 2; gr = (1 / ratio - 1) * t * t / (2 * knee); }
                if (gr < env) env = aAtt * env + (1 - aAtt) * gr;
                else env = aRel * env + (1 - aRel) * gr;
                double g = Math.pow(10, env / 20);
                l *= g; r *= g;
            }

            if (useSat) {
                double sl = (Math.tanh(drive * (l + bias)) - th0) * norm;
                double sr = (Math.tanh(drive * (r + bias)) - th0) * norm;
                l += mix * (sl - l);
                r += mix * (sr - r);
                double yl = l - dcXl + dcR * dcYl; dcXl = l; dcYl = yl; l = yl;
                double yr = r - dcXr + dcR * dcYr; dcXr = r; dcYr = yr; r = yr;
            }

            double m = 0.5 * (l + r), sd = 0.5 * (l - r);
            double sh = sideA * (sY + sd - sX);   // thông cao bậc 1
            sX = sd; sY = sh;
            sd = sh * s.width;
            L[i] = (float) (m + sd);
            R[i] = (float) (m - sd);

            if ((i & (STEP - 1)) == 0) tick(p, 0.12 + 0.30 * i / (double) n, "Cân bằng & nén");
        }

        // 3) Tìm độ lợi để đạt LUFS mục tiêu sau limiter (lặp, không ghi)
        double lp = measure(L, R, n, fs, p, 0.42, 0.50, "Đo sau xử lý")[0];
        double gainDb = s.targetLufs - lp;
        double internalCeil = s.ceilingDb - 0.1;
        int iters = 5;
        for (int it = 0; it < iters; it++) {
            double p0 = 0.50 + 0.064 * it;
            double[] out = runLimiter(L, R, n, fs, gainDb, internalCeil, false, p, p0, p0 + 0.064, "Tối ưu độ lớn");
            double corr = s.targetLufs - out[0];
            double tpOver = out[2] - s.ceilingDb;      // đỉnh thật đo chính xác so với trần
            boolean ok = Math.abs(corr) < 0.1 && tpOver <= 0;
            if (ok) break;
            if (tpOver > 0) internalCeil -= tpOver + 0.03;
            gainDb += corr;
            if (gainDb > 30) { gainDb = 30; }
        }

        // 4) Chạy limiter thật, ghi tại chỗ
        double[] fin = runLimiter(L, R, n, fs, gainDb, internalCeil, true, p, 0.82, 0.90, "Limiter");
        res.limiterGrDb = fin[1];

        // 5) Kiểm tra cuối: đo lại, nếu đỉnh thật vượt trần thì hạ nhẹ
        double[] o = measure(L, R, n, fs, p, 0.90, 0.99, "Kiểm tra");
        if (o[1] > s.ceilingDb) {
            float k = (float) Math.pow(10, (s.ceilingDb - o[1] - 0.02) / 20);
            for (int i = 0; i < n; i++) { L[i] *= k; R[i] *= k; }
            o = measure(L, R, n, fs, null, 0, 0, "");
        }
        res.outLufs = o[0];
        res.outTpDb = o[1];
        tick(p, 1, "Xong");
        return res;
    }

    /** @return {LUFS đầu ra, mức giảm lớn nhất của limiter (dB), true peak đầu ra (dBTP)} */
    private static double[] runLimiter(float[] L, float[] R, int n, double fs, double gainDb, double ceilDb,
                                       boolean write, Progress p, double p0, double p1, String stage) {
        float g = (float) Math.pow(10, gainDb / 20);
        Limiter lim = new Limiter(fs, ceilDb, 3.0, 90);
        LoudnessMeter m = write ? null : new LoudnessMeter(fs);
        int lat = lim.getLatency();
        for (int i = 0; i < n + lat; i++) {
            float l = i < n ? L[i] * g : 0f, r = i < n ? R[i] * g : 0f;
            lim.process(l, r);
            int o = i - lat;
            if (o >= 0) {
                if (write) { L[o] = lim.outL; R[o] = lim.outR; }
                else m.add(lim.outL, lim.outR);
            }
            if ((i & (STEP - 1)) == 0) tick(p, p0 + (p1 - p0) * i / (double) (n + lat), stage);
        }
        return m != null
                ? new double[]{m.integrated(), lim.maxReductionDb(), m.truePeakDb()}
                : new double[]{0, lim.maxReductionDb(), 0};
    }
}

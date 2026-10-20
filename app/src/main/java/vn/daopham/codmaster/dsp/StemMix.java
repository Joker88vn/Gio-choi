package vn.daopham.codmaster.dsp;

/**
 * Chế độ 2 stem: giọng hát + nhạc nền.
 *
 * Giọng:   cắt trầm → bớt đục → nén giọng (RMS) → khử xì (dynamic, chỉ dải cao) → rõ giọng → độ sáng → vang (plate).
 * Nhạc nền: cắt siêu trầm → trầm → "nhường chỗ cho giọng" (EQ động 1–4.5 kHz theo giọng) → độ rộng (bass giữ mono).
 * Trộn:    mỗi stem giữ nguyên độ lớn như bản gốc (bù sau xử lý) rồi cộng/trừ theo thanh "to/nhỏ",
 *          sau đó đưa qua chuỗi master tổng (Mastering) như bình thường.
 */
public final class StemMix {
    private StemMix() {}

    public static final class StemSettings {
        public double vocalDb = 0;       // giọng to/nhỏ so với cân bằng gốc
        public double vocalHpf = 90;     // Hz
        public double vocalComp = 0.45;  // 0..1
        public double deEss = 0.4;       // 0..1
        public double vocalPresence = 1.5;
        public double vocalAir = 1.5;
        public double vocalMud = -1.5;
        public double reverb = 0.1;      // 0..1
        public double beatDb = 0;
        public double pocket = 2.0;      // dB tối đa nhường chỗ
        public double beatBass = 0;
        public double beatWidth = 1.1;
        public double deReverb = 0.5;    // 0..1 khử vang có sẵn trong giọng (Suno)
        public double deDelay = 0.6;     // 0..1 khử delay có sẵn trong giọng
        public boolean keepVocal;        // giữ lại giọng đã xử lý để nghe riêng

        public StemSettings copy() {
            StemSettings s = new StemSettings();
            s.vocalDb = vocalDb; s.vocalHpf = vocalHpf; s.vocalComp = vocalComp; s.deEss = deEss;
            s.vocalPresence = vocalPresence; s.vocalAir = vocalAir; s.vocalMud = vocalMud; s.reverb = reverb;
            s.beatDb = beatDb; s.pocket = pocket; s.beatBass = beatBass; s.beatWidth = beatWidth;
            s.deReverb = deReverb; s.deDelay = deDelay; s.keepVocal = keepVocal;
            return s;
        }

        public boolean sameAs(StemSettings o) {
            return o != null && vocalDb == o.vocalDb && vocalHpf == o.vocalHpf && vocalComp == o.vocalComp
                    && deEss == o.deEss && vocalPresence == o.vocalPresence && vocalAir == o.vocalAir
                    && vocalMud == o.vocalMud && reverb == o.reverb && beatDb == o.beatDb && pocket == o.pocket
                    && beatBass == o.beatBass && beatWidth == o.beatWidth
                    && deReverb == o.deReverb && deDelay == o.deDelay;
        }
    }

    public static final class Result {
        public Mastering.Result master;
        public double vocalLufs, beatLufs;       // stem gốc
        public double maxDeEssDb, maxPocketDb;   // mức tác động lớn nhất
        public VocalDeverb.Analysis deverb;      // kết quả đo vang/delay của giọng gốc
        public float[] vocalL, vocalR;           // giọng đã xử lý (cùng độ lớn như giọng gốc) — nếu keepVocal
    }

    private static final int STEP = 65536;

    private static void tick(Mastering.Progress p, double f, String s) {
        if (p != null && !p.onProgress(Math.min(1, Math.max(0, f)), s)) throw new Mastering.CancelledException();
    }

    private static double db(double x) { return 20 * Math.log10(Math.max(x, 1e-12)); }
    private static double lin(double d) { return Math.pow(10, d / 20); }

    /**
     * Xử lý và trộn 2 stem vào outL/outR (độ dài n), rồi master tổng.
     * Mảng stem đầu vào không bị thay đổi.
     */
    public static Result process(float[] vL, float[] vR, float[] bL, float[] bR, int n, double fs,
                                 StemSettings ss, Mastering.Settings bus, float[] outL, float[] outR,
                                 Mastering.Progress p) {
        return run(vL, vR, bL, bR, n, fs, ss, bus, outL, outR, p, false);
    }

    /**
     * Bản tiết kiệm bộ nhớ (điện thoại): ghi đè lên mảng đầu vào.
     * Bản master nằm trong bL/bR; nếu keepVocal thì giọng đã xử lý nằm trong vL/vR (res.vocalL/R trỏ vào đó).
     */
    public static Result processInPlace(float[] vL, float[] vR, float[] bL, float[] bR, int n, double fs,
                                        StemSettings ss, Mastering.Settings bus, Mastering.Progress p) {
        return run(vL, vR, bL, bR, n, fs, ss, bus, bL, bR, p, true);
    }

    private static Result run(float[] vL, float[] vR, float[] bL, float[] bR, int n, double fs,
                              StemSettings ss, Mastering.Settings bus, float[] outL, float[] outR,
                              Mastering.Progress p, boolean inPlace) {
        Result res = new Result();

        // 0) Đo bản gốc: từng stem và tổng thô
        LoudnessMeter mv = new LoudnessMeter(fs), mb = new LoudnessMeter(fs), mm = new LoudnessMeter(fs);
        for (int i = 0; i < n; i++) {
            mv.add(vL[i], vR[i]);
            mb.add(bL[i], bR[i]);
            mm.add(vL[i] + bL[i], vR[i] + bR[i]);
            if ((i & (STEP - 1)) == 0) tick(p, 0.10 * i / n, "Đo 2 stem");
        }
        double lv0 = mv.integrated(), lb0 = mb.integrated();
        double mixIn = mm.integrated(), mixInTp = mm.truePeakDb();
        res.vocalLufs = lv0;
        res.beatLufs = lb0;
        boolean hasVocal = lv0 > -60, hasBeat = lb0 > -60;
        if (!hasVocal && !hasBeat) throw new Mastering.SilentException();

        float[] wvL = inPlace ? vL : new float[n], wvR = inPlace ? vR : new float[n];

        // 1) Chuỗi giọng hát (ở mức −20 LUFS để ngưỡng ổn định)
        if (hasVocal) {
            if (!inPlace) {
                System.arraycopy(vL, 0, wvL, 0, n);
                System.arraycopy(vR, 0, wvR, 0, n);
            }
            // 1a) khử vang + delay có sẵn (trước mọi xử lý khác, vì nén sẽ làm đuôi vang to lên)
            res.deverb = VocalDeverb.process(wvL, wvR, n, fs, ss.deReverb, ss.deDelay, p, 0.10, 0.21);
            double lvd = (ss.deReverb > 0.01 || ss.deDelay > 0.01)
                    ? Mastering.measure(wvL, wvR, n, fs, p, 0.21, 0.22, "Đo giọng sau khử vang")[0] : lv0;
            if (lvd < -70) lvd = lv0;
            double pre = lin(-20 - lvd);
            Biquad[] hp1 = {Biquad.highPass(fs, ss.vocalHpf, 0.5412), Biquad.highPass(fs, ss.vocalHpf, 0.5412)};
            Biquad[] hp2 = {Biquad.highPass(fs, ss.vocalHpf, 1.3066), Biquad.highPass(fs, ss.vocalHpf, 1.3066)};
            Biquad[] mud = {Biquad.peak(fs, 280, 1.1, ss.vocalMud), Biquad.peak(fs, 280, 1.1, ss.vocalMud)};
            Biquad[] pres = {Biquad.peak(fs, 3200, 0.9, ss.vocalPresence), Biquad.peak(fs, 3200, 0.9, ss.vocalPresence)};
            double airF = Math.min(11000, fs * 0.42);
            Biquad[] air = {Biquad.highShelf(fs, airF, 0.7, ss.vocalAir), Biquad.highShelf(fs, airF, 0.7, ss.vocalAir)};
            Biquad[] esHp = {Biquad.highPass(fs, Math.min(5200, fs * 0.4), 0.707), Biquad.highPass(fs, Math.min(5200, fs * 0.4), 0.707)};

            // nén giọng (RMS, liên kết stereo)
            boolean useComp = ss.vocalComp > 0.01;
            double cRatio = 1.5 + 3.0 * ss.vocalComp, cThr = -21 - 9 * ss.vocalComp;
            double aRms = Math.exp(-1.0 / (fs * 0.010));
            double cAtt = Math.exp(-1.0 / (fs * 0.005)), cRel = Math.exp(-1.0 / (fs * 0.110));
            double rms = 0, cEnv = 0;

            // khử xì
            boolean useEs = ss.deEss > 0.01;
            double esThr = -30 - 12 * ss.deEss;            // dBFS của dải xì
            double esAtt = Math.exp(-1.0 / (fs * 0.0008)), esRelDet = Math.exp(-1.0 / (fs * 0.030));
            double esGAtt = Math.exp(-1.0 / (fs * 0.001)), esGRel = Math.exp(-1.0 / (fs * 0.060));
            double esDet = 0, esG = 0;
            double maxEs = 0;

            for (int i = 0; i < n; i++) {
                double l = wvL[i] * pre, r = wvR[i] * pre;
                l = mud[0].process(hp2[0].process(hp1[0].process(l)));
                r = mud[1].process(hp2[1].process(hp1[1].process(r)));

                if (useComp) {
                    double x2 = 0.5 * (l * l + r * r);
                    rms = aRms * rms + (1 - aRms) * x2;
                    double lvl = 10 * Math.log10(rms + 1e-12);
                    double over = lvl - cThr, gr = 0;
                    if (over > 3) gr = over * (1 / cRatio - 1);
                    else if (over > -3) { double t = over + 3; gr = (1 / cRatio - 1) * t * t / 12; }
                    cEnv = gr < cEnv ? cAtt * cEnv + (1 - cAtt) * gr : cRel * cEnv + (1 - cRel) * gr;
                    double g = lin(cEnv);
                    l *= g; r *= g;
                }

                if (useEs) {
                    double hl = esHp[0].process(l), hr = esHp[1].process(r);
                    double a = Math.max(Math.abs(hl), Math.abs(hr));
                    esDet = a > esDet ? esAtt * esDet + (1 - esAtt) * a : esRelDet * esDet + (1 - esRelDet) * a;
                    double over = db(esDet) - esThr;
                    double gr = over > 0 ? Math.max(-10, -over * 0.75) : 0;   // tỉ lệ 4:1, tối đa −10 dB
                    esG = gr < esG ? esGAtt * esG + (1 - esGAtt) * gr : esGRel * esG + (1 - esGRel) * gr;
                    if (-esG > maxEs) maxEs = -esG;
                    double k = 1 - lin(esG);
                    l -= k * hl;
                    r -= k * hr;
                } else {
                    esHp[0].process(l); esHp[1].process(r);
                }

                l = air[0].process(pres[0].process(l));
                r = air[1].process(pres[1].process(r));
                wvL[i] = (float) l;
                wvR[i] = (float) r;
                if ((i & (STEP - 1)) == 0) tick(p, 0.22 + 0.03 * i / n, "Xử lý giọng hát");
            }
            res.maxDeEssDb = -maxEs;

            if (ss.reverb > 0.01) addReverb(wvL, wvR, n, fs, ss.reverb, p);
        }

        // 2) Chuỗi nhạc nền, ghi thẳng vào out
        double maxPocket = 0;
        if (hasBeat) {
            Biquad[] hp = {Biquad.highPass(fs, 28, 0.707), Biquad.highPass(fs, 28, 0.707)};
            Biquad[] bass = {Biquad.lowShelf(fs, 95, 0.7, ss.beatBass), Biquad.lowShelf(fs, 95, 0.7, ss.beatBass)};
            double pf = Math.min(2600, fs * 0.3);
            Biquad[] pk = {Biquad.peak(fs, pf, 0.75, 0), Biquad.peak(fs, pf, 0.75, 0)};
            boolean usePocket = hasVocal && ss.pocket > 0.05;
            double aV = Math.exp(-1.0 / (fs * 0.030));
            double actAtt = Math.exp(-1.0 / (fs * 0.020)), actRel = Math.exp(-1.0 / (fs * 0.250));
            double vRms = 0, act = 0;
            double sideA = Math.exp(-2 * Math.PI * 110 / fs);
            double sX = 0, sY = 0;
            for (int i = 0; i < n; i++) {
                double l = bass[0].process(hp[0].process(bL[i]));
                double r = bass[1].process(hp[1].process(bR[i]));
                if (usePocket) {
                    double vl = wvL[i], vr = wvR[i];
                    vRms = aV * vRms + (1 - aV) * 0.5 * (vl * vl + vr * vr);
                    double vdb = 10 * Math.log10(vRms + 1e-12);           // giọng ở ~ −20 LUFS
                    double target = Math.max(0, Math.min(1, (vdb + 42) / 20)); // −42 dB → 0, −22 dB → 1
                    act = target > act ? actAtt * act + (1 - actAtt) * target : actRel * act + (1 - actRel) * target;
                    if ((i & 31) == 0) {
                        double g = -ss.pocket * act;
                        if (-g > maxPocket) maxPocket = -g;
                        pk[0].setPeak(fs, pf, 0.75, g);
                        pk[1].setPeak(fs, pf, 0.75, g);
                    }
                    l = pk[0].process(l);
                    r = pk[1].process(r);
                }
                double m = 0.5 * (l + r), sd = 0.5 * (l - r);
                double sh = sideA * (sY + sd - sX);
                sX = sd; sY = sh;
                sd = sh * ss.beatWidth;
                outL[i] = (float) (m + sd);
                outR[i] = (float) (m - sd);
                if ((i & (STEP - 1)) == 0) tick(p, 0.25 + 0.10 * i / n, "Xử lý nhạc nền");
            }
        } else {
            java.util.Arrays.fill(outL, 0, n, 0f);
            java.util.Arrays.fill(outR, 0, n, 0f);
        }
        res.maxPocketDb = -maxPocket;

        // 3) Giữ độ lớn từng stem như bản gốc, rồi cộng/trừ theo ý người dùng
        double gv = 0, gb = 1;
        if (hasVocal) {
            double lv1 = Mastering.measure(wvL, wvR, n, fs, p, 0.35, 0.39, "Cân bằng giọng")[0];
            gv = lin(lv0 - lv1 + ss.vocalDb);
        }
        if (hasBeat) {
            double lb1 = Mastering.measure(outL, outR, n, fs, p, 0.39, 0.43, "Cân bằng nhạc nền")[0];
            gb = lin(lb0 - lb1 + ss.beatDb);
        }
        float fv = (float) gv, fb = (float) gb;
        for (int i = 0; i < n; i++) {
            outL[i] = outL[i] * fb + wvL[i] * fv;
            outR[i] = outR[i] * fb + wvR[i] * fv;
        }
        if (ss.keepVocal && hasVocal) {
            // bản nghe riêng: giọng đã xử lý, cùng độ lớn với giọng gốc (dùng lại mảng, không cấp thêm)
            float fk = (float) lin(-ss.vocalDb) * fv;
            for (int i = 0; i < n; i++) { wvL[i] *= fk; wvR[i] *= fk; }
            res.vocalL = wvL;
            res.vocalR = wvR;
        }
        wvL = null; wvR = null;

        // 4) Master tổng — EQ tổng nhẹ tay hơn vì đã chỉnh riêng từng stem
        Mastering.Settings b = bus.copy();
        b.bassDb *= 0.5; b.mudDb *= 0.5; b.presenceDb *= 0.4; b.airDb *= 0.5;
        b.width = 1 + (b.width - 1) * 0.5;
        final double base = 0.43;
        Mastering.Result mr = Mastering.process(outL, outR, n, fs, b,
                p == null ? null : (f, s) -> p.onProgress(base + (1 - base) * f, s));
        mr.inLufs = mixIn;
        mr.inTpDb = mixInTp;
        res.master = mr;
        return res;
    }

    /** Vang kiểu plate (Freeverb: 8 comb + 4 allpass mỗi kênh), có pre-delay và lọc gọn dải. */
    static void addReverb(float[] L, float[] R, int n, double fs, double amount, Mastering.Progress p) {
        double sc = fs / 44100.0;
        int[] combT = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        int[] apT = {556, 441, 341, 225};
        int spread = 23;
        double room = 0.80 + 0.06 * amount, damp = 0.35, fbAp = 0.5;
        float[][] cbL = new float[8][], cbR = new float[8][];
        int[] ciL = new int[8], ciR = new int[8];
        double[] fsL = new double[8], fsR = new double[8];
        for (int c = 0; c < 8; c++) {
            cbL[c] = new float[(int) (combT[c] * sc)];
            cbR[c] = new float[(int) ((combT[c] + spread) * sc)];
        }
        float[][] abL = new float[4][], abR = new float[4][];
        int[] aiL = new int[4], aiR = new int[4];
        for (int a = 0; a < 4; a++) {
            abL[a] = new float[(int) (apT[a] * sc)];
            abR[a] = new float[(int) ((apT[a] + spread) * sc)];
        }
        int pdN = (int) (fs * 0.028);
        float[] pd = new float[Math.max(1, pdN)];
        int pdi = 0;
        Biquad sendHp = Biquad.highPass(fs, 220, 0.707);
        Biquad sendLp = Biquad.highShelf(fs, Math.min(7000, fs * 0.4), 0.7, -6);
        double wet = 0.06 * amount;   // tỉ lệ nhỏ: vang ngồi phía sau giọng
        for (int i = 0; i < n; i++) {
            double in = 0.5 * (L[i] + R[i]);
            double x = pd[pdi];
            pd[pdi] = (float) sendLp.process(sendHp.process(in));
            if (++pdi == pd.length) pdi = 0;
            double inp = x * 0.015 * 20;   // fixed gain Freeverb, chuẩn hoá
            double oL = 0, oR = 0;
            for (int c = 0; c < 8; c++) {
                float[] b = cbL[c];
                double y = b[ciL[c]];
                fsL[c] = y * (1 - damp) + fsL[c] * damp;
                b[ciL[c]] = (float) (inp + fsL[c] * room);
                if (++ciL[c] == b.length) ciL[c] = 0;
                oL += y;
                float[] bb = cbR[c];
                double yr = bb[ciR[c]];
                fsR[c] = yr * (1 - damp) + fsR[c] * damp;
                bb[ciR[c]] = (float) (inp + fsR[c] * room);
                if (++ciR[c] == bb.length) ciR[c] = 0;
                oR += yr;
            }
            for (int a = 0; a < 4; a++) {
                float[] b = abL[a];
                double bo = b[aiL[a]];
                b[aiL[a]] = (float) (oL + bo * fbAp);
                oL = bo - oL;
                if (++aiL[a] == b.length) aiL[a] = 0;
                float[] bb = abR[a];
                double bo2 = bb[aiR[a]];
                bb[aiR[a]] = (float) (oR + bo2 * fbAp);
                oR = bo2 - oR;
                if (++aiR[a] == bb.length) aiR[a] = 0;
            }
            L[i] += (float) (oL * wet);
            R[i] += (float) (oR * wet);
            if ((i & (STEP - 1)) == 0) tick(p, 0.25, "Thêm vang cho giọng");
        }
    }
}

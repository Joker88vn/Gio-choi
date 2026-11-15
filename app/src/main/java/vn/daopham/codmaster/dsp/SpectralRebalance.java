package vn.daopham.codmaster.dsp;

/**
 * Cân bằng lại dải âm — sửa các lỗi nghe thấy hay gặp ở nhạc tạo bằng AI (Suno, Udio…):
 *  1) Tông lệch: các "bướu" (đục 300–600 Hz, chói 2–5 kHz…) so với đường xu hướng mượt của chính bài,
 *     và độ nghiêng phổ quá sáng / quá tối → EQ tĩnh nhẹ, tự tính.
 *  2) Cộng hưởng đứng yên ở dải cao (tiếng "rít kim loại" kéo dài cả bài) → cắt hẹp đúng chỗ.
 *  3) Tiếng lấp lánh rè rè ở dải cao (thoắt to thoắt nhỏ) → làm dịu những chỗ trồi lên đột ngột,
 *     chừa lại tiếng gõ, tiếng cymbal thật.
 * Xử lý STFT tại chỗ (không cấp mảng cỡ cả bài) — chạy được trên điện thoại.
 */
public final class SpectralRebalance {
    private SpectralRebalance() {}

    public static final class Report {
        public double[] bandHz;
        public double[] corrDb;          // EQ tĩnh đã áp theo dải 1/3 octave
        public int resonances;           // số cộng hưởng dải cao đã cắt
        public double maxResDb;          // cắt sâu nhất (dB, âm)
        public double tiltBefore;        // độ nghiêng phổ dB/octave (trước)
        public double shimmerMaxDb;      // mức làm dịu lấp lánh lớn nhất (dB, âm)

        /** Tóm tắt ngắn để hiện trên giao diện. */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            java.util.List<int[]> picks = new java.util.ArrayList<>();
            for (int i = 0; i < corrDb.length; i++) picks.add(new int[]{i});
            picks.sort((a, b) -> Double.compare(Math.abs(corrDb[b[0]]), Math.abs(corrDb[a[0]])));
            int shown = 0;
            java.util.List<Integer> idx = new java.util.ArrayList<>();
            for (int[] pk : picks) {
                if (shown >= 3 || Math.abs(corrDb[pk[0]]) < 0.5) break;
                boolean near = false;
                for (int j : idx) if (Math.abs(j - pk[0]) < 3) near = true;
                if (near) continue;
                idx.add(pk[0]);
                shown++;
            }
            java.util.Collections.sort(idx);
            for (int i : idx) {
                if (sb.length() > 0) sb.append(" · ");
                double f = bandHz[i];
                String fs = f >= 1000 ? String.format(java.util.Locale.US, "%.1fk", f / 1000).replace(".0k", "k") : String.valueOf((int) Math.round(f));
                sb.append(fs).append(" ").append(String.format(java.util.Locale.US, "%+.1f", corrDb[i]).replace('-', '−'));
            }
            if (sb.length() == 0) sb.append("tông đã cân");
            if (resonances > 0) sb.append(" · ").append(resonances).append(" cộng hưởng");
            if (shimmerMaxDb < -0.5) sb.append(" · dịu rè cao");
            return sb.toString();
        }
    }

    // ───────────── FFT radix-2 ─────────────
    static final class FFT {
        final int n;
        final int[] rev;
        final double[] cs, sn;

        FFT(int n) {
            this.n = n;
            rev = new int[n];
            int bits = Integer.numberOfTrailingZeros(n);
            for (int i = 0; i < n; i++) rev[i] = Integer.reverse(i) >>> (32 - bits);
            cs = new double[n / 2];
            sn = new double[n / 2];
            for (int k = 0; k < n / 2; k++) {
                cs[k] = Math.cos(2 * Math.PI * k / n);
                sn[k] = Math.sin(2 * Math.PI * k / n);
            }
        }

        void run(double[] re, double[] im, boolean inverse) {
            for (int i = 0; i < n; i++) {
                int j = rev[i];
                if (i < j) {
                    double t = re[i]; re[i] = re[j]; re[j] = t;
                    t = im[i]; im[i] = im[j]; im[j] = t;
                }
            }
            for (int len = 2; len <= n; len <<= 1) {
                int half = len >> 1, step = n / len;
                for (int i = 0; i < n; i += len) {
                    for (int k = 0, w = 0; k < half; k++, w += step) {
                        double wr = cs[w], wi = inverse ? sn[w] : -sn[w];
                        int a = i + k, b = a + half;
                        double xr = re[b] * wr - im[b] * wi, xi = re[b] * wi + im[b] * wr;
                        re[b] = re[a] - xr; im[b] = im[a] - xi;
                        re[a] += xr; im[a] += xi;
                    }
                }
            }
            if (inverse) {
                double s = 1.0 / n;
                for (int i = 0; i < n; i++) { re[i] *= s; im[i] *= s; }
            }
        }
    }

    private static void tick(Mastering.Progress p, double p0, double p1, double f, String s) {
        if (p != null && !p.onProgress(p0 + (p1 - p0) * Math.max(0, Math.min(1, f)), s))
            throw new Mastering.CancelledException();
    }

    static final double TARGET_TILT = -3.5;   // dB/octave (công suất theo dải 1/3 octave) — phổ bài hát thương mại điển hình

    /**
     * @param amount 0..1 mức can thiệp
     */
    public static Report process(float[] L, float[] R, int n, double fs, double amount,
                                 Mastering.Progress p, double p0, double p1) {
        Report rep = new Report();
        double amt = Math.max(0, Math.min(1, amount));
        final int N = fs > 60000 ? 8192 : 4096;
        final int hop = N / 4, K = N / 2 + 1;
        final double hopSec = hop / fs, binHz = fs / N;
        final FFT fft = new FFT(N);
        final double[] win = new double[N];
        for (int i = 0; i < N; i++) win[i] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
        final int M = (n + N) / hop + 1;
        final double[] re = new double[N], im = new double[N];
        final double pMid = p0 + (p1 - p0) * 0.4;

        // ── 1) Phân tích: phổ trung bình dài hạn (chỉ các khung có nhạc) ──
        double[] ltas = new double[K];
        double[] fe = new double[M];
        double maxE = 0;
        // lượt 1a: năng lượng khung
        for (int m = 0; m < M; m++) {
            int start = m * hop - N;
            double e = 0;
            for (int i = 0; i < N; i += 8) {
                int s = start + i;
                if (s >= 0 && s < n) { double x = 0.5 * (L[s] + R[s]); e += x * x; }
            }
            fe[m] = e;
            if (e > maxE) maxE = e;
        }
        int used = 0;
        for (int m = 0; m < M; m++) {
            if (fe[m] < maxE * 1e-4) continue;           // bỏ khung im lặng (< −40 dB)
            int start = m * hop - N;
            for (int i = 0; i < N; i++) {
                int s = start + i;
                re[i] = s >= 0 && s < n ? 0.5 * (L[s] + R[s]) * win[i] : 0;
                im[i] = 0;
            }
            fft.run(re, im, false);
            for (int k = 0; k < K; k++) ltas[k] += re[k] * re[k] + im[k] * im[k];
            used++;
            if ((m & 127) == 0) tick(p, p0, pMid, (double) m / M, "Phân tích dải âm");
        }
        if (used < 8) { rep.bandHz = new double[0]; rep.corrDb = new double[0]; return rep; }
        double[] lt = new double[K];
        for (int k = 0; k < K; k++) lt[k] = 10 * Math.log10(ltas[k] / used + 1e-20);

        // dải 1/3 octave 63 Hz … 16 kHz
        java.util.List<Double> centers = new java.util.ArrayList<>();
        for (double f = 63; f <= Math.min(16000, fs * 0.45); f *= Math.pow(2, 1.0 / 3)) centers.add(f);
        int B = centers.size();
        double[] fc = new double[B], lev = new double[B];
        for (int b = 0; b < B; b++) {
            fc[b] = centers.get(b);
            int k0 = Math.max(1, (int) Math.floor(fc[b] / Math.pow(2, 1.0 / 6) / binHz));
            int k1 = Math.min(K - 1, (int) Math.ceil(fc[b] * Math.pow(2, 1.0 / 6) / binHz));
            double s = 0;
            for (int k = k0; k <= k1; k++) s += ltas[k] / used;
            lev[b] = 10 * Math.log10(s + 1e-20);
        }
        // đường xu hướng (hồi quy tuyến tính theo log tần số) trên 100 Hz – 10 kHz
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int c = 0;
        for (int b = 0; b < B; b++) {
            if (fc[b] < 100 || fc[b] > 10500) continue;
            double x = Math.log(fc[b] / 1000) / Math.log(2);
            sx += x; sy += lev[b]; sxx += x * x; sxy += x * lev[b]; c++;
        }
        double tilt = (c * sxy - sx * sy) / (c * sxx - sx * sx), icpt = (sy - tilt * sx) / c;
        rep.tiltBefore = tilt;

        // bướu/lõm so với xu hướng (làm mượt 3 dải) + kéo độ nghiêng về mức điển hình
        double[] dev = new double[B];
        for (int b = 0; b < B; b++) dev[b] = lev[b] - (icpt + tilt * Math.log(fc[b] / 1000) / Math.log(2));
        double[] devS = new double[B];
        for (int b = 0; b < B; b++) {
            double s = 0, w = 0;
            for (int d = -1; d <= 1; d++) {
                int q = b + d;
                if (q < 0 || q >= B) continue;
                double ww = d == 0 ? 2 : 1;
                s += dev[q] * ww; w += ww;
            }
            devS[b] = s / w;
        }
        double[] corr = new double[B];
        double hfLev = lev[B - 1], ref12 = lev[Math.max(0, B - 2)];
        for (int b = 0; b < B; b++) {
            double x = Math.log(fc[b] / 1000) / Math.log(2);
            double bump = -Math.max(-3, Math.min(3, devS[b])) * 0.8;
            double tiltFix = Math.max(-2, Math.min(2, (TARGET_TILT - tilt) * x * 0.3));
            double v = (bump + tiltFix) * amt;
            v = Math.max(-4 * amt, Math.min(4 * amt, v));
            if (fc[b] < 100) v *= Math.max(0, (fc[b] - 63) / 37);       // trầm sâu: để thanh "Trầm" lo
            if (fc[b] > 11000 && v > 0 && hfLev < ref12 - 12) v = 0;   // không đẩy dải cao đã bị cắt (chỉ khuếch nhiễu)
            corr[b] = v;
        }
        rep.bandHz = fc;
        rep.corrDb = corr;

        // gain tĩnh theo từng khe tần số (nội suy theo log tần số)
        double[] Gs = new double[K];
        for (int k = 0; k < K; k++) {
            double f = Math.max(fc[0], Math.min(fc[B - 1], k * binHz));
            double x = Math.log(f / fc[0]) / Math.log(2) * 3;
            int b = Math.min(B - 2, (int) Math.floor(x));
            double t = Math.max(0, Math.min(1, x - b));
            double db = corr[b] * (1 - t) + corr[b + 1] * t;
            if (k * binHz < fc[0]) db = corr[0] * Math.max(0, (k * binHz - 30) / (fc[0] - 30));
            Gs[k] = db;
        }

        // ── 2) Cộng hưởng đứng yên ở dải cao (4–14 kHz): đỉnh hẹp nhô trên phổ dài hạn đã làm mượt ──
        int kr0 = (int) Math.round(4000 / binHz), kr1 = Math.min(K - 2, (int) Math.round(Math.min(14000, fs * 0.42) / binHz));
        double maxRes = 0;
        int nres = 0;
        for (int k = kr0; k <= kr1; k++) {
            int w = Math.max(4, (int) Math.round(k * 0.06));          // ~1/6 octave
            double s = 0;
            int cnt = 0;
            for (int d = -w; d <= w; d++) {
                if (Math.abs(d) <= 2) continue;
                int q = k + d;
                if (q < 1 || q >= K) continue;
                s += lt[q]; cnt++;
            }
            double fine = lt[k] - s / cnt;
            boolean peak = lt[k] >= lt[k - 1] && lt[k] >= lt[k + 1];
            // vạch hẹp (tiếng rít đứng yên), không phải bướu rộng của nhạc cụ
            boolean narrow = k > 3 && k + 3 < K && lt[k] - Math.max(lt[k - 3], lt[k + 3]) > 3;
            if (peak && narrow && fine > 4) {
                double cut = -Math.min(12, (fine - 3) * 0.9) * amt;
                Gs[k] += cut;
                Gs[k - 1] += cut * 0.6;
                Gs[k + 1] += cut * 0.6;
                if (k > 1) Gs[k - 2] += cut * 0.25;
                if (k + 2 < K) Gs[k + 2] += cut * 0.25;
                nres++;
                maxRes = Math.min(maxRes, cut);
            }
        }
        rep.resonances = nres;
        rep.maxResDb = maxRes;
        final double[] Glin = new double[K];
        for (int k = 0; k < K; k++) Glin[k] = Math.pow(10, Gs[k] / 20);
        tick(p, p0, pMid, 1, "Phân tích dải âm");

        // ── 3) Xử lý: EQ tĩnh + làm dịu lấp lánh dải cao ──
        int ks0 = (int) Math.round(5000 / binHz), ks1 = Math.min(K - 1, (int) Math.round(Math.min(16000, fs * 0.45) / binHz));
        int ksN = ks1 - ks0 + 1;
        double[] slow = new double[ksN];            // mức trung bình chậm (~250 ms) từng khe dải cao
        double[] gsh = new double[ksN];             // gain làm dịu (dB) hiện tại
        double aSlow = Math.exp(-hopSec / 0.25), relS = Math.exp(-hopSec / 0.06);
        double shDepth = 3.5 * amt;
        double prevHf = 0;
        double minSh = 0;
        double[] accL = new double[N], accR = new double[N];
        final double ola = 0.5;

        for (int m = 0; m < M; m++) {
            int start = m * hop - N;
            for (int i = 0; i < N; i++) {
                int s = start + i;
                if (s >= 0 && s < n) { re[i] = L[s] * win[i]; im[i] = R[s] * win[i]; }
                else { re[i] = 0; im[i] = 0; }
            }
            fft.run(re, im, false);
            // tách L, R từ X = FFT(l + i r)
            double hf = 0;
            for (int k = ks0; k <= ks1; k++) {
                int nk = (N - k) & (N - 1);
                double ar = re[k], ai = im[k], br = re[nk], bi = -im[nk];
                double lr = 0.5 * (ar + br), li = 0.5 * (ai + bi), rr = 0.5 * (ai - bi), ri = -0.5 * (ar - br);
                hf += lr * lr + li * li + rr * rr + ri * ri;
            }
            boolean onset = hf > prevHf * 4 && hf > 1e-12;   // tiếng gõ/cymbal mới vang lên: không đụng
            prevHf = hf;
            double[] G = new double[K];
            for (int k = 0; k < K; k++) G[k] = Glin[k];
            if (shDepth > 0.05) {
                for (int j = 0; j < ksN; j++) {
                    int k = ks0 + j, nk = (N - k) & (N - 1);
                    double ar = re[k], ai = im[k], br = re[nk], bi = -im[nk];
                    double lr = 0.5 * (ar + br), li = 0.5 * (ai + bi), rr = 0.5 * (ai - bi), ri = -0.5 * (ar - br);
                    double pw = 0.5 * (lr * lr + li * li + rr * rr + ri * ri);
                    double over = slow[j] > 0 ? 10 * Math.log10((pw + 1e-20) / (slow[j] + 1e-20)) : 0;
                    double tgt = (!onset && over > 6) ? -Math.min(shDepth, (over - 6) * 0.5) : 0;
                    gsh[j] = tgt < gsh[j] ? tgt : relS * gsh[j] + (1 - relS) * tgt;
                    slow[j] = slow[j] == 0 ? pw : aSlow * slow[j] + (1 - aSlow) * pw;
                    if (gsh[j] < minSh) minSh = gsh[j];
                    G[k] *= Math.pow(10, gsh[j] / 20);
                }
            }
            // áp gain cho cả hai kênh: Y = G·X đối xứng (G thực) → giữ nguyên pha và ảnh stereo
            for (int k = 0; k < K; k++) {
                double g = G[k];
                re[k] *= g; im[k] *= g;
                if (k > 0 && k < N / 2) { re[N - k] *= g; im[N - k] *= g; }
            }
            fft.run(re, im, true);
            for (int i = 0; i < N; i++) {
                accL[i] += re[i] * win[i] * ola;
                accR[i] += im[i] * win[i] * ola;
            }
            for (int i = 0; i < hop; i++) {
                int s = start + i;
                if (s >= 0 && s < n) { L[s] = (float) accL[i]; R[s] = (float) accR[i]; }
            }
            System.arraycopy(accL, hop, accL, 0, N - hop);
            System.arraycopy(accR, hop, accR, 0, N - hop);
            java.util.Arrays.fill(accL, N - hop, N, 0);
            java.util.Arrays.fill(accR, N - hop, N, 0);
            if ((m & 127) == 0) tick(p, pMid, p1, (double) m / M, "Cân bằng dải âm");
        }
        rep.shimmerMaxDb = minSh;
        tick(p, pMid, p1, 1, "Cân bằng dải âm");
        return rep;
    }
}

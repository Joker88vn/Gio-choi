package vn.daopham.codmaster.dsp;

/**
 * Khử vang + khử delay cho stem giọng hát (không dùng AI).
 *
 * 1) Phân tích: đo độ dài vang (T60) và mức đuôi vang từ các điểm kết thúc câu hát;
 *    dò delay bằng cách tìm độ trễ mà ở đó cấu trúc hài âm của giọng bị lặp lại y hệt nhưng nhỏ hơn.
 * 2) Xử lý (STFT 2048, chồng 75 %): ước lượng phổ vang muộn bằng mô hình suy giảm mũ trên phần giọng khô
 *    đã ước lượng, cộng phổ tiếng dội = g²·phổ của D giây trước; trừ phổ (giới hạn sàn), làm mượt khe tần số
 *    và thời gian (lên tức thì, xuống ~15 ms) để không sinh tiếng "lạo xạo"; vang thường rộng stereo nên phần
 *    Side được hạ thêm.
 */
public final class VocalDeverb {
    private VocalDeverb() {}

    static final boolean DEBUG = Boolean.getBoolean("deverb.debug");
    static double T60_MUL = Double.parseDouble(System.getProperty("deverb.t60mul", "1.5"));

    public static final class Analysis {
        public double t60 = 1.2;          // giây
        public double tailDb = -3;         // mức vang ổn định so với giọng khô (dB, R/D)
        public boolean reverbMeasured;     // đo được từ bài (false = dùng mặc định)
        public boolean dryLike;            // giọng gần như khô sẵn
        public int events;
        public boolean delayFound;
        public double delayMs, echoDb = -99, delayScore;
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

    private static double percentile(double[] a, int cnt, double q) {
        if (cnt == 0) return -120;
        double[] b = java.util.Arrays.copyOf(a, cnt);
        java.util.Arrays.sort(b);
        return b[Math.min(cnt - 1, Math.max(0, (int) Math.round(q * (cnt - 1))))];
    }

    private static double median(java.util.List<Double> v) {
        if (v.isEmpty()) return Double.NaN;
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        java.util.Arrays.sort(a);
        return a.length % 2 == 1 ? a[a.length / 2] : 0.5 * (a[a.length / 2 - 1] + a[a.length / 2]);
    }

    /**
     * Xử lý tại chỗ.
     * @param reverbAmt 0..1 mức khử vang
     * @param delayAmt  0..1 mức khử delay
     */
    public static Analysis process(float[] L, float[] R, int n, double fs, double reverbAmt, double delayAmt,
                                   Mastering.Progress p, double p0, double p1) {
        Analysis an = new Analysis();
        if (reverbAmt <= 0.01 && delayAmt <= 0.01) return an;

        final int N = fs > 60000 ? 4096 : 2048;
        final int hop = N / 4, K = N / 2 + 1;
        final double hopSec = hop / fs;
        final FFT fft = new FFT(N);
        final double[] win = new double[N];
        for (int i = 0; i < N; i++) win[i] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
        final double olaNorm = 1.0 / 2.0;   // Σ hann ở bước N/4 = 2
        final int M = (n + 2 * N - N) / hop + 1;   // số khung trên tín hiệu đã đệm N mẫu hai đầu
        final double[] re = new double[N], im = new double[N];
        final double pMid = p0 + (p1 - p0) * 0.35;

        // ── 1) Phân tích ────────────────────────────────────────────
        int kB0 = Math.max(1, (int) Math.round(200.0 * N / fs)), kB1 = Math.min(K - 1, (int) Math.round(8000.0 * N / fs));
        double[] E = new double[M];
        for (int m = 0; m < M; m++) {
            int start = m * hop - N;
            for (int i = 0; i < N; i++) {
                int s = start + i;
                re[i] = s >= 0 && s < n ? 0.5 * (L[s] + R[s]) * win[i] : 0;
                im[i] = 0;
            }
            fft.run(re, im, false);
            double sum = 0;
            for (int k = kB0; k <= kB1; k++) sum += re[k] * re[k] + im[k] * im[k];
            E[m] = 10 * Math.log10(sum + 1e-14);
            if ((m & 255) == 0) tick(p, p0, pMid, 0.4 * m / M, "Phân tích vang & delay của giọng");
        }

        // sàn nhiễu và mức đỉnh
        double[] Ev = new double[M];
        int cv = 0;
        for (int m = 0; m < M; m++) if (E[m] > -110) Ev[cv++] = E[m];
        double floor = percentile(Ev, cv, 0.10), top = percentile(Ev, cv, 0.95);
        if (top - floor < 15) floor = top - 40;

        // ── 1a) dò delay: cepstrum trung bình — tiếng dội để lại "vân lược" trên phổ, thành đỉnh nhọn ở quefrency = D ──
        detectDelay(L, R, n, fs, an, p, p0 + (pMid - p0) * 0.4, p0 + (pMid - p0) * 0.85);
        tick(p, p0, pMid, 0.85, "Phân tích vang & delay của giọng");

        // ── 1b) đo độ dài vang từ các điểm kết thúc nốt ──
        java.util.List<Double> t60s = new java.util.ArrayList<>(), drops = new java.util.ArrayList<>();
        int fast = 0, offsets = 0;
        int d0 = Math.max(2, (int) Math.round(0.06 / hopSec));
        int minLen = Math.max(4, (int) Math.round(0.08 / hopSec));
        int maxSpan = (int) Math.round(1.5 / hopSec);  // có thể rút ngắn bên dưới
        int f80 = Math.max(2, (int) Math.round(0.08 / hopSec));
        double riseTol = 4;
        // giọng khô thì tắt nốt rơi rất nhanh; nếu có delay, tiếng dội lấp khoảng lặng nên ngưỡng "rơi" thấp hơn
        double fastThr = an.delayFound ? Math.min(20, Math.max(8, -an.echoDb - 2)) : 20;
        if (an.delayFound) {                         // chỉ đo đuôi vang trước khi tiếng dội đầu tiên tới
            int le = (int) Math.floor(an.delayMs / 1000 / hopSec) - 1;
            maxSpan = Math.max(minLen + d0, Math.min(maxSpan, le));
        }
        for (int t = 3; t < M - minLen - d0; t++) {
            double e0 = E[t];
            if (e0 < top - 25 || e0 < floor + 25 || t < 6 || t + 5 >= M) continue;
            // điểm bắt đầu tắt nốt: còn ở mức nốt (so với 60 ms trước), khung sau bắt đầu giảm, 5 khung sau giảm ≥ 5 dB
            double ref = E[t - 1];
            for (int k = 2; k <= 5; k++) ref = Math.max(ref, E[t - k]);
            if (e0 < ref - 1.5 || E[t + 1] >= e0 || E[t + 5] > e0 - 5) continue;
            int end = t + 1;
            double runMin = E[t + 1];
            while (end < M - 1 && end - t < maxSpan) {
                double e = E[end + 1];
                if (e > runMin + riseTol) break;
                if (e < Math.max(floor + 8, e0 - 45)) { end++; break; }
                end++;
                runMin = Math.min(runMin, e);
            }
            double minE = e0;
            for (int k = t + 1; k <= end; k++) minE = Math.min(minE, E[k]);
            if (e0 - minE < 12) continue;            // không phải chỗ kết thúc nốt / câu
            offsets++;
            if (DEBUG && offsets <= 8) {
                StringBuilder sb = new StringBuilder(String.format("off t=%d e0=%.1f floor=%.1f end=%d :", t, e0, floor, end - t));
                for (int k = t; k <= Math.min(end, t + 40); k += 2) sb.append(String.format(" %.0f", E[k] - e0));
                System.err.println(sb);
            }
            double min80 = e0;
            for (int k = t + 1; k <= Math.min(M - 1, t + f80); k++) min80 = Math.min(min80, E[k]);
            if (min80 < e0 - fastThr) { fast++; t = end; continue; }
            int a = t + d0, b = end;
            while (a < b && E[a] > e0 - 10) a++;                 // chỉ lấy phần đuôi rõ ràng
            while (b > a && E[b] < e0 - 35) b--;
            if (b - a + 1 >= minLen) {
                double sx = 0, sy = 0, sxx = 0, sxy = 0;
                int c = 0;
                for (int k = a; k <= b; k++) {
                    double x = (k - t) * hopSec, y = E[k];
                    sx += x; sy += y; sxx += x * x; sxy += x * y; c++;
                }
                double den = c * sxx - sx * sx;
                if (den > 1e-12) {
                    double slope = (c * sxy - sx * sy) / den, icpt = (sy - slope * sx) / c;
                    if (DEBUG && offsets <= 8) System.err.printf("   slope=%.1f dB/s icpt-e0=%.1f%n", slope, icpt - e0);
                    if (slope < -12 && slope > -400) {
                        t60s.add(-60 / slope);
                        drops.add(e0 - icpt);
                    }
                }
            }
            t = end;
        }
        an.events = t60s.size();
        if (fast >= 4 && fast >= 2 * t60s.size()) {
            an.dryLike = true;
            an.reverbMeasured = true;
            an.t60 = 0.3;
            an.tailDb = -20;
        } else if (t60s.size() >= 2) {
            an.reverbMeasured = true;
            an.t60 = Math.max(0.25, Math.min(4.0, median(t60s)));
            double drop = Math.max(1.0, Math.min(20, median(drops)));
            an.tailDb = Math.max(-5, Math.min(-2, 10 * Math.log10(1.0 / (Math.pow(10, drop / 10) - 1))));
        }
        tick(p, p0, pMid, 1, "Phân tích vang & delay của giọng");

        // ── 2) Xử lý ───────────────────────────────────────────────
        double rv = Math.max(0, Math.min(1, reverbAmt)), dl = Math.max(0, Math.min(1, delayAmt));
        boolean useEcho = an.delayFound && dl > 0.01;
        boolean useRev = rv > 0.01 && !an.dryLike;
        if (!useEcho && !useRev) return an;

        double a = Math.pow(10, -6 * hopSec / (an.t60 * T60_MUL));   // suy giảm công suất mỗi khung
        double rdr = Math.pow(10, an.tailDb / 10);
        double cRev = rdr * (1 - a);
        int Nd = Math.max(1, (int) Math.round(0.04 / hopSec));
        double br = useRev ? 0.5 + 1.5 * rv : 0;
        double be = useEcho ? 0.6 + 2.0 * dl : 0;
        double g2e = useEcho ? Math.pow(10, an.echoDb / 10) : 0;
        int Le = useEcho ? Math.max(1, (int) Math.round(an.delayMs / 1000 / hopSec)) : 1;
        double gmin = Math.pow(10, -(3 + 15 * Math.max(useRev ? rv : 0, useEcho ? dl : 0)) / 20);
        double gmin2 = gmin * gmin;
        double sideG = 1 - 0.5 * (useRev ? rv : 0);
        double rel = Math.exp(-hopSec / 0.015);

        double gateDb = -(4 + 10 * Math.max(useRev ? rv : 0, useEcho ? dl : 0)), gateEnv = 0;
        double gateAtt = Math.exp(-hopSec / 0.040), gateRel = Math.exp(-hopSec / 0.008);
        // cộng chồng (OLA) trong bộ đệm N mẫu, ghi đè tại chỗ phần đã xong — không cần mảng phụ cỡ cả bài
        double[] accL = new double[N], accR = new double[N];
        double[] Rv = new double[K], G = new double[K], G2 = new double[K], G2s = new double[K];
        java.util.Arrays.fill(G, 1);
        int ringD = Nd + 1, ringP = Le + 1;
        float[][] Dbuf = new float[ringD][K];
        float[][] Pbuf = new float[ringP][K];
        double[] Lr = new double[K], Li = new double[K], Rr = new double[K], Ri = new double[K], P = new double[K];

        for (int m = 0; m < M; m++) {
            int start = m * hop - N;
            for (int i = 0; i < N; i++) {
                int s = start + i;
                if (s >= 0 && s < n) { re[i] = L[s] * win[i]; im[i] = R[s] * win[i]; }
                else { re[i] = 0; im[i] = 0; }
            }
            fft.run(re, im, false);
            for (int k = 0; k < K; k++) {
                int nk = (N - k) & (N - 1);
                double ar = re[k], ai = im[k], brr = re[nk], bii = -im[nk];   // B = conj(X[N-k])
                Lr[k] = 0.5 * (ar + brr); Li[k] = 0.5 * (ai + bii);
                double dr = ar - brr, di = ai - bii;
                Rr[k] = 0.5 * di; Ri[k] = -0.5 * dr;
                P[k] = 0.5 * (Lr[k] * Lr[k] + Li[k] * Li[k] + Rr[k] * Rr[k] + Ri[k] * Ri[k]);
            }
            float[] dOld = Dbuf[(m + 1) % ringD];          // D(m - Nd)
            float[] pOld = Pbuf[(m + 1) % ringP];          // P(m - Le)
            for (int k = 0; k < K; k++) {
                double interf = 0;
                if (useRev) {
                    Rv[k] = a * Rv[k] + cRev * (m >= Nd ? dOld[k] : 0);
                    interf += br * Rv[k];
                }
                if (useEcho && m >= Le) interf += be * g2e * pOld[k];
                double g2 = 1 - interf / (P[k] + 1e-14);
                G2[k] = g2 < gmin2 ? gmin2 : g2;
            }
            for (int k = 0; k < K; k++) {
                double l = G2[Math.max(0, k - 1)], r = G2[Math.min(K - 1, k + 1)];
                G2s[k] = 0.25 * l + 0.5 * G2[k] + 0.25 * r;
            }
            float[] dNew = Dbuf[m % ringD], pNew = Pbuf[m % ringP];
            // cổng đuôi: khung mà phần "giọng khô" ước lượng chỉ còn rất ít so với tổng → đang ở đuôi vang/dội
            double sumP = 0, sumD = 0;
            for (int k = kB0; k <= kB1; k++) { sumP += P[k]; sumD += G2s[k] * P[k]; }
            double q = sumP > 1e-14 ? sumD / sumP : 1;
            double tgt = gateDb * Math.max(0, Math.min(1, (0.45 - q) / 0.35));
            gateEnv = tgt < gateEnv ? gateAtt * gateEnv + (1 - gateAtt) * tgt : gateRel * gateEnv + (1 - gateRel) * tgt;
            double gx = Math.pow(10, gateEnv / 20);
            for (int k = 0; k < K; k++) {
                double g = Math.sqrt(G2s[k]);
                if (g < G[k]) g = rel * G[k] + (1 - rel) * g;
                G[k] = g;
                g = Math.max(gmin * 0.35, g * gx);
                dNew[k] = (float) (g * g * P[k]);
                pNew[k] = (float) P[k];
                // áp gain + hạ Side
                double mr = 0.5 * (Lr[k] + Rr[k]), mi = 0.5 * (Li[k] + Ri[k]);
                double sr = 0.5 * (Lr[k] - Rr[k]) * sideG, si = 0.5 * (Li[k] - Ri[k]) * sideG;
                Lr[k] = g * (mr + sr); Li[k] = g * (mi + si);
                Rr[k] = g * (mr - sr); Ri[k] = g * (mi - si);
            }
            // ghép lại: Y = L' + i R'
            for (int k = 0; k < K; k++) {
                re[k] = Lr[k] - Ri[k];
                im[k] = Li[k] + Rr[k];
                if (k > 0 && k < N / 2) {
                    int nk = N - k;
                    re[nk] = Lr[k] + Ri[k];       // conj(L) + i conj(R)
                    im[nk] = -Li[k] + Rr[k];
                }
            }
            fft.run(re, im, true);
            for (int i = 0; i < N; i++) {
                accL[i] += re[i] * win[i] * olaNorm;
                accR[i] += im[i] * win[i] * olaNorm;
            }
            // mẫu [start, start+hop) đã đủ mọi khung chồng lên → ghi ra; các khung sau chỉ đọc từ start+hop trở đi
            for (int i = 0; i < hop; i++) {
                int s = start + i;
                if (s >= 0 && s < n) { L[s] = (float) accL[i]; R[s] = (float) accR[i]; }
            }
            System.arraycopy(accL, hop, accL, 0, N - hop);
            System.arraycopy(accR, hop, accR, 0, N - hop);
            java.util.Arrays.fill(accL, N - hop, N, 0);
            java.util.Arrays.fill(accR, N - hop, N, 0);
            if ((m & 255) == 0) tick(p, pMid, p1, m / (double) M, "Khử vang & delay giọng");
        }
        tick(p, pMid, p1, 1, "Khử vang & delay giọng");
        return an;
    }

    /** Dò delay bằng cepstrum công suất trung bình trên các khung ~1.5 s (hạ mẫu để nhanh). */
    static void detectDelay(float[] L, float[] R, int n, double fs, Analysis an, Mastering.Progress p, double q0, double q1) {
        int ds = fs > 60000 ? 4 : 2;
        double fsd = fs / ds;
        int Nc = 1;
        while (Nc < 1.4 * fsd) Nc <<= 1;
        int hopc = Nc / 2, nd = n / ds;
        if (nd < Nc + hopc) return;
        float[] md = new float[nd];
        for (int i = 0; i < nd; i++) {
            double s = 0;
            for (int j = 0; j < ds; j++) s += L[i * ds + j] + R[i * ds + j];
            md[i] = (float) (s / (2 * ds));
        }
        double[] w = new double[Nc];
        for (int i = 0; i < Nc; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / Nc);
        int frames = (nd - Nc) / hopc + 1;
        double[] fe = new double[frames];
        double maxE = 0;
        for (int f = 0; f < frames; f++) {
            double e = 0;
            int st = f * hopc;
            for (int i = 0; i < Nc; i += 4) { double x = md[st + i] * w[i]; e += x * x; }
            fe[f] = e;
            maxE = Math.max(maxE, e);
        }
        FFT fft = new FFT(Nc);
        double[] re = new double[Nc], im = new double[Nc], acc = new double[Nc / 2 + 1];
        int used = 0;
        for (int f = 0; f < frames; f++) {
            if (fe[f] < maxE * 1e-3) continue;
            int st = f * hopc;
            for (int i = 0; i < Nc; i++) { re[i] = md[st + i] * w[i]; im[i] = 0; }
            fft.run(re, im, false);
            double mean = 0;
            for (int k = 0; k <= Nc / 2; k++) { re[k] = re[k] * re[k] + im[k] * im[k]; mean += re[k]; }
            double eps = 1e-6 * mean / (Nc / 2 + 1) + 1e-30;
            for (int k = 0; k <= Nc / 2; k++) acc[k] += Math.log(re[k] + eps);
            used++;
            tick(p, q0, q1, 0.9 * f / frames, "Dò delay trong giọng");
        }
        if (used < 4) return;
        for (int k = 0; k <= Nc / 2; k++) { re[k] = acc[k] / used; im[k] = 0; }
        for (int k = 1; k < Nc / 2; k++) { re[Nc - k] = re[k]; im[Nc - k] = 0; }
        fft.run(re, im, true);
        int qMin = (int) Math.round(0.08 * fsd), qMax = Math.min((int) Math.round(1.0 * fsd), Nc / 2 - 2);
        int cnt = qMax - qMin + 1;
        double[] v = new double[cnt];
        int best = qMin;
        for (int q = qMin; q <= qMax; q++) {
            v[q - qMin] = re[q];
            if (re[q] > re[best]) best = q;
        }
        double[] srt = v.clone();
        java.util.Arrays.sort(srt);
        double med = srt[cnt / 2];
        for (int i = 0; i < cnt; i++) srt[i] = Math.abs(v[i] - med);
        java.util.Arrays.sort(srt);
        double sigma = 1.4826 * srt[cnt / 2] + 1e-12;
        double z = (re[best] - med) / sigma;
        an.delayScore = z;
        // tiếng dội thật cho một đỉnh nhọn, đứng riêng; rung giọng (vibrato) cho một cụm đỉnh sát nhau
        int iso0 = (int) Math.round(0.0003 * fsd), iso1 = (int) Math.round(0.010 * fsd);
        double nearMax = 0;
        for (int d = iso0 + 1; d <= iso1; d++) {
            if (best - d >= 1) nearMax = Math.max(nearMax, re[best - d]);
            if (best + d < Nc / 2) nearMax = Math.max(nearMax, re[best + d]);
        }
        boolean isolated = nearMax < 0.35 * re[best];
        if (DEBUG) System.err.printf("cep best=%.1fms c=%.4f z=%.1f near=%.4f iso=%b%n", best / fsd * 1000, re[best], z, nearMax, isolated);
        if (z > 9 && re[best] > 0.05 && isolated) {
            double frac = 0;
            double y0 = re[best - 1], y1 = re[best], y2 = re[best + 1], den = y0 - 2 * y1 + y2;
            if (Math.abs(den) > 1e-12) frac = Math.max(-0.5, Math.min(0.5, 0.5 * (y0 - y2) / den));
            an.delayFound = true;
            an.delayMs = (best + frac) * ds / fs * 1000;
            an.echoDb = Math.max(-30, Math.min(-1, 20 * Math.log10(Math.min(0.9, 1.6 * re[best]))));
        }
        tick(p, q0, q1, 1, "Dò delay trong giọng");
    }
}

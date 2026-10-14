package vn.daopham.codmaster.dsp;

/**
 * Đo độ lớn tích hợp (Integrated LUFS) theo ITU-R BS.1770-4 / EBU R128:
 * lọc K-weighting, khối 400 ms chồng 75 %, cổng tuyệt đối -70 LUFS và cổng tương đối -10 LU.
 * Đồng thời đo True Peak (dBTP) bằng nội suy 4x.
 */
public final class LoudnessMeter {
    private final Biquad shL, shR, hpL, hpR;
    private final int sub;            // số mẫu của 100 ms
    private double subSum;
    private int subCount;
    private final double[] lastSubs = new double[4];
    private int subsSeen;
    private double[] blocks = new double[1024];
    private int nBlocks;
    private final TruePeak tpL = new TruePeak(), tpR = new TruePeak();

    public LoudnessMeter(double fs) {
        // Hệ số K-weighting cho tần số mẫu bất kỳ.
        shL = Biquad.kShelf(fs); shR = Biquad.kShelf(fs);
        hpL = Biquad.kHighPass(fs); hpR = Biquad.kHighPass(fs);
        sub = (int) Math.round(fs * 0.1);
    }

    public void add(float l, float r) {
        tpL.push(l);
        tpR.push(r);
        double kl = hpL.process(shL.process(l));
        double kr = hpR.process(shR.process(r));
        subSum += kl * kl + kr * kr;
        if (++subCount == sub) {
            lastSubs[subsSeen & 3] = subSum / sub;
            subsSeen++;
            subSum = 0;
            subCount = 0;
            if (subsSeen >= 4) {
                double ms = (lastSubs[0] + lastSubs[1] + lastSubs[2] + lastSubs[3]) / 4.0;
                if (nBlocks == blocks.length) {
                    double[] nb = new double[blocks.length * 2];
                    System.arraycopy(blocks, 0, nb, 0, nBlocks);
                    blocks = nb;
                }
                blocks[nBlocks++] = ms;
            }
        }
    }

    private static double lufs(double ms) { return -0.691 + 10 * Math.log10(ms); }

    /** Integrated loudness (LUFS). Trả về -100 nếu im lặng / quá ngắn. */
    public double integrated() {
        int n = nBlocks;
        double[] b = blocks;
        if (n == 0 && subsSeen > 0) {
            // Bài quá ngắn (< 400 ms): dùng trung bình các khối con đã có.
            double s = 0; int c = Math.min(subsSeen, 4);
            for (int i = 0; i < c; i++) s += lastSubs[i];
            b = new double[]{s / c};
            n = 1;
        }
        double sum = 0; int cnt = 0;
        double absMs = Math.pow(10, (-70 + 0.691) / 10);
        for (int i = 0; i < n; i++) if (b[i] > absMs) { sum += b[i]; cnt++; }
        if (cnt == 0) return -100;
        double rel = lufs(sum / cnt) - 10;
        double relMs = Math.pow(10, (rel + 0.691) / 10);
        sum = 0; cnt = 0;
        for (int i = 0; i < n; i++) if (b[i] > absMs && b[i] > relMs) { sum += b[i]; cnt++; }
        if (cnt == 0) return -100;
        return lufs(sum / cnt);
    }

    /** True peak lớn nhất (dBTP). */
    public double truePeakDb() {
        tpL.flush(); tpR.flush();
        double p = Math.max(tpL.max, tpR.max);
        return p <= 1e-9 ? -100 : 20 * Math.log10(p);
    }

    /**
     * Bộ đo đỉnh thật: nội suy bằng sinc cửa sổ Kaiser (β = 8).
     * Bản đo chính xác dùng 8x / 32 tap; bản nhanh cho limiter dùng 4x / 24 tap.
     */
    static final class TruePeak {
        final int taps, half, phases;
        final double[][] coef;
        private final double[] hist;
        private int pos;
        double max;

        TruePeak() { this(8, 16); }

        TruePeak(int phases, int half) {
            this.phases = phases;
            this.half = half;
            this.taps = 2 * half;
            hist = new double[taps];
            coef = new double[phases][taps];
            double beta = 8, i0b = bessel0(beta);
            for (int p = 0; p < phases; p++) {
                for (int j = 0; j < taps; j++) {
                    double t = (half - 1) + p / (double) phases - j;
                    double sinc = Math.abs(t) < 1e-12 ? 1 : Math.sin(Math.PI * t) / (Math.PI * t);
                    double u = t / half;
                    double w = Math.abs(u) >= 1 ? 0 : bessel0(beta * Math.sqrt(1 - u * u)) / i0b;
                    coef[p][j] = sinc * w;
                }
            }
        }

        private static double bessel0(double x) {
            double sum = 1, term = 1, q = x * x / 4;
            for (int k = 1; k < 40; k++) { term *= q / (k * k); sum += term; if (term < 1e-14 * sum) break; }
            return sum;
        }

        /** Đẩy 1 mẫu; trả về đỉnh (trị tuyệt đối) trên đoạn [n-half, n-half+1]. */
        double push(double x) { return push(x, max * 0.5); }

        /**
         * Như trên, nhưng bỏ qua nội suy (trả về đỉnh mẫu) khi hai mẫu hai đầu đoạn
         * thấp hơn 'floor' quá 6 dB — đỉnh giữa mẫu thực tế không vượt nổi mức đó.
         */
        double push(double x, double floor) {
            hist[pos] = x;
            if (++pos == taps) pos = 0;
            int c0 = pos + half - 1; if (c0 >= taps) c0 -= taps;
            int c1 = c0 + 1; if (c1 >= taps) c1 -= taps;
            double a = Math.abs(hist[c0]), b = Math.abs(hist[c1]);
            double best = a;
            if (Math.max(a, b) * 2.0 < floor) { if (best > max) max = best; return Math.max(a, b); }
            if (b > best) best = b;
            for (int p = 1; p < phases; p++) {
                double[] c = coef[p];
                double s = 0;
                int k = pos;
                for (int j = 0; j < taps; j++) {
                    s += hist[k] * c[j];
                    if (++k == taps) k = 0;
                }
                if (s < 0) s = -s;
                if (s > best) best = s;
            }
            if (best > max) max = best;
            return best;
        }

        void flush() { for (int i = 0; i < half + 1; i++) push(0); }
    }
}

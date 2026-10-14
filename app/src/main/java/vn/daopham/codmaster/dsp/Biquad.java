package vn.daopham.codmaster.dsp;

/** Bộ lọc biquad (RBJ cookbook), tính toán double, Direct Form I. */
public final class Biquad {
    private double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;

    private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0;
        this.a1 = a1 / a0; this.a2 = a2 / a0;
    }

    public double process(double x) {
        double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x; y2 = y1; y1 = y;
        return y;
    }

    public void reset() { x1 = x2 = y1 = y2 = 0; }

    /** Đổi hệ số thành bộ chuông mới, giữ nguyên trạng thái (dùng cho EQ động). */
    public void setPeak(double fs, double f0, double q, double gainDb) {
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f0 / fs;
        double c = Math.cos(w), al = Math.sin(w) / (2 * q);
        double a0 = 1 + al / A;
        b0 = (1 + al * A) / a0; b1 = (-2 * c) / a0; b2 = (1 - al * A) / a0;
        a1 = (-2 * c) / a0; a2 = (1 - al / A) / a0;
    }

    /** K-weighting chuẩn BS.1770 cho tần số mẫu bất kỳ (công thức của libebur128). */
    public static Biquad kShelf(double fs) {
        double f0 = 1681.974450955533, G = 3.999843853973347, Q = 0.7071752369554196;
        double K = Math.tan(Math.PI * f0 / fs);
        double Vh = Math.pow(10.0, G / 20.0), Vb = Math.pow(Vh, 0.4996667741545416);
        double a0 = 1.0 + K / Q + K * K;
        return new Biquad(Vh + Vb * K / Q + K * K, 2.0 * (K * K - Vh), Vh - Vb * K / Q + K * K,
                a0, 2.0 * (K * K - 1.0), 1.0 - K / Q + K * K);
    }

    public static Biquad kHighPass(double fs) {
        double f0 = 38.13547087602444, Q = 0.5003270373238773;
        double K = Math.tan(Math.PI * f0 / fs);
        double a0 = 1.0 + K / Q + K * K;
        return new Biquad(a0, -2.0 * a0, a0, a0, 2.0 * (K * K - 1.0), 1.0 - K / Q + K * K);
    }

    public static Biquad highPass(double fs, double f0, double q) {
        double w = 2 * Math.PI * f0 / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
    }

    public static Biquad peak(double fs, double f0, double q, double gainDb) {
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f0 / fs;
        double c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return new Biquad(1 + al * A, -2 * c, 1 - al * A, 1 + al / A, -2 * c, 1 - al / A);
    }

    public static Biquad lowShelf(double fs, double f0, double q, double gainDb) {
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f0 / fs;
        double c = Math.cos(w), al = Math.sin(w) / (2 * q), s = 2 * Math.sqrt(A) * al;
        return new Biquad(
                A * ((A + 1) - (A - 1) * c + s),
                2 * A * ((A - 1) - (A + 1) * c),
                A * ((A + 1) - (A - 1) * c - s),
                (A + 1) + (A - 1) * c + s,
                -2 * ((A - 1) + (A + 1) * c),
                (A + 1) + (A - 1) * c - s);
    }

    public static Biquad highShelf(double fs, double f0, double q, double gainDb) {
        double A = Math.pow(10, gainDb / 40), w = 2 * Math.PI * f0 / fs;
        double c = Math.cos(w), al = Math.sin(w) / (2 * q), s = 2 * Math.sqrt(A) * al;
        return new Biquad(
                A * ((A + 1) + (A - 1) * c + s),
                -2 * A * ((A - 1) + (A + 1) * c),
                A * ((A + 1) + (A - 1) * c - s),
                (A + 1) - (A - 1) * c + s,
                2 * ((A - 1) - (A + 1) * c),
                (A + 1) - (A - 1) * c - s);
    }
}

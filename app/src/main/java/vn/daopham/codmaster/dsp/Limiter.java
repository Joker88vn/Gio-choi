package vn.daopham.codmaster.dsp;

/**
 * Limiter nhìn trước (lookahead) có phát hiện đỉnh thật (true peak, 4x).
 * Độ lợi = min trượt trong cửa sổ lookahead, nhả (release) mượt theo hàm mũ,
 * rồi làm mượt bằng bộ lọc hộp cùng chiều dài — đảm bảo không vượt trần mà không gây méo gắt.
 * Tín hiệu ra bị trễ getLatency() mẫu.
 */
public final class Limiter {
    private final int L;                 // chiều dài lookahead (mẫu)
    private final double ceiling;        // trần tuyến tính
    private final double rc;             // hệ số nhả
    private static final int DET_PHASES = 4, DET_HALF = 12;
    private final LoudnessMeter.TruePeak detL = new LoudnessMeter.TruePeak(DET_PHASES, DET_HALF);
    private final LoudnessMeter.TruePeak detR = new LoudnessMeter.TruePeak(DET_PHASES, DET_HALF);

    // hàng đợi đơn điệu cho min trượt
    private final double[] dqVal;
    private final long[] dqIdx;
    private int dqHead, dqSize;

    // bộ lọc hộp
    private final double[] box;
    private int boxPos;
    private double boxSum;

    // đường trễ âm thanh
    private final float[] dl, dr;
    private final int D;
    private int dPos;

    private long k;            // chỉ số mẫu đang tính độ lợi (= n - HALF)
    private double rel = 1;
    private double minGain = 1;

    public float outL, outR;

    public Limiter(double fs, double ceilingDb, double lookaheadMs, double releaseMs) {
        L = Math.max(8, (int) Math.round(fs * lookaheadMs / 1000));
        ceiling = Math.pow(10, ceilingDb / 20);
        rc = 1 - Math.exp(-1.0 / (fs * releaseMs / 1000));
        dqVal = new double[L + 2];
        dqIdx = new long[L + 2];
        box = new double[L];
        java.util.Arrays.fill(box, 1.0);
        boxSum = L;
        D = L + DET_HALF - 1;
        dl = new float[D];
        dr = new float[D];
        k = -DET_HALF;
    }

    public int getLatency() { return D; }

    /** Mức giảm lớn nhất (dB, số âm). */
    public double maxReductionDb() { return 20 * Math.log10(minGain); }

    public void process(float l, float r) {
        // 1) phát hiện đỉnh thật cho mẫu k = n - HALF
        double p = Math.max(detL.push(l, ceiling), detR.push(r, ceiling));
        // đường trễ âm thanh
        float ol = dl[dPos], or = dr[dPos];
        dl[dPos] = l; dr[dPos] = r;
        if (++dPos == D) dPos = 0;
        // ol/or là mẫu n - D = k - (L - 1)

        double req = p > ceiling ? ceiling / p : 1.0;

        // 2) min trượt trên [k-L+1, k]
        while (dqSize > 0 && dqVal[(dqHead + dqSize - 1) % dqVal.length] >= req) dqSize--;
        int tail = (dqHead + dqSize) % dqVal.length;
        dqVal[tail] = req; dqIdx[tail] = k; dqSize++;
        while (dqIdx[dqHead] <= k - L) { dqHead = (dqHead + 1) % dqVal.length; dqSize--; }
        double held = dqVal[dqHead];

        // 3) nhả mượt (chỉ đi lên chậm, đi xuống tức thì)
        if (held < rel) rel = held; else rel += (held - rel) * rc;

        // 4) lọc hộp chiều dài L
        boxSum += rel - box[boxPos];
        box[boxPos] = rel;
        if (++boxPos == L) boxPos = 0;
        double g = boxSum / L;
        if (g > 1) g = 1;
        if (g < minGain) minGain = g;

        outL = (float) (ol * g);
        outR = (float) (or * g);
        k++;
    }
}

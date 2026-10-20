package vn.daopham.codmaster.desktop;

import javax.sound.sampled.*;

/**
 * Phát A/B nhiều nguồn, chuyển tức thì tại cùng vị trí:
 * 0 = bản gốc, 1 = master, 2 = giọng gốc (chế độ stem), 3 = giọng đã xử lý (chế độ stem).
 */
final class Player {
    static final int ORIG = 0, MASTER = 1, VOCAL_ORIG = 2, VOCAL_NEW = 3;

    private AudioEngine.Track src;
    private final float[][] srcL = new float[4][], srcR = new float[4][];
    volatile int sel = MASTER;
    volatile float masterGain = 1f;

    private volatile boolean running;
    private Thread thread;
    private SourceDataLine line;
    private volatile long startFrame;
    private volatile long pausedAt;
    private final Runnable onEnded;

    Player(Runnable onEnded) { this.onEnded = onEnded; }

    void setSource(AudioEngine.Track t) {
        stop();
        src = t;
        for (int i = 0; i < 4; i++) { srcL[i] = null; srcR[i] = null; }
        if (t != null) { srcL[ORIG] = t.left; srcR[ORIG] = t.right; }
        pausedAt = 0;
    }

    synchronized void setAlt(int idx, float[] l, float[] r) {
        srcL[idx] = l;
        srcR[idx] = r;
    }

    void setMaster(float[] l, float[] r) { setAlt(MASTER, l, r); }

    boolean has(int idx) { return srcL[idx] != null; }
    /** Nguồn thực sự đang phát (nếu nguồn đã chọn chưa có thì về bản gốc). */
    int effective() { int s = sel; return srcL[s] != null ? s : ORIG; }
    boolean isPlaying() { return running; }
    int frames() { return src == null ? 0 : src.frames; }
    int sampleRate() { return src == null ? 44100 : src.sampleRate; }

    long position() {
        SourceDataLine ln = line;
        if (!running || ln == null) return pausedAt;
        return Math.min(frames(), startFrame + ln.getLongFramePosition());
    }

    synchronized void play() throws LineUnavailableException {
        AudioEngine.Track t = src;
        if (t == null || running) return;
        if (pausedAt >= t.frames - 1) pausedAt = 0;
        AudioFormat fmt = new AudioFormat(t.sampleRate, 16, 2, true, false);
        SourceDataLine ln = AudioSystem.getSourceDataLine(fmt);
        ln.open(fmt, (int) (t.sampleRate * 4 * 0.12));
        ln.start();
        line = ln;
        startFrame = pausedAt;
        running = true;
        Thread th = new Thread(() -> {
            int block = 1024;
            byte[] buf = new byte[block * 4];
            long pos = startFrame;
            int n = t.frames;
            while (running && pos < n) {
                int m = (int) Math.min(block, n - pos);
                int s = effective();
                float[] a = srcL[s], b = srcR[s];
                if (a == null || b == null) { a = t.left; b = t.right; s = ORIG; }
                float g = s == MASTER ? masterGain : 1f;
                int q = 0;
                for (int i = 0; i < m; i++) {
                    int p = (int) (pos + i);
                    float xa = p < a.length ? a[p] : 0f, xb = p < b.length ? b[p] : 0f;
                    int vl = Math.round(Math.max(-1f, Math.min(1f, xa * g)) * 32767f);
                    int vr = Math.round(Math.max(-1f, Math.min(1f, xb * g)) * 32767f);
                    buf[q++] = (byte) vl; buf[q++] = (byte) (vl >> 8);
                    buf[q++] = (byte) vr; buf[q++] = (byte) (vr >> 8);
                }
                ln.write(buf, 0, q);
                pos += m;
            }
            if (running && pos >= n) {
                ln.drain();
                running = false;
                pausedAt = 0;
                ln.close();
                if (onEnded != null) onEnded.run();
            }
        }, "player");
        th.setDaemon(true);
        thread = th;
        th.start();
    }

    synchronized void pause() {
        if (!running) return;
        pausedAt = position();
        stopLine();
    }

    void stop() { pause(); }

    synchronized void seek(long frame) throws LineUnavailableException {
        boolean was = running;
        if (was) stopLine();
        pausedAt = Math.max(0, Math.min(Math.max(0, frames() - 1), frame));
        if (was) play();
    }

    private void stopLine() {
        running = false;
        SourceDataLine ln = line;
        if (ln != null) { ln.stop(); ln.flush(); }
        try { if (thread != null) thread.join(500); } catch (InterruptedException ignored) {}
        if (ln != null) ln.close();
        line = null;
        thread = null;
    }
}

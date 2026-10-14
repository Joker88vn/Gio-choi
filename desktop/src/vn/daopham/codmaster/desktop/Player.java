package vn.daopham.codmaster.desktop;

import javax.sound.sampled.*;

/** Phát A/B: chuyển Gốc ↔ Master tức thì tại cùng vị trí; có hệ số cân âm lượng cho bản master. */
final class Player {
    private AudioEngine.Track src;
    private volatile float[] mL, mR;
    volatile boolean useMaster = true;
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
        mL = mR = null;
        pausedAt = 0;
    }

    void setMaster(float[] l, float[] r) { mL = l; mR = r; }

    boolean hasMaster() { return mL != null; }
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
                float[] ml = mL, mr = mR;
                boolean master = useMaster && ml != null && mr != null;
                float g = master ? masterGain : 1f;
                float[] a = master ? ml : t.left, b = master ? mr : t.right;
                int q = 0;
                for (int i = 0; i < m; i++) {
                    int p = (int) (pos + i);
                    int vl = Math.round(Math.max(-1f, Math.min(1f, a[p] * g)) * 32767f);
                    int vr = Math.round(Math.max(-1f, Math.min(1f, b[p] * g)) * 32767f);
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

package vn.daopham.codmaster.desktop;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc mọi định dạng âm thanh qua ffmpeg (đi kèm bộ cài), ghi WAV 16/24-bit và MP3 320k. */
final class AudioEngine {
    private AudioEngine() {}

    static final class Track {
        final int sampleRate, frames, srcChannels;
        final float[] left, right;
        Track(int sr, float[] l, float[] r, int n, int ch) {
            sampleRate = sr; left = l; right = r; frames = n; srcChannels = ch;
        }
    }

    interface Progress { boolean update(double f); }

    static final class Cancelled extends RuntimeException { Cancelled() { super("Đã huỷ"); } }

    private static String ffmpegPath;

    /** Tìm ffmpeg: cạnh file jar (bản cài), rồi PATH. */
    static String ffmpeg() {
        if (ffmpegPath != null) return ffmpegPath;
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = win ? "ffmpeg.exe" : "ffmpeg";
        try {
            File jar = new File(AudioEngine.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jar.isFile() ? jar.getParentFile() : jar;
            for (File d = dir; d != null; d = d.getParentFile()) {
                File f = new File(d, exe);
                if (f.isFile()) return ffmpegPath = f.getAbsolutePath();
                File f2 = new File(new File(d, "app"), exe);
                if (f2.isFile()) return ffmpegPath = f2.getAbsolutePath();
                if (d.getParentFile() == null || d.toPath().getNameCount() < 1) break;
            }
        } catch (Exception ignored) {
        }
        return ffmpegPath = exe;
    }

    private static final Pattern DURATION = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Pattern AUDIO = Pattern.compile("Audio: [^\\n]*?(\\d+) Hz, ([^,\\n]+)");

    static Track decode(File file, Progress progress) throws IOException { return decode(file, 0, progress); }

    /** @param targetSr nếu > 0: chuyển về tần số mẫu này (dùng khi ghép 2 stem khác tần số). */
    static Track decode(File file, int targetSr, Progress progress) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg(), "-hide_banner", "-nostdin", "-i", file.getAbsolutePath(),
                "-vn", "-ac", "2"));
        if (targetSr > 0) { cmd.add("-ar"); cmd.add(String.valueOf(targetSr)); }
        cmd.addAll(List.of("-acodec", "pcm_f32le", "-f", "f32le", "pipe:1"));
        Process p;
        try {
            p = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new IOException("Không chạy được bộ giải mã ffmpeg. Hãy cài lại COD Master.");
        }
        StringBuilder err = new StringBuilder();
        double[] durSec = {0};
        int[] info = {0, 2}; // sr, channels
        Thread et = new Thread(() -> {
            try (Reader r = new InputStreamReader(p.getErrorStream(), "UTF-8")) {
                char[] b = new char[4096];
                int k;
                while ((k = r.read(b)) > 0) {
                    synchronized (err) {
                        err.append(b, 0, k);
                        if (err.length() > 200_000) err.delete(0, 100_000);
                        if (durSec[0] == 0) {
                            Matcher m = DURATION.matcher(err);
                            if (m.find()) durSec[0] = Integer.parseInt(m.group(1)) * 3600 + Integer.parseInt(m.group(2)) * 60 + Double.parseDouble(m.group(3));
                        }
                        if (info[0] == 0) {
                            Matcher m = AUDIO.matcher(err);
                            if (m.find()) {
                                info[0] = Integer.parseInt(m.group(1));
                                String lay = m.group(2).trim();
                                info[1] = lay.startsWith("mono") ? 1 : 2;
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }, "ffmpeg-err");
        et.start();

        int cap = 44100 * 60 * 6;
        float[] L = new float[cap], R = new float[cap];
        int n = 0;
        long lastTick = 0;
        try (InputStream in = new BufferedInputStream(p.getInputStream(), 1 << 17)) {
            byte[] buf = new byte[1 << 16];
            ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            int carry = 0;
            while (true) {
                int k = in.read(buf, carry, buf.length - carry);
                if (k < 0) break;
                int avail = carry + k;
                int frames = avail / 8;
                if (n + frames > cap) {
                    cap = Math.max(cap * 3 / 2, n + frames + 44100);
                    L = java.util.Arrays.copyOf(L, cap);
                    R = java.util.Arrays.copyOf(R, cap);
                }
                for (int f = 0; f < frames; f++) {
                    L[n + f] = bb.getFloat(f * 8);
                    R[n + f] = bb.getFloat(f * 8 + 4);
                }
                n += frames;
                carry = avail - frames * 8;
                if (carry > 0) System.arraycopy(buf, frames * 8, buf, 0, carry);
                long now = System.currentTimeMillis();
                if (now - lastTick > 100) {
                    lastTick = now;
                    double d = durSec[0], sr = info[0];
                    double f = d > 0 && sr > 0 ? n / (d * sr) : 0;
                    if (!progress.update(Math.min(1, f))) {
                        p.destroyForcibly();
                        throw new Cancelled();
                    }
                }
            }
        }
        try {
            p.waitFor();
            et.join(2000);
        } catch (InterruptedException ignored) {
        }
        if (n < 4410 || info[0] == 0) {
            String e;
            synchronized (err) { e = err.toString(); }
            String last = "";
            for (String line : e.split("[\\r\\n]+")) if (!line.isBlank()) last = line.trim();
            throw new IOException("Không đọc được file này." + (last.isEmpty() ? "" : "\n" + last));
        }
        // làm sạch giá trị bất thường
        for (int i = 0; i < n; i++) {
            if (!Float.isFinite(L[i])) L[i] = 0;
            if (!Float.isFinite(R[i])) R[i] = 0;
        }
        return new Track(targetSr > 0 ? targetSr : info[0], L, R, n, info[1]);
    }

    /** Ghi WAV PCM; 16-bit có dither TPDF. */
    static void writeWav(File out, float[] l, float[] r, int frames, int sr, int bits, Progress pr) throws IOException {
        int bp = bits / 8;
        long dataLen = (long) frames * 2 * bp;
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 17)) {
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()).putInt((int) (36 + dataLen)).put("WAVE".getBytes()).put("fmt ".getBytes());
            h.putInt(16).putShort((short) 1).putShort((short) 2).putInt(sr).putInt(sr * 2 * bp)
                    .putShort((short) (2 * bp)).putShort((short) bits);
            h.put("data".getBytes()).putInt((int) dataLen);
            os.write(h.array());
            int chunk = 8192;
            byte[] b = new byte[chunk * 2 * bp];
            Random rnd = new Random(7);
            for (int i = 0; i < frames; i += chunk) {
                int m = Math.min(chunk, frames - i), q = 0;
                for (int k = 0; k < m; k++) {
                    for (int c = 0; c < 2; c++) {
                        float x = c == 0 ? l[i + k] : r[i + k];
                        if (bits == 16) {
                            int v = Math.round(x * 32767f + (rnd.nextFloat() - rnd.nextFloat()));
                            v = Math.max(-32768, Math.min(32767, v));
                            b[q++] = (byte) v; b[q++] = (byte) (v >> 8);
                        } else {
                            int v = Math.round(x * 8388607f);
                            v = Math.max(-8388608, Math.min(8388607, v));
                            b[q++] = (byte) v; b[q++] = (byte) (v >> 8); b[q++] = (byte) (v >> 16);
                        }
                    }
                }
                os.write(b, 0, q);
                if (pr != null && (i / chunk) % 32 == 0 && !pr.update(i / (double) frames)) throw new Cancelled();
            }
        }
    }

    /** Mã hoá MP3 320 kbps (LAME qua ffmpeg). */
    static void writeMp3(File out, String title, float[] l, float[] r, int frames, int sr, Progress pr) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(ffmpeg(), "-hide_banner", "-nostdin", "-y",
                "-f", "f32le", "-ar", String.valueOf(sr), "-ac", "2", "-i", "pipe:0",
                "-c:a", "libmp3lame", "-b:a", "320k", "-id3v2_version", "3"));
        if (title != null && !title.isBlank()) { cmd.add("-metadata"); cmd.add("title=" + title); }
        Path tmp = out.toPath().resolveSibling(out.getName() + ".part.mp3");
        cmd.add(tmp.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try (OutputStream os = new BufferedOutputStream(p.getOutputStream(), 1 << 17)) {
            int chunk = 8192;
            ByteBuffer bb = ByteBuffer.allocate(chunk * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frames; i += chunk) {
                int m = Math.min(chunk, frames - i);
                bb.clear();
                for (int k = 0; k < m; k++) { bb.putFloat(l[i + k]); bb.putFloat(r[i + k]); }
                os.write(bb.array(), 0, m * 8);
                if (pr != null && (i / chunk) % 32 == 0 && !pr.update(0.95 * i / frames)) {
                    p.destroyForcibly();
                    throw new Cancelled();
                }
            }
        }
        try {
            int code = p.waitFor();
            if (code != 0) throw new IOException("Mã hoá MP3 thất bại (mã " + code + ")");
        } catch (InterruptedException e) {
            throw new IOException("Bị gián đoạn");
        }
        Files.move(tmp, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (pr != null) pr.update(1);
    }
}

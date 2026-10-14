package vn.daopham.codmaster.desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.DoubleConsumer;

/** Dạng sóng lớn: bản master (hổ phách) phía sau, bản gốc (xám) phía trước, vạch phát, thước thời gian. */
final class WaveView extends JComponent {
    static final int BUCKETS = 1400;

    private float[] orig, master;
    private double progress = 0;
    private double hoverX = -1;
    private long frames;
    private int sr = 44100;
    DoubleConsumer onSeek;
    private String emptyText = "Kéo thả file nhạc vào đây, hoặc bấm “Mở file…”";

    WaveView() {
        setPreferredSize(new Dimension(640, 320));
        setMinimumSize(new Dimension(300, 160));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoverX = e.getX(); repaint(); }
            @Override public void mouseExited(MouseEvent e) { hoverX = -1; repaint(); }
            @Override public void mousePressed(MouseEvent e) { seek(e); }
            @Override public void mouseDragged(MouseEvent e) { hoverX = e.getX(); progress = frac(e.getX()); repaint(); }
            @Override public void mouseReleased(MouseEvent e) { seek(e); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private double frac(int x) {
        int p = 12;
        return Math.max(0, Math.min(1, (x - p) / (double) Math.max(1, getWidth() - 2 * p)));
    }

    private void seek(MouseEvent e) {
        if (orig == null) return;
        progress = frac(e.getX());
        repaint();
        if (onSeek != null) onSeek.accept(progress);
    }

    void setData(float[] o, float[] m, long frames, int sr) {
        this.orig = o; this.master = m; this.frames = frames; this.sr = sr; repaint();
    }

    void setEmptyText(String s) { emptyText = s; repaint(); }

    void setProgress(double p) {
        if (Math.abs(p - progress) > 1e-5) { progress = p; repaint(); }
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.g2(g);
        int w = getWidth(), h = getHeight();
        Theme.roundRect(g2, 0, 0, w, h, 14, Theme.BG, Theme.LINE);
        int p = 12;
        int top = 10, bottom = h - 26;
        int mid = (top + bottom) / 2;
        double half = (bottom - top) / 2.0 - 2;
        // lưới mức -6 dB
        g2.setColor(new Color(0x1A1F27));
        int g6 = (int) (half * 0.5);
        g2.drawLine(p, mid - g6, w - p, mid - g6);
        g2.drawLine(p, mid + g6, w - p, mid + g6);
        g2.setColor(Theme.LINE);
        g2.drawLine(p, mid, w - p, mid);

        if (orig == null) {
            Theme.centerText(g2, emptyText, Theme.font(14f), Theme.SUB, 0, top, w, bottom - top);
            g2.dispose();
            return;
        }
        int cw = w - 2 * p;
        drawBars(g2, master, Theme.ACC, p, cw, mid, half);
        drawBars(g2, orig, Theme.WAVE_ORIG, p, cw, mid, half);

        // thước thời gian
        g2.setFont(Theme.font(11f));
        g2.setColor(Theme.FAINT);
        double secs = sr > 0 ? frames / (double) sr : 0;
        int stepSec = secs > 600 ? 120 : secs > 300 ? 60 : secs > 120 ? 30 : secs > 40 ? 10 : 5;
        for (int s = 0; s <= secs; s += stepSec) {
            int x = (int) (p + cw * (s / Math.max(1e-9, secs)));
            g2.fillRect(x, bottom + 4, 1, 4);
            String t = String.format("%d:%02d", s / 60, s % 60);
            if (x + 30 < w) g2.drawString(t, x + 3, bottom + 18);
        }

        // vùng đã phát
        int px = (int) (p + cw * progress);
        g2.setColor(new Color(255, 255, 255, 14));
        g2.fillRect(p, top, Math.max(0, px - p), bottom - top);
        // vạch rê chuột
        if (hoverX >= p && hoverX <= w - p) {
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRect((int) hoverX, top, 1, bottom - top);
            String t = Theme.fmtTime((long) (frames * frac((int) hoverX)), sr);
            g2.setFont(Theme.medium(11f));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(t) + 10;
            int tx = (int) Math.min(w - p - tw, hoverX + 6);
            Theme.roundRect(g2, tx, top + 2, tw, 18, 6, Theme.CARD2, Theme.LINE);
            g2.setColor(Theme.INK);
            g2.drawString(t, tx + 5, top + 15);
        }
        // vạch phát
        g2.setColor(Theme.INK);
        g2.fillRect(px - 1, top - 2, 2, bottom - top + 4);
        g2.fillOval(px - 4, top - 6, 8, 8);
        g2.dispose();
    }

    private void drawBars(Graphics2D g2, float[] arr, Color c, int x0, int cw, int mid, double half) {
        if (arr == null) return;
        g2.setColor(c);
        int n = arr.length;
        for (int x = 0; x < cw; x++) {
            int a = (int) ((long) x * n / cw), b = (int) ((long) (x + 1) * n / cw);
            float mx = 0;
            for (int i = a; i < Math.max(a + 1, b) && i < n; i++) mx = Math.max(mx, arr[i]);
            int hh = (int) Math.max(1, mx * half);
            g2.fillRect(x0 + x, mid - hh, 1, hh * 2);
        }
    }

    static float[] peaks(float[] l, float[] r, int n) {
        float[] out = new float[BUCKETS];
        for (int b = 0; b < BUCKETS; b++) {
            int s = (int) ((long) n * b / BUCKETS), e = (int) ((long) n * (b + 1) / BUCKETS);
            float mx = 0;
            for (int i = s; i < e; i++) {
                float a = Math.abs(l[i]), c = Math.abs(r[i]);
                if (a > mx) mx = a;
                if (c > mx) mx = c;
            }
            out[b] = Math.min(1f, mx);
        }
        return out;
    }
}

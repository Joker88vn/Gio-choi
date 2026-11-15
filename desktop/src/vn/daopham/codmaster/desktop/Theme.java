package vn.daopham.codmaster.desktop;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Bảng màu, phông chữ và các hàm vẽ dùng chung — tông tối kiểu phòng thu. */
final class Theme {
    private Theme() {}

    static final Color BG = new Color(0x0D1015);
    static final Color PANEL = new Color(0x111419);
    static final Color CARD = new Color(0x161A21);
    static final Color CARD2 = new Color(0x1D222B);
    static final Color HOVER = new Color(0x242A34);
    static final Color INK = new Color(0xECEFF3);
    static final Color SUB = new Color(0x8C96A4);
    static final Color FAINT = new Color(0x5C6573);
    static final Color LINE = new Color(0x272D37);
    static final Color ACC = new Color(0xF5A623);
    static final Color ACC_HOVER = new Color(0xFFB845);
    static final Color ACC_SOFT = new Color(0x3A2C14);
    static final Color ACC_INK = new Color(0x1A1206);
    static final Color WAVE_ORIG = new Color(0x9A, 0xA4, 0xB2, 0xB4);
    static final Color GOOD = new Color(0x4CC38A);
    static final Color BAD = new Color(0xE5534B);

    private static String regular = "SansSerif";
    private static String semibold = null;

    static void initFonts() {
        Set<String> fams = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        if (fams.contains("Segoe UI")) {
            regular = "Segoe UI";
            if (fams.contains("Segoe UI Semibold")) semibold = "Segoe UI Semibold";
        } else if (fams.contains("Inter")) {
            regular = "Inter";
        }
    }

    static Font font(float size) { return new Font(regular, Font.PLAIN, 12).deriveFont(size); }

    static Font medium(float size) {
        if (semibold != null) return new Font(semibold, Font.PLAIN, 12).deriveFont(size);
        return new Font(regular, Font.BOLD, 12).deriveFont(size);
    }

    static Font bold(float size) { return new Font(regular, Font.BOLD, 12).deriveFont(size); }

    static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    static void roundRect(Graphics2D g, double x, double y, double w, double h, double r, Color fill, Color stroke) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, r * 2, r * 2);
        if (fill != null) { g.setColor(fill); g.fill(rr); }
        if (stroke != null) { g.setColor(stroke); g.setStroke(new BasicStroke(1f)); g.draw(rr); }
    }

    static void centerText(Graphics2D g, String s, Font f, Color c, int x, int y, int w, int h) {
        g.setFont(f);
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (w - fm.stringWidth(s)) / 2;
        int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(s, tx, ty);
    }

    static String trimToWidth(String s, FontMetrics fm, int w) {
        if (fm.stringWidth(s) <= w) return s;
        String e = "…";
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + e) <= w) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + e;
    }

    enum IconKind { PLAY, PAUSE, SAVE, OPEN, MUSIC, CLOSE }

    /** Vẽ biểu tượng trong ô vuông (x, y, s). */
    static void icon(Graphics2D g, IconKind k, double x, double y, double s, Color c) {
        if (s <= 1) return;
        g.setColor(c);
        BasicStroke st = new BasicStroke((float) (s * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Path2D p = new Path2D.Double();
        switch (k) {
            case PLAY -> {
                p.moveTo(x + s * 0.30, y + s * 0.18); p.lineTo(x + s * 0.84, y + s * 0.50); p.lineTo(x + s * 0.30, y + s * 0.82); p.closePath();
                g.fill(p);
            }
            case PAUSE -> {
                g.fill(new RoundRectangle2D.Double(x + s * 0.24, y + s * 0.18, s * 0.18, s * 0.64, s * 0.08, s * 0.08));
                g.fill(new RoundRectangle2D.Double(x + s * 0.58, y + s * 0.18, s * 0.18, s * 0.64, s * 0.08, s * 0.08));
            }
            case SAVE -> {
                g.setStroke(st);
                p.moveTo(x + s * 0.5, y + s * 0.14); p.lineTo(x + s * 0.5, y + s * 0.62);
                p.moveTo(x + s * 0.30, y + s * 0.44); p.lineTo(x + s * 0.5, y + s * 0.64); p.lineTo(x + s * 0.70, y + s * 0.44);
                p.moveTo(x + s * 0.18, y + s * 0.70); p.lineTo(x + s * 0.18, y + s * 0.84); p.lineTo(x + s * 0.82, y + s * 0.84); p.lineTo(x + s * 0.82, y + s * 0.70);
                g.draw(p);
            }
            case OPEN -> {
                g.setStroke(st);
                p.moveTo(x + s * 0.14, y + s * 0.78); p.lineTo(x + s * 0.14, y + s * 0.24); p.lineTo(x + s * 0.40, y + s * 0.24);
                p.lineTo(x + s * 0.48, y + s * 0.34); p.lineTo(x + s * 0.86, y + s * 0.34); p.lineTo(x + s * 0.86, y + s * 0.78); p.closePath();
                g.draw(p);
            }
            case MUSIC -> {
                g.setStroke(st);
                p.moveTo(x + s * 0.40, y + s * 0.72); p.lineTo(x + s * 0.40, y + s * 0.22); p.lineTo(x + s * 0.80, y + s * 0.14); p.lineTo(x + s * 0.80, y + s * 0.62);
                g.draw(p);
                double r = s * 0.12;
                g.fill(new java.awt.geom.Ellipse2D.Double(x + s * 0.30 - r, y + s * 0.74 - r, 2 * r, 2 * r));
                g.fill(new java.awt.geom.Ellipse2D.Double(x + s * 0.70 - r, y + s * 0.64 - r, 2 * r, 2 * r));
            }
            case CLOSE -> {
                g.setStroke(st);
                p.moveTo(x + s * 0.28, y + s * 0.28); p.lineTo(x + s * 0.72, y + s * 0.72);
                p.moveTo(x + s * 0.72, y + s * 0.28); p.lineTo(x + s * 0.28, y + s * 0.72);
                g.draw(p);
            }
        }
    }

    static String fmtTime(long frames, int sr) {
        long sec = sr > 0 ? frames / sr : 0;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    static String num(double v) {
        if (v <= -99) return "—";
        return String.format(java.util.Locale.US, "%.1f", v).replace('-', '−');
    }
}

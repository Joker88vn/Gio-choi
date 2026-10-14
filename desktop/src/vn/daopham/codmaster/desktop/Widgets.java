package vn.daopham.codmaster.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/** Bộ thành phần giao diện vẽ tay: thẻ, nút, nút gạt, thanh trượt, ô số đo, ngăn xếp. */
final class Widgets {
    private Widgets() {}

    // ───────────── bố cục ─────────────

    /** Xếp dọc: mỗi phần tử giãn ngang, có khoảng cách phía trên. */
    static class VStack extends JPanel {
        private int row = 0;

        VStack() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        VStack row(Component c, int topGap) {
            if (c instanceof JComponent jc) {
                Dimension p = jc.getPreferredSize();
                jc.setMinimumSize(new Dimension(Math.min(jc.getMinimumSize().width, p.width), p.height));
            }
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = row++; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(topGap, 0, 0, 0);
            add(c, g);
            return this;
        }

        VStack grow(Component c, int topGap) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = row++; g.weightx = 1; g.weighty = 1; g.fill = GridBagConstraints.BOTH;
            g.insets = new Insets(topGap, 0, 0, 0);
            add(c, g);
            return this;
        }

        VStack filler() {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.gridy = row++; g.weighty = 1; g.fill = GridBagConstraints.VERTICAL;
            add(Box.createVerticalGlue(), g);
            return this;
        }
    }

    /** Xếp ngang: phần tử có weight > 0 giãn theo tỉ lệ. */
    static class HStack extends JPanel {
        private int col = 0;

        HStack() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        HStack add(Component c, double weight, int leftGap) {
            if (weight == 0 && c instanceof JComponent jc) jc.setMinimumSize(jc.getPreferredSize());
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = col++; g.gridy = 0; g.weightx = weight;
            g.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            g.anchor = GridBagConstraints.CENTER;
            g.insets = new Insets(0, leftGap, 0, 0);
            add(c, g);
            return this;
        }
    }

    /** Thẻ bo góc có viền mảnh. */
    static class Card extends JPanel {
        private final Color fill;
        Card(LayoutManager lm, int pad) { this(lm, pad, Theme.CARD); }
        Card(LayoutManager lm, int pad, Color fill) {
            super(lm);
            this.fill = fill;
            setOpaque(false);
            setBorder(new EmptyBorder(pad, pad, pad, pad));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 16, fill, Theme.LINE);
            g2.dispose();
        }
        /** Không bao giờ bị ép thấp hơn nội dung (nội dung có thể đổi khi chuyển chế độ). */
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    }

    static JLabel label(String s, Font f, Color c) {
        JLabel l = new JLabel(s);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    static JLabel caps(String s) {
        JLabel l = label(s.toUpperCase(), Theme.medium(11f), Theme.SUB);
        return l;
    }

    // ───────────── nút ─────────────

    enum Style { FILLED, SOFT, OUTLINE, GHOST }

    /** Nút bo tròn, có trạng thái rê chuột / nhấn / vô hiệu. */
    static class Pill extends JComponent {
        String text;
        Style style;
        Theme.IconKind icon;
        final int h;
        float fontSize;
        Runnable onClick;
        boolean hover, press;
        int radius = -1;

        Pill(String text, Style style, int h, Runnable onClick) {
            this.text = text; this.style = style; this.h = h; this.onClick = onClick;
            this.fontSize = h >= 48 ? 15f : 13f;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = press && contains(e.getPoint()) && isEnabled();
                    press = false; repaint();
                    if (fire && Pill.this.onClick != null) Pill.this.onClick.run();
                }
            };
            addMouseListener(m);
        }

        Pill icon(Theme.IconKind k) { this.icon = k; return this; }
        Pill radius(int r) { this.radius = r; return this; }
        void setText(String t) { this.text = t; revalidate(); repaint(); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(fontFor());
            int w = (text == null ? 0 : fm.stringWidth(text)) + h;
            if (icon != null) w += h / 2 + (text == null || text.isEmpty() ? -h / 2 : 0);
            return new Dimension(Math.max(w, h), h);
        }
        @Override public Dimension getMinimumSize() { return isMinimumSizeSet() ? super.getMinimumSize() : new Dimension(h, h); }

        private Font fontFor() { return style == Style.FILLED ? Theme.bold(fontSize) : Theme.medium(fontSize); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
            int w = getWidth(), hh = getHeight();
            int r = radius >= 0 ? radius : hh / 2;
            Color fill, stroke = null, fg;
            boolean hv = hover && isEnabled();
            switch (style) {
                case FILLED -> { fill = press ? Theme.ACC.darker() : hv ? Theme.ACC_HOVER : Theme.ACC; fg = Theme.ACC_INK; }
                case SOFT -> { fill = hv ? new Color(0x4A3818) : Theme.ACC_SOFT; fg = Theme.ACC; }
                case OUTLINE -> { fill = hv ? Theme.HOVER : Theme.CARD2; stroke = Theme.LINE; fg = Theme.INK; }
                default -> { fill = hv ? Theme.HOVER : null; fg = Theme.SUB; }
            }
            Theme.roundRect(g2, 0, 0, w, hh, r, fill, stroke);
            Font f = fontFor();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            boolean hasText = text != null && !text.isEmpty();
            int is = (int) (hh * 0.42);
            int tw = hasText ? fm.stringWidth(text) : 0;
            int total = tw + (icon != null ? is + (hasText ? 8 : 0) : 0);
            int x = (w - total) / 2;
            if (icon != null) {
                Theme.icon(g2, icon, x, (hh - is) / 2.0, is, fg);
                x += is + (hasText ? 8 : 0);
            }
            if (hasText) {
                g2.setColor(fg);
                g2.drawString(text, x, (hh - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }

    /** Nút tròn chỉ có biểu tượng. */
    static class RoundIcon extends JComponent {
        Theme.IconKind kind;
        final Color fill, fg, stroke;
        final int size;
        boolean hover;
        Runnable onClick;

        RoundIcon(Theme.IconKind kind, int size, Color fill, Color fg, Color stroke, Runnable onClick) {
            this.kind = kind; this.size = size; this.fill = fill; this.fg = fg; this.stroke = stroke; this.onClick = onClick;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (isEnabled() && RoundIcon.this.onClick != null) RoundIcon.this.onClick.run(); }
            });
        }

        void setKind(Theme.IconKind k) { if (k != kind) { kind = k; repaint(); } }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            if (!isEnabled()) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
            int s = Math.min(getWidth(), getHeight());
            Color f = fill;
            if (hover && isEnabled()) f = fill == Theme.ACC ? Theme.ACC_HOVER : Theme.HOVER;
            g2.setColor(f);
            g2.fillOval(0, 0, s - 1, s - 1);
            if (stroke != null) { g2.setColor(stroke); g2.drawOval(0, 0, s - 1, s - 1); }
            double is = s * 0.44;
            double off = kind == Theme.IconKind.PLAY ? s * 0.03 : 0;
            Theme.icon(g2, kind, (s - is) / 2 + off, (s - is) / 2, is, fg);
            g2.dispose();
        }
    }

    /** Nút gạt nhiều lựa chọn. */
    static class Segmented extends JComponent {
        final String[] items;
        int selected;
        final Consumer<Integer> onPick;
        int hover = -1;
        final int h;
        boolean[] disabled;

        Segmented(String[] items, int selected, int h, Consumer<Integer> onPick) {
            this.items = items; this.selected = selected; this.h = h; this.onPick = onPick;
            this.disabled = new boolean[items.length];
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int i = idx(e.getX()); if (i != hover) { hover = i; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    int i = idx(e.getX());
                    if (i >= 0 && isEnabled() && !disabled[i]) { Segmented.this.selected = i; repaint(); onPick.accept(i); }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void setSelected(int i) { if (selected != i) { selected = i; repaint(); } }

        private int idx(int x) {
            int w = (getWidth() - 6) / items.length;
            int i = (x - 3) / Math.max(1, w);
            return i >= 0 && i < items.length ? i : -1;
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.medium(12.5f));
            int mx = 0;
            for (String s : items) mx = Math.max(mx, fm.stringWidth(s));
            return new Dimension((mx + 30) * items.length + 6, h);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), hh = getHeight();
            Theme.roundRect(g2, 0, 0, w, hh, hh / 2.0, Theme.CARD2, Theme.LINE);
            int sw = (w - 6) / items.length;
            for (int i = 0; i < items.length; i++) {
                int x = 3 + i * sw;
                boolean sel = i == selected;
                if (sel) Theme.roundRect(g2, x, 3, sw, hh - 6, (hh - 6) / 2.0, Theme.ACC, null);
                else if (i == hover && !disabled[i]) Theme.roundRect(g2, x, 3, sw, hh - 6, (hh - 6) / 2.0, Theme.HOVER, null);
                Color c = sel ? Theme.ACC_INK : disabled[i] ? Theme.FAINT : Theme.SUB;
                Theme.centerText(g2, items[i], Theme.medium(12.5f), c, x, 0, sw, hh);
            }
            g2.dispose();
        }
    }

    /** Thanh trượt mảnh có vạch 0 cho thông số hai chiều; lăn chuột để chỉnh mịn. */
    static class Slider extends JComponent {
        final double min, max, step;
        double value;
        final boolean bipolar;
        Consumer<Double> onChange;
        boolean drag, hover;

        Slider(double min, double max, double step, double value, Consumer<Double> onChange) {
            this.min = min; this.max = max; this.step = step; this.value = value; this.onChange = onChange;
            this.bipolar = min < 0 && max > 0;
            setPreferredSize(new Dimension(120, 24));
            setMinimumSize(new Dimension(60, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2 && bipolar) { set(0, true); return; }
                    drag = true; setFromX(e.getX());
                }
                @Override public void mouseDragged(MouseEvent e) { if (drag) setFromX(e.getX()); }
                @Override public void mouseReleased(MouseEvent e) { drag = false; repaint(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseWheelMoved(MouseWheelEvent e) { set(value - e.getWheelRotation() * step, true); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
        }

        private int pad() { return 8; }

        private void setFromX(int x) {
            double f = (x - pad()) / (double) Math.max(1, getWidth() - 2 * pad());
            set(min + f * (max - min), true);
        }

        void set(double v, boolean fromUser) {
            v = Math.max(min, Math.min(max, v));
            v = min + Math.round((v - min) / step) * step;
            v = Math.round(v * 1000) / 1000.0;
            if (v != value) {
                value = v;
                repaint();
                if (onChange != null && fromUser) onChange.accept(v);
            }
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth(), h = getHeight(), p = pad();
            int cy = h / 2;
            Theme.roundRect(g2, p, cy - 2, w - 2 * p, 4, 2, Theme.LINE, null);
            double f = (value - min) / (max - min);
            double z = bipolar ? (0 - min) / (max - min) : 0;
            double x0 = p + Math.min(f, z) * (w - 2 * p), x1 = p + Math.max(f, z) * (w - 2 * p);
            Theme.roundRect(g2, x0, cy - 2, Math.max(4, x1 - x0), 4, 2, Theme.ACC, null);
            if (bipolar) {
                g2.setColor(Theme.FAINT);
                int zx = (int) (p + z * (w - 2 * p));
                g2.fillRect(zx, cy - 5, 1, 10);
            }
            double tx = p + f * (w - 2 * p);
            int r = drag || hover ? 8 : 7;
            g2.setColor(Theme.ACC);
            g2.fillOval((int) (tx - r), cy - r, 2 * r, 2 * r);
            g2.setColor(Theme.ACC_INK);
            g2.fillOval((int) (tx - 3), cy - 3, 6, 6);
            g2.dispose();
        }
    }

    /** Ô số đo: tiêu đề nhỏ, số lớn, dòng phụ. */
    static class Tile extends JComponent {
        String title, value = "—", unit = "", sub = "";
        Color valueColor = Theme.INK;

        Tile(String title) {
            this.title = title.toUpperCase();
            setPreferredSize(new Dimension(150, 78));
        }

        void set(String value, String unit, String sub) {
            this.value = value; this.unit = unit; this.sub = sub; repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 12, Theme.CARD2, null);
            g2.setFont(Theme.medium(10.5f));
            g2.setColor(Theme.SUB);
            g2.drawString(title, 14, 22);
            g2.setFont(Theme.bold(22f));
            g2.setColor(valueColor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(value, 14, 50);
            int vx = 14 + fm.stringWidth(value) + 5;
            g2.setFont(Theme.font(12f));
            g2.setColor(Theme.SUB);
            g2.drawString(unit, vx, 50);
            g2.setFont(Theme.font(11.5f));
            g2.setColor(Theme.FAINT);
            g2.drawString(Theme.trimToWidth(sub, g2.getFontMetrics(), getWidth() - 24), 14, 68);
            g2.dispose();
        }
    }

    /** Thanh tiến trình mảnh. */
    static class Progress extends JComponent {
        double value;
        Progress() { setPreferredSize(new Dimension(200, 6)); }
        void set(double v) { value = v; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int h = getHeight();
            Theme.roundRect(g2, 0, 0, getWidth(), h, h / 2.0, Theme.LINE, null);
            Theme.roundRect(g2, 0, 0, Math.max(h, getWidth() * value), h, h / 2.0, Theme.ACC, null);
            g2.dispose();
        }
    }

    /** Hàng chỉnh thông số: tên | thanh trượt | giá trị. */
    static class ParamRow extends JPanel {
        final Slider slider;
        final JLabel val;
        final DoubleFunction<String> fmt;

        ParamRow(String title, String hint, double min, double max, double step, double value,
                 DoubleFunction<String> fmt, Consumer<Double> onChange) {
            super(new GridBagLayout());
            setOpaque(false);
            this.fmt = fmt;
            JLabel name = label(title, Theme.font(13f), Theme.INK);
            name.setToolTipText(hint);
            name.setPreferredSize(new Dimension(116, 24));
            name.setMinimumSize(new Dimension(116, 24));
            val = label(fmt.apply(value), Theme.medium(12.5f), Theme.ACC);
            val.setHorizontalAlignment(SwingConstants.RIGHT);
            val.setPreferredSize(new Dimension(78, 24));
            val.setMinimumSize(new Dimension(78, 24));
            slider = new Slider(min, max, step, value, v -> {
                val.setText(fmt.apply(v));
                onChange.accept(v);
            });
            slider.setToolTipText(hint + " · lăn chuột để chỉnh mịn" + (min < 0 && max > 0 ? ", nhấp đúp về 0" : ""));
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = 0; g.gridx = 0; g.anchor = GridBagConstraints.WEST;
            add(name, g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(0, 4, 0, 4);
            add(slider, g);
            g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE; g.insets = new Insets(0, 0, 0, 0);
            add(val, g);
        }

        void setValue(double v) {
            slider.set(v, false);
            slider.value = v;
            slider.repaint();
            val.setText(fmt.apply(v));
        }
    }
}

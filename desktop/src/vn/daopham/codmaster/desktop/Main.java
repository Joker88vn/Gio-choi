package vn.daopham.codmaster.desktop;

import vn.daopham.codmaster.dsp.Mastering;
import vn.daopham.codmaster.desktop.Widgets.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

/** COD Master cho Windows — cửa sổ chính. */
public final class Main {
    static final String VERSION = "1.2.0";

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
            UIManager.put("Component.arc", 12);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 8);
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        }
        UIManager.put("ToolTip.background", Theme.CARD2);
        UIManager.put("ToolTip.foreground", Theme.INK);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE), new EmptyBorder(6, 8, 6, 8)));
        Theme.initFonts();
        UIManager.put("ToolTip.font", Theme.font(12.5f));
        SwingUtilities.invokeLater(() -> new Main().show());
    }

    // ───────────────────────────── trạng thái ─────────────────────────────
    private final Preferences prefs = Preferences.userNodeForPackage(Main.class);
    private AudioEngine.Track track;
    private String trackName = "";
    private File trackFile;
    private final Mastering.Settings settings = new Mastering.Settings();
    private int presetIdx = 0;
    private float[] masterL, masterR;
    private Mastering.Result result;
    private Mastering.Settings masteredWith;
    private float[] origPeaks, masterPeaks;
    private int exportFmt = 0; // 0 WAV24, 1 WAV16, 2 MP3
    private boolean matchLevel = false;
    private volatile boolean busy = false;
    private volatile boolean cancel = false;
    private final Player player = new Player(() -> SwingUtilities.invokeLater(this::refreshPlayer));

    // ───────────────────────────── giao diện ─────────────────────────────
    private JFrame frame;
    private JLabel fileName, fileInfo;
    private PresetList presetList;
    private final List<ParamRow> paramRows = new ArrayList<>();
    private Segmented ab, fmtSeg;
    private WaveView wave;
    private RoundIcon playBtn;
    private JLabel nowText, timeText;
    private Pill matchBtn, masterBtn, saveBtn, openBtn;
    private JLabel dirtyNote;
    private Tile tLufs, tPeak, tLim, tLen;
    private JLabel status;
    private Progress progress;
    private Pill cancelBtn;

    private void show() {
        frame = new JFrame("COD Master — Mastering studio");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImages(appIcons());
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.add(header(), BorderLayout.NORTH);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(center(), BorderLayout.CENTER);
        root.add(rightPanel(), BorderLayout.EAST);
        root.add(statusBar(), BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(1160, 760));
        frame.setSize(1320, 840);
        frame.setLocationRelativeTo(null);
        installDragDrop(root);
        installKeys();
        Presets.ALL.get(0).applyTo(settings);
        refreshAll();
        new Timer(40, e -> updatePlayhead()).start();
        frame.setVisible(true);

        String demo = System.getProperty("codmaster.demo");
        if (demo != null) loadFile(new File(demo));
    }

    // ── đầu trang
    private JComponent header() {
        HStack h = new HStack();
        h.setBorder(new EmptyBorder(16, 20, 14, 20));
        JComponent logo = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10, Theme.ACC, null);
                Theme.icon(g2, Theme.IconKind.MUSIC, 8, 8, getWidth() - 16, Theme.ACC_INK);
                g2.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(38, 38));
        h.add(logo, 0, 0);
        VStack t = new VStack();
        t.row(Widgets.label("COD Master", Theme.bold(19f), Theme.INK), 0);
        JLabel sub = Widgets.caps("Mastering studio · v" + VERSION);
        sub.setForeground(Theme.ACC);
        sub.setFont(Theme.medium(10.5f));
        t.row(sub, 0);
        h.add(t, 0, 12);
        h.add(Box.createHorizontalGlue(), 1, 0);
        h.add(Widgets.label("Space: phát / dừng   ·   B: đổi Gốc ↔ Master", Theme.font(12f), Theme.FAINT), 0, 0);
        h.add(chip("EBU R128 · True Peak 8×"), 0, 16);
        return h;
    }

    private JComponent chip(String s) {
        JLabel l = Widgets.label(s, Theme.medium(11.5f), Theme.SUB);
        l.setBorder(new EmptyBorder(6, 12, 6, 12));
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), getHeight() / 2.0, Theme.CARD2, Theme.LINE);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.add(l);
        return p;
    }

    // ── cột trái: bài nhạc + phong cách
    private JComponent sidebar() {
        VStack v = new VStack();
        v.setBorder(new EmptyBorder(0, 20, 16, 0));
        v.setPreferredSize(new Dimension(286, 100));

        Card c = new Card(new BorderLayout(), 16);
        VStack cv = new VStack();
        cv.row(Widgets.caps("Bài nhạc"), 0);
        HStack row = new HStack();
        JComponent tileIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.g2(g);
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 12, Theme.ACC_SOFT, null);
                Theme.icon(g2, Theme.IconKind.MUSIC, 11, 11, getWidth() - 22, Theme.ACC);
                g2.dispose();
            }
        };
        tileIcon.setPreferredSize(new Dimension(46, 46));
        row.add(tileIcon, 0, 0);
        VStack info = new VStack();
        fileName = Widgets.label("Chưa chọn bài", Theme.medium(14f), Theme.INK);
        fileInfo = Widgets.label("MP3 · WAV · FLAC · M4A · OGG…", Theme.font(12f), Theme.SUB);
        info.row(fileName, 0);
        info.row(fileInfo, 3);
        row.add(info, 1, 12);
        cv.row(row, 12);
        openBtn = new Pill("Mở file nhạc…", Style.OUTLINE, 40, this::pickFile).icon(Theme.IconKind.OPEN);
        cv.row(openBtn, 14);
        cv.row(Widgets.label("hoặc kéo thả file nhạc vào cửa sổ", Theme.font(11.5f), Theme.FAINT), 8);
        c.add(cv);
        v.row(c, 0);

        v.row(Widgets.caps("Phong cách"), 18);
        presetList = new PresetList();
        JScrollPane sp = new JScrollPane(presetList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setUI(new ThinScrollUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 8));
        sp.getVerticalScrollBar().setOpaque(false);
        v.grow(sp, 8);
        return v;
    }

    // ── giữa: sóng âm + phát + số đo
    private JComponent center() {
        VStack v = new VStack();
        v.setBorder(new EmptyBorder(0, 16, 16, 16));

        Card mon = new Card(new BorderLayout(0, 14), 18);
        HStack top = new HStack();
        top.add(Widgets.caps("Nghe thử & so sánh"), 1, 0);
        ab = new Segmented(new String[]{"GỐC", "MASTER"}, 1, 32, i -> setAB(i == 1));
        top.add(ab, 0, 0);
        mon.add(top, BorderLayout.NORTH);

        wave = new WaveView();
        wave.onSeek = f -> {
            try { player.seek((long) (f * player.frames())); } catch (Exception e) { error(e); }
            updatePlayhead();
        };
        mon.add(wave, BorderLayout.CENTER);

        HStack tr = new HStack();
        playBtn = new RoundIcon(Theme.IconKind.PLAY, 58, Theme.ACC, Theme.ACC_INK, null, this::togglePlay);
        tr.add(playBtn, 0, 0);
        VStack now = new VStack();
        nowText = Widgets.label("Chưa có bài", Theme.medium(15f), Theme.INK);
        timeText = Widgets.label("0:00 / 0:00", Theme.font(13f), Theme.SUB);
        now.row(nowText, 0);
        now.row(timeText, 3);
        tr.add(now, 1, 16);
        matchBtn = new Pill("Cân âm lượng", Style.OUTLINE, 36, () -> {
            matchLevel = !matchLevel;
            applyMatch();
            refreshPlayer();
        });
        matchBtn.setToolTipText("Hạ bản master bằng độ lớn bản gốc để so sánh màu tiếng công bằng — không bị lừa vì bản master to hơn");
        tr.add(matchBtn, 0, 10);
        mon.add(tr, BorderLayout.SOUTH);
        v.grow(mon, 0);

        JPanel tiles = new JPanel(new GridLayout(1, 4, 12, 0));
        tiles.setOpaque(false);
        tLufs = new Tile("Độ lớn");
        tPeak = new Tile("Đỉnh thật");
        tLim = new Tile("Limiter");
        tLen = new Tile("Bài nhạc");
        tiles.add(tLufs); tiles.add(tPeak); tiles.add(tLim); tiles.add(tLen);
        v.row(tiles, 12);
        return v;
    }

    // ── cột phải: tinh chỉnh + xuất + master
    private JComponent rightPanel() {
        VStack v = new VStack();
        v.setBorder(new EmptyBorder(0, 0, 16, 20));
        v.setPreferredSize(new Dimension(372, 100));

        Card tune = new Card(new BorderLayout(), 16);
        VStack tv = new VStack();
        HStack th = new HStack();
        th.add(Widgets.caps("Tinh chỉnh"), 1, 0);
        Pill reset = new Pill("Đặt lại", Style.GHOST, 26, () -> {
            Presets.ALL.get(Math.max(0, presetIdx)).applyTo(settings);
            if (presetIdx < 0) presetIdx = 0;
            refreshParams(); presetList.repaint(); refreshDirty();
        });
        reset.fontSize = 12f;
        reset.setToolTipText("Về lại thông số của phong cách đang chọn");
        th.add(reset, 0, 0);
        tv.row(th, 0);
        for (Presets.Param p : Presets.PARAMS) {
            ParamRow r = new ParamRow(p.title(), p.hint(), p.min(), p.max(), p.step(), p.get().applyAsDouble(settings),
                    p.fmt(), val -> {
                        p.set().accept(settings, val);
                        presetIdx = -1;
                        presetList.repaint();
                        refreshDirty();
                    });
            paramRows.add(r);
            tv.row(r, paramRows.size() == 1 ? 10 : 4);
        }
        tune.add(tv);
        v.row(tune, 0);

        Card exp = new Card(new BorderLayout(), 16);
        VStack ev = new VStack();
        ev.row(Widgets.caps("Xuất file"), 0);
        fmtSeg = new Segmented(new String[]{"WAV 24-bit", "WAV 16-bit", "MP3 320k"}, exportFmt, 34, i -> exportFmt = i);
        ev.row(fmtSeg, 10);
        ev.row(Widgets.label("<html>WAV 24-bit: cao nhất, để dựng video · 16-bit: có dither<br>MP3 320k: nhẹ, đăng mạng / gửi Zalo</html>",
                Theme.font(11.5f), Theme.FAINT), 8);
        saveBtn = new Pill("Lưu bản master…", Style.OUTLINE, 42, this::saveFile).icon(Theme.IconKind.SAVE);
        ev.row(saveBtn, 12);
        exp.add(ev);
        v.row(exp, 12);

        v.filler();
        dirtyNote = Widgets.label(" ", Theme.font(12f), Theme.SUB);
        dirtyNote.setHorizontalAlignment(SwingConstants.CENTER);
        v.row(dirtyNote, 10);
        masterBtn = new Pill("MASTER", Style.FILLED, 56, this::startMaster).radius(14);
        masterBtn.fontSize = 17f;
        v.row(masterBtn, 8);
        return v;
    }

    private JComponent statusBar() {
        HStack s = new HStack() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.PANEL);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.LINE);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        s.setBorder(new EmptyBorder(8, 20, 8, 20));
        status = Widgets.label("Sẵn sàng", Theme.font(12.5f), Theme.SUB);
        s.add(status, 1, 0);
        progress = new Progress();
        progress.setPreferredSize(new Dimension(280, 6));
        s.add(progress, 0, 12);
        cancelBtn = new Pill("Huỷ", Style.GHOST, 24, () -> cancel = true);
        cancelBtn.fontSize = 12f;
        s.add(cancelBtn, 0, 10);
        s.add(Widgets.label("Lọc siêu trầm → EQ → nén glue → bão hoà → stereo M/S → limiter true-peak", Theme.font(11.5f), Theme.FAINT), 0, 16);
        return s;
    }

    // ───────────────────────────── danh sách phong cách ─────────────────────────────

    private final class PresetList extends JComponent implements Scrollable {
        static final int ITEM = 52, GAP = 6;
        int hover = -1;

        PresetList() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int i = at(e.getY()); if (i != hover) { hover = i; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { int i = at(e.getY()); if (i >= 0) choosePreset(i); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        int at(int y) {
            int i = y / (ITEM + GAP);
            return i >= 0 && i < Presets.ALL.size() && y % (ITEM + GAP) < ITEM ? i : -1;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(250, Presets.ALL.size() * (ITEM + GAP));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.g2(g);
            int w = getWidth() - 10;
            for (int i = 0; i < Presets.ALL.size(); i++) {
                Presets.Preset p = Presets.ALL.get(i);
                int y = i * (ITEM + GAP);
                boolean sel = i == presetIdx, hv = i == hover;
                Theme.roundRect(g2, 0, y, w, ITEM, 12, sel ? Theme.ACC_SOFT : hv ? Theme.HOVER : Theme.CARD,
                        sel ? Theme.ACC : Theme.LINE);
                if (sel) {
                    g2.setColor(Theme.ACC);
                    g2.fillRoundRect(0, y + 12, 3, ITEM - 24, 3, 3);
                }
                g2.setFont(Theme.medium(13.5f));
                g2.setColor(sel ? Theme.ACC : Theme.INK);
                g2.drawString(p.name(), 16, y + 22);
                g2.setFont(Theme.font(11.5f));
                g2.setColor(Theme.SUB);
                g2.drawString(Theme.trimToWidth(p.desc(), g2.getFontMetrics(), w - 28), 16, y + 40);
            }
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return ITEM * 3; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class ThinScrollUI extends BasicScrollBarUI {
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = Theme.g2(g);
            Theme.roundRect(g2, r.x + 2, r.y, r.width - 4, r.height, 3, Theme.LINE, null);
            g2.dispose();
        }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
    }

    // ───────────────────────────── làm mới ─────────────────────────────

    private void refreshAll() {
        refreshFile();
        refreshParams();
        refreshPlayer();
        refreshStats();
        refreshDirty();
        presetList.repaint();
        wave.setData(origPeaks, masterPeaks, track == null ? 0 : track.frames, track == null ? 44100 : track.sampleRate);
        setBusy(busy, null, 0);
    }

    private void refreshFile() {
        if (track == null) {
            fileName.setText("Chưa chọn bài");
            fileInfo.setText("MP3 · WAV · FLAC · M4A · OGG…");
            openBtn.setText("Mở file nhạc…");
        } else {
            FontMetrics fm = fileName.getFontMetrics(fileName.getFont());
            fileName.setText(Theme.trimToWidth(trackName, fm, 170));
            fileName.setToolTipText(trackName);
            fileInfo.setText(String.format(Locale.US, "%.1f kHz · %s · %s", track.sampleRate / 1000.0,
                    track.srcChannels == 1 ? "Mono" : "Stereo", Theme.fmtTime(track.frames, track.sampleRate)));
            openBtn.setText("Đổi bài khác…");
        }
    }

    private void refreshParams() {
        for (int i = 0; i < Presets.PARAMS.size(); i++)
            paramRows.get(i).setValue(Presets.PARAMS.get(i).get().applyAsDouble(settings));
    }

    private boolean settingsChanged() {
        Mastering.Settings a = masteredWith, b = settings;
        if (a == null) return true;
        return a.targetLufs != b.targetLufs || a.ceilingDb != b.ceilingDb || a.bassDb != b.bassDb || a.mudDb != b.mudDb
                || a.presenceDb != b.presenceDb || a.airDb != b.airDb || a.warmth != b.warmth || a.comp != b.comp
                || a.width != b.width;
    }

    private void refreshDirty() {
        boolean has = track != null, mastered = masterL != null, changed = mastered && settingsChanged();
        String t;
        if (!has) t = "Mở một bài nhạc để bắt đầu";
        else if (changed) t = "Cài đặt đã đổi — bấm MASTER LẠI để áp dụng";
        else if (mastered) t = "✓ Đã master — bấm ▶ để nghe, rồi Lưu";
        else t = "Chọn phong cách rồi bấm MASTER";
        dirtyNote.setText(t);
        dirtyNote.setForeground(changed ? Theme.ACC : Theme.SUB);
        masterBtn.setText(mastered ? "MASTER LẠI" : "MASTER");
        masterBtn.setEnabled(has && !busy);
        saveBtn.setEnabled(mastered && !busy);
        openBtn.setEnabled(!busy);
    }

    private void refreshPlayer() {
        boolean has = track != null, m = player.useMaster && masterL != null;
        playBtn.setEnabled(has);
        playBtn.setKind(player.isPlaying() ? Theme.IconKind.PAUSE : Theme.IconKind.PLAY);
        ab.setSelected(m ? 1 : 0);
        ab.disabled[1] = masterL == null;
        ab.repaint();
        nowText.setText(!has ? "Chưa có bài" : m ? (matchLevel ? "Đang nghe: Master (đã cân âm lượng)" : "Đang nghe: Master") : "Đang nghe: Bản gốc");
        matchBtn.style = matchLevel ? Style.SOFT : Style.OUTLINE;
        matchBtn.setText(matchLevel ? "✓ Cân âm lượng" : "Cân âm lượng");
        matchBtn.setEnabled(masterL != null);
        updatePlayhead();
    }

    private void refreshStats() {
        if (track != null) {
            tLen.set(Theme.fmtTime(track.frames, track.sampleRate), "",
                    String.format(Locale.US, "%.1f kHz · %s", track.sampleRate / 1000.0, track.srcChannels == 1 ? "mono" : "stereo"));
        } else tLen.set("—", "", "chưa mở bài");
        Mastering.Result r = result;
        if (r == null) {
            tLufs.set("—", "LUFS", "mục tiêu " + Theme.num(settings.targetLufs));
            tPeak.set("—", "dBTP", "trần " + Theme.num(settings.ceilingDb));
            tLim.set("—", "dB", "mức nén tối đa");
            return;
        }
        tLufs.set(Theme.num(r.outLufs), "LUFS", "bản gốc " + Theme.num(r.inLufs) + " LUFS");
        tPeak.set(Theme.num(r.outTpDb), "dBTP", "bản gốc " + Theme.num(r.inTpDb) + " dBTP");
        tLim.set(Theme.num(r.limiterGrDb), "dB", r.limiterGrDb > -3 ? "nhẹ, giữ độ động" : r.limiterGrDb > -6 ? "vừa phải" : "mạnh, nghe kỹ");
    }

    private void updatePlayhead() {
        int n = player.frames();
        if (n <= 0) { timeText.setText("0:00 / 0:00"); return; }
        long pos = player.position();
        wave.setProgress(pos / (double) n);
        timeText.setText(Theme.fmtTime(pos, player.sampleRate()) + " / " + Theme.fmtTime(n, player.sampleRate()));
        playBtn.setKind(player.isPlaying() ? Theme.IconKind.PAUSE : Theme.IconKind.PLAY);
    }

    private void setBusy(boolean on, String text, double f) {
        busy = on;
        progress.setVisible(on);
        cancelBtn.setVisible(on);
        if (text != null) status.setText(text);
        else if (!on) status.setText(track == null ? "Sẵn sàng" : "Sẵn sàng · " + trackName);
        progress.set(f);
        refreshDirty();
    }

    // ───────────────────────────── hành động ─────────────────────────────

    private void choosePreset(int i) {
        presetIdx = i;
        Presets.ALL.get(i).applyTo(settings);
        refreshParams();
        presetList.repaint();
        refreshDirty();
        if (result == null) refreshStats();
    }

    private void pickFile() {
        if (busy) return;
        FileDialog d = new FileDialog(frame, "Mở file nhạc", FileDialog.LOAD);
        String dir = prefs.get("openDir", null);
        if (dir != null) d.setDirectory(dir);
        d.setFile("*.mp3;*.wav;*.flac;*.m4a;*.aac;*.ogg;*.opus;*.wma;*.aif;*.aiff;*.mp4");
        d.setVisible(true);
        if (d.getFile() == null) return;
        prefs.put("openDir", d.getDirectory());
        loadFile(new File(d.getDirectory(), d.getFile()));
    }

    private void loadFile(File f) {
        if (busy) return;
        player.setSource(null);
        track = null; masterL = masterR = null; result = null; masteredWith = null;
        origPeaks = masterPeaks = null;
        refreshAll();
        cancel = false;
        trackFile = f;
        String name = f.getName();
        setBusy(true, "Đang đọc “" + name + "”…", 0);
        new Thread(() -> {
            try {
                AudioEngine.Track t = AudioEngine.decode(f, x -> {
                    SwingUtilities.invokeLater(() -> progress.set(x));
                    return !cancel;
                });
                float[] pk = WaveView.peaks(t.left, t.right, t.frames);
                SwingUtilities.invokeLater(() -> {
                    track = t;
                    trackName = name;
                    origPeaks = pk;
                    player.setSource(t);
                    player.useMaster = true;
                    setBusy(false, null, 0);
                    refreshAll();
                    if (System.getProperty("codmaster.demo") != null) startMaster();
                });
            } catch (AudioEngine.Cancelled e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, "Đã huỷ", 0); refreshAll(); });
            } catch (OutOfMemoryError e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null, 0); refreshAll(); error(new Exception("Bài quá dài so với bộ nhớ.")); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null, 0); refreshAll(); error(e); });
            }
        }, "decode").start();
    }

    private void startMaster() {
        AudioEngine.Track t = track;
        if (t == null || busy) return;
        player.pause();
        player.setMaster(null, null);
        masterL = masterR = null;
        masterPeaks = null;
        wave.setData(origPeaks, null, t.frames, t.sampleRate);
        cancel = false;
        Mastering.Settings s = settings.copy();
        setBusy(true, "Chuẩn bị…", 0);
        refreshPlayer();
        new Thread(() -> {
            try {
                int n = t.frames;
                float[] l = java.util.Arrays.copyOf(t.left, n), r = java.util.Arrays.copyOf(t.right, n);
                long t0 = System.currentTimeMillis();
                long[] last = {0};
                Mastering.Result res = Mastering.process(l, r, n, t.sampleRate, s, (f, stage) -> {
                    long now = System.currentTimeMillis();
                    if (now - last[0] > 80) {
                        last[0] = now;
                        SwingUtilities.invokeLater(() -> {
                            status.setText(stage + "… " + (int) (f * 100) + "%");
                            progress.set(f);
                        });
                    }
                    return !cancel;
                });
                float[] pk = WaveView.peaks(l, r, n);
                double secs = (System.currentTimeMillis() - t0) / 1000.0;
                SwingUtilities.invokeLater(() -> {
                    masterL = l; masterR = r; result = res; masteredWith = s; masterPeaks = pk;
                    player.setMaster(l, r);
                    player.useMaster = true;
                    applyMatch();
                    setBusy(false, String.format(Locale.US, "✓ Master xong trong %.1f giây — %s LUFS, đỉnh %s dBTP",
                            secs, Theme.num(res.outLufs), Theme.num(res.outTpDb)), 1);
                    refreshAll();
                    status.setText(String.format(Locale.US, "✓ Master xong trong %.1f giây — %s LUFS, đỉnh %s dBTP",
                            secs, Theme.num(res.outLufs), Theme.num(res.outTpDb)));
                    String shot = System.getProperty("codmaster.shot");
                    if (shot != null) screenshot(new File(shot));
                });
            } catch (Mastering.CancelledException e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, "Đã huỷ", 0); refreshAll(); });
            } catch (OutOfMemoryError e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null, 0); refreshAll(); error(new Exception("Không đủ bộ nhớ để xử lý bài này.")); });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null, 0); refreshAll(); error(e); });
            }
        }, "master").start();
    }

    private void applyMatch() {
        Mastering.Result r = result;
        player.masterGain = matchLevel && r != null && r.inLufs > -60
                ? (float) Math.min(1.0, Math.pow(10, (r.inLufs - r.outLufs) / 20.0)) : 1f;
    }

    private void togglePlay() {
        if (track == null || busy) return;
        try {
            if (player.isPlaying()) player.pause(); else player.play();
        } catch (Exception e) {
            error(new Exception("Không mở được thiết bị âm thanh: " + e.getMessage()));
        }
        refreshPlayer();
    }

    private void setAB(boolean master) {
        if (master && masterL == null) { ab.setSelected(0); return; }
        player.useMaster = master;
        refreshPlayer();
    }

    private void saveFile() {
        if (masterL == null || busy) return;
        String ext = exportFmt == 2 ? ".mp3" : ".wav";
        String base = trackName.contains(".") ? trackName.substring(0, trackName.lastIndexOf('.')) : trackName;
        FileDialog d = new FileDialog(frame, "Lưu bản master", FileDialog.SAVE);
        String dir = prefs.get("saveDir", trackFile != null ? trackFile.getParent() : null);
        if (dir != null) d.setDirectory(dir);
        d.setFile(base + " (Master)" + ext);
        d.setVisible(true);
        if (d.getFile() == null) return;
        prefs.put("saveDir", d.getDirectory());
        String fn = d.getFile();
        if (!fn.toLowerCase(Locale.ROOT).endsWith(ext)) fn += ext;
        File out = new File(d.getDirectory(), fn);
        int fmt = exportFmt;
        float[] l = masterL, r = masterR;
        AudioEngine.Track t = track;
        cancel = false;
        setBusy(true, "Đang lưu " + out.getName() + "…", 0);
        new Thread(() -> {
            try {
                AudioEngine.Progress pr = x -> { SwingUtilities.invokeLater(() -> progress.set(x)); return !cancel; };
                if (fmt == 2) AudioEngine.writeMp3(out, base, l, r, t.frames, t.sampleRate, pr);
                else AudioEngine.writeWav(out, l, r, t.frames, t.sampleRate, fmt == 0 ? 24 : 16, pr);
                SwingUtilities.invokeLater(() -> setBusy(false, "✓ Đã lưu: " + out.getAbsolutePath(), 1));
            } catch (AudioEngine.Cancelled e) {
                out.delete();
                SwingUtilities.invokeLater(() -> setBusy(false, "Đã huỷ lưu", 0));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { setBusy(false, null, 0); error(e); });
            }
        }, "save").start();
    }

    private void error(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        status.setText("Lỗi: " + m.split("\n")[0]);
        if (System.getProperty("codmaster.demo") != null) { System.err.println("ERROR " + m); return; }
        JOptionPane.showMessageDialog(frame, m, "COD Master", JOptionPane.WARNING_MESSAGE);
    }

    // ───────────────────────────── kéo thả, phím tắt, biểu tượng ─────────────────────────────

    private void installDragDrop(JComponent root) {
        TransferHandler th = new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @Override @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) loadFile(files.get(0));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        frame.setTransferHandler(th);
        root.setTransferHandler(th);
        wave.setTransferHandler(th);
    }

    private void installKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || !frame.isActive()) return false;
            if (e.getKeyCode() == KeyEvent.VK_SPACE) { togglePlay(); return true; }
            if (e.getKeyCode() == KeyEvent.VK_B) { setAB(!(player.useMaster && masterL != null)); return true; }
            return false;
        });
    }

    static BufferedImage iconImage(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = Theme.g2(img.getGraphics());
        Theme.roundRect(g, 0, 0, s, s, s * 0.22, Theme.ACC, null);
        g.setColor(Theme.ACC_INK);
        double[] hs = {0.14, 0.34, 0.56, 0.40, 0.24, 0.10};
        double bw = s * 0.085, gap = s * 0.045;
        double total = hs.length * bw + (hs.length - 1) * gap;
        double x = (s - total) / 2;
        for (double h : hs) {
            double hh = Math.max(bw, s * h * 1.1);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x, (s - hh) / 2, bw, hh, bw, bw));
            x += bw + gap;
        }
        g.dispose();
        return img;
    }

    private static List<Image> appIcons() {
        List<Image> l = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128, 256}) l.add(iconImage(s));
        return l;
    }

    private void screenshot(File out) {
        try {
            JComponent c = (JComponent) frame.getContentPane();
            BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            c.printAll(g);
            g.dispose();
            ImageIO.write(img, "png", out);
            System.out.println("SHOT " + out + " " + String.format(Locale.US, "%.2f %.2f", result.outLufs, result.outTpDb));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (Boolean.getBoolean("codmaster.exit")) System.exit(0);
    }
}

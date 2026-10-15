package vn.daopham.amlich;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;

/** Vẽ nội dung cho 5 widget. */
final class Widgets {
    private Widgets() {}

    static final String[] THU_UP = {"CHỦ NHẬT", "THỨ HAI", "THỨ BA", "THỨ TƯ", "THỨ NĂM", "THỨ SÁU", "THỨ BẢY"};
    static final String[] WD = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};

    static final Class<?>[] PROVIDERS = {WidgetDay.class, WidgetDetail.class, WidgetMonth.class, WidgetTet.class, WidgetLine.class};

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        for (Class<?> cls : PROVIDERS) {
            int[] ids = m.getAppWidgetIds(new ComponentName(c, cls));
            for (int id : ids) update(c, m, id, cls);
        }
    }

    static void update(Context c, AppWidgetManager m, int id, Class<?> cls) {
        try {
            RemoteViews rv;
            if (cls == WidgetDetail.class) rv = detail(c, id);
            else if (cls == WidgetMonth.class) rv = month(c, id);
            else if (cls == WidgetTet.class) rv = tet(c, id);
            else if (cls == WidgetLine.class) rv = line(c, id);
            else rv = day(c, id);
            m.updateAppWidget(id, rv);
        } catch (Exception ignored) {
        }
    }

    private static int wd(LocalDate d) { return d.getDayOfWeek().getValue() % 7; }

    private static String lunarShort(int[] l) {
        return l[0] + "/" + l[1] + (l[3] == 1 ? "N" : "");
    }

    static RemoteViews day(Context c, int id) {
        LocalDate d = LocalDate.now();
        int dd = d.getDayOfMonth(), mm = d.getMonthValue(), yy = d.getYear();
        int[] l = Lunar.solar2lunar(dd, mm, yy);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_day);
        rv.setTextViewText(R.id.w_wd, THU_UP[wd(d)]);
        rv.setTextViewText(R.id.w_day, String.valueOf(dd));
        String hol = Lunar.holiday(dd, mm, yy);
        rv.setTextViewText(R.id.w_month, hol.isEmpty() ? "Tháng " + mm + " · " + yy : hol);
        rv.setTextViewText(R.id.w_lunar, lunarShort(l) + " · " + Lunar.yearCanChi(l[2]));
        rv.setOnClickPendingIntent(R.id.w_root, Notifier.openApp(c, d, "day", 100 + id % 1000));
        return rv;
    }

    static RemoteViews detail(Context c, int id) {
        LocalDate d = LocalDate.now();
        int dd = d.getDayOfMonth(), mm = d.getMonthValue(), yy = d.getYear();
        int[] l = Lunar.solar2lunar(dd, mm, yy);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_detail);
        rv.setTextViewText(R.id.w_wd, THU_UP[wd(d)]);
        rv.setTextViewText(R.id.w_day, String.valueOf(dd));
        rv.setTextViewText(R.id.w_month, "Tháng " + mm + "/" + yy);
        rv.setTextViewText(R.id.w_lunar, Lunar.lunarDayLabel(l) + " " + Lunar.lunarMonthName(l[1], l[3] == 1));
        rv.setTextViewText(R.id.w_canchi, "Ngày " + Lunar.dayCanChi(dd, mm, yy) + " · Tháng " + Lunar.monthCanChi(l[1], l[2]));
        rv.setTextViewText(R.id.w_year, "Năm " + Lunar.yearCanChi(l[2]) + " · " + Lunar.tietKhi(dd, mm, yy));
        boolean hd = Lunar.isHoangDao(dd, mm, yy);
        rv.setTextViewText(R.id.w_hd, hd ? "● Hoàng đạo" : "● Hắc đạo");
        rv.setTextColor(R.id.w_hd, hd ? 0xFF1F7A55 : 0xFFB3261E);
        rv.setTextViewText(R.id.w_gio, "Giờ tốt: " + Lunar.gioHoangDao(dd, mm, yy, false));
        String hol = Lunar.holiday(dd, mm, yy);
        rv.setTextViewText(R.id.w_hol, hol);
        rv.setViewVisibility(R.id.w_hol, hol.isEmpty() ? View.GONE : View.VISIBLE);
        rv.setOnClickPendingIntent(R.id.w_root, Notifier.openApp(c, d, "day", 1100 + id % 1000));
        return rv;
    }

    static RemoteViews tet(Context c, int id) {
        LocalDate d = LocalDate.now();
        int[] l = Lunar.solar2lunar(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_tet);
        if (l[1] == 1 && l[0] <= 3 && l[3] == 0) {
            rv.setTextViewText(R.id.w_num, "Tết");
            rv.setTextViewText(R.id.w_label, "Chúc mừng năm mới " + Lunar.yearCanChi(l[2]) + "!");
        } else {
            int[] t = Lunar.lunar2solar(1, 1, l[2] + 1, false);
            long n = t == null ? 0 : LocalDate.of(t[2], t[1], t[0]).toEpochDay() - d.toEpochDay();
            rv.setTextViewText(R.id.w_num, String.valueOf(n));
            rv.setTextViewText(R.id.w_label, "ngày nữa đến Tết " + Lunar.yearCanChi(l[2] + 1));
        }
        rv.setOnClickPendingIntent(R.id.w_root, Notifier.openApp(c, d, "day", 2100 + id % 1000));
        return rv;
    }

    static RemoteViews line(Context c, int id) {
        LocalDate d = LocalDate.now();
        int dd = d.getDayOfMonth(), mm = d.getMonthValue(), yy = d.getYear();
        int[] l = Lunar.solar2lunar(dd, mm, yy);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_line);
        rv.setTextViewText(R.id.w_l1, Lunar.THU[wd(d)] + ", " + dd + " tháng " + mm);
        String hol = Lunar.holiday(dd, mm, yy);
        rv.setTextViewText(R.id.w_l2, Lunar.lunarDayLabel(l) + " " + Lunar.lunarMonthName(l[1], l[3] == 1) + " · " + Lunar.yearCanChi(l[2])
                + (hol.isEmpty() ? " · Ngày " + Lunar.dayCanChi(dd, mm, yy) : " · " + hol));
        rv.setOnClickPendingIntent(R.id.w_root, Notifier.openApp(c, d, "day", 3100 + id % 1000));
        return rv;
    }

    /* ---------- Lịch tháng ---------- */
    static String offKey(int id) { return "moff_" + id; }

    static RemoteViews month(Context c, int id) {
        int off = Store.prefs(c).getInt(offKey(id), 0);
        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1).plusMonths(off);
        int ws = Store.weekStart(c);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_month);

        int[] lFirst = Lunar.solar2lunar(1, first.getMonthValue(), first.getYear());
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        int[] lLast = Lunar.solar2lunar(last.getDayOfMonth(), last.getMonthValue(), last.getYear());
        rv.setTextViewText(R.id.w_title, "Tháng " + first.getMonthValue() + ", " + first.getYear());
        rv.setTextViewText(R.id.w_sub, "Âm lịch tháng " + lFirst[1] + (lFirst[1] != lLast[1] ? "–" + lLast[1] : "") + " · " + Lunar.yearCanChi(lLast[2]));

        for (int i = 0; i < 7; i++) {
            int w = (ws + i) % 7;
            rv.setTextViewText(MonthIds.HEAD[i], WD[w]);
            rv.setTextColor(MonthIds.HEAD[i], w == 0 ? 0xFFB3261E : 0xFF968A80);
        }
        int lead = (wd(first) - ws + 7) % 7;
        LocalDate start = first.minusDays(lead);
        int rows = (lead + first.lengthOfMonth() + 6) / 7;
        for (int r = 0; r < 6; r++) rv.setViewVisibility(MonthIds.ROWS[r], r < rows ? View.VISIBLE : View.GONE);

        for (int i = 0; i < 42; i++) {
            LocalDate d = start.plusDays(i);
            int cid = MonthIds.CELLS[i];
            if (i >= rows * 7) continue;
            int[] l = Lunar.solar2lunar(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
            boolean in = d.getMonthValue() == first.getMonthValue();
            boolean isToday = d.equals(today);
            boolean sun = wd(d) == 0;
            boolean hol = !Lunar.holiday(d.getDayOfMonth(), d.getMonthValue(), d.getYear()).isEmpty();
            String s = String.valueOf(d.getDayOfMonth());
            String lt = l[0] == 1 ? l[0] + "/" + l[1] : String.valueOf(l[0]);
            SpannableString sp = new SpannableString(s + "\n" + lt);
            int dayColor = isToday ? 0xFFFFFFFF : (sun || hol) ? 0xFFB3261E : 0xFF2A221E;
            int lunColor = isToday ? 0xE6FFFFFF : l[0] == 1 ? 0xFFB3261E : l[0] == 15 ? 0xFFB8862B : 0xFF968A80;
            if (!in && !isToday) {
                dayColor = (dayColor & 0x00FFFFFF) | 0x55000000;
                lunColor = (lunColor & 0x00FFFFFF) | 0x55000000;
            }
            sp.setSpan(new ForegroundColorSpan(dayColor), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new RelativeSizeSpan(0.62f), s.length() + 1, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new ForegroundColorSpan(lunColor), s.length() + 1, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (l[0] == 1 || l[0] == 15) sp.setSpan(new StyleSpan(Typeface.BOLD), s.length() + 1, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rv.setTextViewText(cid, sp);
            rv.setInt(cid, "setBackgroundResource", isToday ? R.drawable.w_cell_today : hol && in ? R.drawable.w_cell_hol : 0);
            rv.setOnClickPendingIntent(cid, Notifier.openApp(c, d, "day", 10000 + (id % 1000) * 50 + i));
        }

        rv.setOnClickPendingIntent(R.id.w_prev, nav(c, id, WidgetMonth.ACT_PREV));
        rv.setOnClickPendingIntent(R.id.w_next, nav(c, id, WidgetMonth.ACT_NEXT));
        rv.setOnClickPendingIntent(R.id.w_title_box, off == 0 ? Notifier.openApp(c, today, "month", 9000 + id % 1000) : nav(c, id, WidgetMonth.ACT_TODAY));
        return rv;
    }

    private static PendingIntent nav(Context c, int id, String action) {
        Intent i = new Intent(c, WidgetMonth.class).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        int rc = (action.hashCode() & 0xff) * 1000 + id % 1000;
        return PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Lớp cơ sở cho các widget đơn giản. */
    abstract static class Base extends AppWidgetProvider {
        @Override
        public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
            for (int id : ids) update(c, m, id, getClass());
            AlarmReceiver.schedule(c);
        }
    }
}

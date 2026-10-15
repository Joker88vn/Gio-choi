package vn.daopham.amlich;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Thông báo: nhắc mùng 1/rằm, ngày lễ, sự kiện; thông báo cố định ngày âm. */
final class Notifier {
    private Notifier() {}

    static final String CH_REMIND = "remind";
    static final String CH_ONGOING = "ongoing";
    static final int ID_ONGOING = 1000;

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel r = new NotificationChannel(CH_REMIND, "Nhắc lịch", NotificationManager.IMPORTANCE_HIGH);
        r.setDescription("Mùng 1, Rằm, ngày lễ và sự kiện của bạn");
        nm.createNotificationChannel(r);
        NotificationChannel o = new NotificationChannel(CH_ONGOING, "Ngày âm hôm nay", NotificationManager.IMPORTANCE_LOW);
        o.setDescription("Hiện ngày âm lịch trên thanh trạng thái");
        o.setShowBadge(false);
        nm.createNotificationChannel(o);
    }

    static PendingIntent openApp(Context c, LocalDate d, String tab, int rc) {
        Intent i = new Intent(c, MainActivity.class)
                .putExtra("d", d.toString())
                .putExtra("t", tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void post(Context c, int id, String title, String text, LocalDate d) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, CH_REMIND)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFB3261E)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(c, d, "day", 5000 + (id & 0xfff)))
                .build();
        try {
            nm.notify(id, n);
        } catch (SecurityException ignored) {
        }
    }

    private static String lunarName(int[] l) {
        return Lunar.lunarDayLabel(l) + " " + Lunar.lunarMonthName(l[1], l[3] == 1);
    }

    static void runDaily(Context c) {
        ensureChannels(c);
        JSONObject s = Store.settings(c);
        LocalDate today = LocalDate.now(), tmr = today.plusDays(1);
        int[] lt = Lunar.solar2lunar(today.getDayOfMonth(), today.getMonthValue(), today.getYear());
        int[] ln = Lunar.solar2lunar(tmr.getDayOfMonth(), tmr.getMonthValue(), tmr.getYear());
        String holT = Lunar.holiday(today.getDayOfMonth(), today.getMonthValue(), today.getYear());
        String holN = Lunar.holiday(tmr.getDayOfMonth(), tmr.getMonthValue(), tmr.getYear());

        if (s.optBoolean("hol", true)) {
            if (!holT.isEmpty()) post(c, 2001, "Hôm nay: " + holT, lunarName(lt) + " · " + Lunar.dayCanChi(today.getDayOfMonth(), today.getMonthValue(), today.getYear()), today);
            if (!holN.isEmpty()) post(c, 2002, "Ngày mai: " + holN, "Đừng quên chuẩn bị nhé!", tmr);
        }
        if (s.optBoolean("ram", true)) {
            boolean dupT = s.optBoolean("hol", true) && !holT.isEmpty();
            boolean dupN = s.optBoolean("hol", true) && !holN.isEmpty();
            if ((lt[0] == 1 || lt[0] == 15) && !dupT)
                post(c, 2003, "Hôm nay " + lunarName(lt), "Ngày " + Lunar.dayCanChi(today.getDayOfMonth(), today.getMonthValue(), today.getYear()) + " · Giờ tốt: " + Lunar.gioHoangDao(today.getDayOfMonth(), today.getMonthValue(), today.getYear(), false), today);
            if ((ln[0] == 1 || ln[0] == 15) && !dupN)
                post(c, 2004, "Ngày mai là " + lunarName(ln), "Chuẩn bị hương hoa, lễ cúng cho gia đình.", tmr);
        }
        if (s.optBoolean("ev", true)) {
            JSONArray evs = Store.events(c);
            for (int i = 0; i < evs.length(); i++) {
                JSONObject ev = evs.optJSONObject(i);
                if (ev == null) continue;
                String title = ev.optString("title", "Sự kiện");
                int rem = ev.optInt("remind", 0);
                int base = 3000 + Math.abs(ev.optString("id", title).hashCode() % 100000) * 2;
                if (Store.eventOn(ev, today)) {
                    post(c, base, "Hôm nay: " + title, lunarName(lt) + " · " + today.getDayOfMonth() + "/" + today.getMonthValue() + "/" + today.getYear(), today);
                }
                if (rem > 0) {
                    LocalDate t = today.plusDays(rem);
                    if (Store.eventOn(ev, t)) {
                        int[] l = Lunar.solar2lunar(t.getDayOfMonth(), t.getMonthValue(), t.getYear());
                        post(c, base + 1, (rem == 1 ? "Ngày mai: " : "Còn " + rem + " ngày: ") + title,
                                Lunar.THU[t.getDayOfWeek().getValue() % 7] + ", " + t.getDayOfMonth() + "/" + t.getMonthValue() + " (" + lunarName(l) + ")", t);
                    }
                }
            }
        }
        updateOngoing(c);
    }

    private static Icon numberIcon(String text) {
        int sz = 96;
        Bitmap b = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(text.length() >= 2 ? 72 : 84);
        Paint.FontMetrics fm = p.getFontMetrics();
        float y = sz / 2f - (fm.ascent + fm.descent) / 2f;
        cv.drawText(text, sz / 2f, y, p);
        return Icon.createWithBitmap(b);
    }

    static void updateOngoing(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (!Store.settings(c).optBoolean("ongoing", false)) {
            nm.cancel(ID_ONGOING);
            return;
        }
        ensureChannels(c);
        LocalDate d = LocalDate.now();
        int dd = d.getDayOfMonth(), mm = d.getMonthValue(), yy = d.getYear();
        int[] l = Lunar.solar2lunar(dd, mm, yy);
        String hol = Lunar.holiday(dd, mm, yy);
        String title = lunarName(l) + " · Năm " + Lunar.yearCanChi(l[2]);
        String text = "Ngày " + Lunar.dayCanChi(dd, mm, yy) + " · " + (Lunar.isHoangDao(dd, mm, yy) ? "Hoàng đạo" : "Hắc đạo")
                + (hol.isEmpty() ? "" : " · " + hol);
        String big = text + "\nGiờ tốt: " + Lunar.gioHoangDao(dd, mm, yy, true);
        Notification n = new Notification.Builder(c, CH_ONGOING)
                .setSmallIcon(numberIcon(String.valueOf(l[0])))
                .setColor(0xFFB3261E)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(c, d, "day", 4999))
                .build();
        try {
            nm.notify(ID_ONGOING, n);
        } catch (SecurityException ignored) {
        }
    }
}

package vn.daopham.amlich;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Hẹn giờ: nhắc hằng ngày + cập nhật lúc nửa đêm; khởi động lại sau reboot/đổi giờ. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String ACT_DAILY = "vn.daopham.amlich.DAILY";
    static final String ACT_MIDNIGHT = "vn.daopham.amlich.MIDNIGHT";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        try {
            if (ACT_DAILY.equals(a)) {
                Notifier.runDaily(c);
            } else {
                Widgets.updateAll(c);
                Notifier.updateOngoing(c);
            }
        } catch (Exception ignored) {
        }
        schedule(c);
    }

    static void schedule(Context c) {
        String time = Store.settings(c).optString("time", "07:00");
        int hh = 7, mm = 0;
        try {
            String[] p = time.split(":");
            hh = Integer.parseInt(p[0]);
            mm = Integer.parseInt(p[1]);
        } catch (Exception ignored) {
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime daily = LocalDate.now().atTime(LocalTime.of(hh, mm));
        if (!daily.isAfter(now)) daily = daily.plusDays(1);
        LocalDateTime midnight = LocalDate.now().plusDays(1).atTime(0, 0, 5);
        set(c, ACT_DAILY, daily, 1);
        set(c, ACT_MIDNIGHT, midnight, 2);
    }

    private static void set(Context c, String action, LocalDateTime when, int rc) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long t = when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }
}

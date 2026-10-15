package vn.daopham.amlich;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Lưu cài đặt & sự kiện (dùng chung giữa giao diện và widget/thông báo). */
final class Store {
    private Store() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("data", Context.MODE_PRIVATE); }

    static String get(Context c, String k) { return prefs(c).getString(k, null); }

    static void put(Context c, String k, String v) { prefs(c).edit().putString(k, v).apply(); }

    static JSONObject settings(Context c) {
        try {
            String s = get(c, "settings");
            return s == null ? new JSONObject() : new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static JSONArray events(Context c) {
        try {
            String s = get(c, "events");
            return s == null ? new JSONArray() : new JSONArray(s);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static int weekStart(Context c) { return settings(c).optInt("weekStart", 1); }

    /** Sự kiện có rơi vào ngày d không (đồng bộ với occurrences() trong app.js). */
    static boolean eventOn(JSONObject ev, LocalDate d) {
        String type = ev.optString("type", "lunar");
        String repeat = ev.optString("repeat", "yearly");
        int day = ev.optInt("day", 1), month = ev.optInt("month", 1), year = ev.optInt("year", 0);
        boolean leap = ev.optBoolean("leap", false);
        if ("solar".equals(type)) {
            int clamp = Math.min(day, d.lengthOfMonth());
            switch (repeat) {
                case "none": return d.getDayOfMonth() == day && d.getMonthValue() == month && d.getYear() == year;
                case "monthly": return d.getDayOfMonth() == clamp;
                default: return d.getMonthValue() == month && d.getDayOfMonth() == Math.min(day, d.withDayOfMonth(1).withMonth(month).lengthOfMonth());
            }
        }
        int[] l = Lunar.solar2lunar(d.getDayOfMonth(), d.getMonthValue(), d.getYear());
        int clampL = Math.min(day, l[4]);
        switch (repeat) {
            case "none": return l[0] == clampL && l[1] == month && l[2] == year && (l[3] == 1) == leap;
            case "monthly": return l[0] == clampL;
            default: return l[3] == 0 && l[1] == month && l[0] == clampL;
        }
    }
}

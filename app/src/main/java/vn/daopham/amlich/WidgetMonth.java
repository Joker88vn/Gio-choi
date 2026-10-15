package vn.daopham.amlich;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;

public class WidgetMonth extends Widgets.Base {
    static final String ACT_PREV = "vn.daopham.amlich.M_PREV";
    static final String ACT_NEXT = "vn.daopham.amlich.M_NEXT";
    static final String ACT_TODAY = "vn.daopham.amlich.M_TODAY";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (ACT_PREV.equals(a) || ACT_NEXT.equals(a) || ACT_TODAY.equals(a)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id < 0) return;
            String k = Widgets.offKey(id);
            int off = Store.prefs(c).getInt(k, 0);
            off = ACT_TODAY.equals(a) ? 0 : off + (ACT_NEXT.equals(a) ? 1 : -1);
            Store.prefs(c).edit().putInt(k, off).apply();
            Widgets.update(c, AppWidgetManager.getInstance(c), id, WidgetMonth.class);
            return;
        }
        super.onReceive(c, intent);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Store.prefs(c).edit().remove(Widgets.offKey(id)).apply();
    }
}

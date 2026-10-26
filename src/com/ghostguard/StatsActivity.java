package com.ghostguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/** Ghost touches per day, per hour of the day, and per app. */
public class StatsActivity extends Activity {
    private SharedPreferences prefs;
    private Ui ui;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        ui = new Ui(this);
        build();
    }

    private void build() {
        LinearLayout root = ui.page();
        root.addView(ui.title("Statistics", "Blocked ghost touches over time", true));
        JSONObject o = Stats.load(prefs);

        int[] days = Stats.lastDays(o, 14);
        int week = 0, prevWeek = 0;
        for (int i = 0; i < 7; i++) { prevWeek += days[i]; week += days[i + 7]; }

        // Summary tiles.
        LinearLayout tiles = new LinearLayout(this);
        tiles.addView(tile(String.valueOf(days[13]), "Today"), weight(0, 6));
        tiles.addView(tile(String.valueOf(week), "Last 7 days"), weight(6, 6));
        tiles.addView(tile(trend(week, prevWeek), "vs. week before"), weight(6, 0));
        root.addView(tiles);

        // Per day.
        LinearLayout perDay = ui.card("Last 14 days");
        String[] dayLabels = new String[14];
        for (int i = 0; i < 14; i++) dayLabels[i] = Stats.dayLabel(13 - i);
        perDay.addView(new Bars(this, days, dayLabels, ui.warn));
        root.addView(perDay);

        // Per hour.
        LinearLayout perHour = ui.card("Time of day");
        JSONArray h = o.optJSONArray("hours");
        int[] hours = new int[24];
        String[] hourLabels = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = h == null ? 0 : h.optInt(i);
            hourLabels[i] = i % 6 == 0 ? i + "h" : "";
        }
        perHour.addView(new Bars(this, hours, hourLabels, ui.accent));
        int peak = 0;
        for (int i = 1; i < 24; i++) if (hours[i] > hours[peak]) peak = i;
        if (hours[peak] > 0) {
            TextView t = ui.label("Worst around " + peak + "h–" + (peak + 1) % 24 + "h", 13, ui.muted, false);
            t.setPadding(0, ui.dp(8), 0, 0);
            perHour.addView(t);
        }
        root.addView(perHour);

        // Per app.
        LinearLayout perApp = ui.card("By app");
        JSONObject apps = o.optJSONObject("apps");
        ArrayList<String> keys = new ArrayList<>();
        if (apps != null) for (Iterator<String> it = apps.keys(); it.hasNext(); ) keys.add(it.next());
        keys.sort((x, y) -> apps.optInt(y) - apps.optInt(x));
        if (keys.isEmpty()) {
            perApp.addView(ui.label("Nothing recorded yet.", 14, ui.muted, false));
        } else {
            int max = apps.optInt(keys.get(0));
            for (int i = 0; i < Math.min(8, keys.size()); i++) {
                perApp.addView(appRow(keys.get(i), apps.optInt(keys.get(i)), max));
            }
        }
        root.addView(perApp);

        root.addView(ui.button("Clear statistics", false, v ->
                new AlertDialog.Builder(this)
                        .setMessage("Erase the daily, hourly and per-app history?")
                        .setPositiveButton("Clear", (d, w) -> {
                            prefs.edit().remove(Prefs.STATS).apply();
                            build();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()));
    }

    private String trend(int now, int before) {
        if (before == 0) return now == 0 ? "—" : "new";
        int pct = Math.round((now - before) * 100f / before);
        return (pct > 0 ? "+" : "") + pct + "%";
    }

    private LinearLayout.LayoutParams weight(int l, int r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.leftMargin = ui.dp(l / 2f);
        lp.rightMargin = ui.dp(r / 2f);
        lp.bottomMargin = ui.dp(12);
        return lp;
    }

    private View tile(String value, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        c.setBackground(ui.rounded(ui.surface, ui.dp(20)));
        c.addView(ui.label(value, 24, ui.onSurface, true));
        c.addView(ui.label(label, 12, ui.muted, false));
        return c;
    }

    private View appRow(String pkg, int count, int max) {
        PackageManager pm = getPackageManager();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        ImageView icon = new ImageView(this);
        String name = pkg;
        try {
            icon.setImageDrawable(pm.getApplicationIcon(pkg));
            name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) {}
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        TextView n = ui.label(name, 15, ui.onSurface, false);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(n);
        View bar = new View(this);
        bar.setBackground(ui.rounded(ui.warn, ui.dp(3)));
        LinearLayout track = new LinearLayout(this);
        track.addView(bar, new LinearLayout.LayoutParams(0, ui.dp(6), Math.max(0.02f, (float) count / max)));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, ui.dp(6), Math.max(0f, 1f - (float) count / max)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = ui.dp(4);
        col.addView(track, tp);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.label(String.valueOf(count), 15, ui.accent, true));
        return row;
    }

    /** Minimal bar chart with labels under some bars. */
    private final class Bars extends View {
        private final int[] values;
        private final String[] labels;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG),
                grid = new Paint();

        Bars(Context c, int[] values, String[] labels, int color) {
            super(c);
            this.values = values;
            this.labels = labels;
            bar.setColor(color);
            text.setColor(ui.muted);
            text.setTextSize(11 * getResources().getDisplayMetrics().density);
            text.setTextAlign(Paint.Align.CENTER);
            grid.setColor((ui.muted & 0x00FFFFFF) | 0x26000000);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), ui.dp(140));
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float labelH = 18 * d, top = 18 * d, chartH = getHeight() - labelH - top;
            int max = 1;
            for (int v : values) max = Math.max(max, v);
            float slot = (float) getWidth() / values.length, bw = slot * 0.62f;
            c.drawLine(0, top + chartH, getWidth(), top + chartH, grid);
            for (int i = 0; i < values.length; i++) {
                float x = i * slot + (slot - bw) / 2;
                float hgt = values[i] == 0 ? 0 : Math.max(3 * d, chartH * values[i] / max);
                c.drawRoundRect(new RectF(x, top + chartH - hgt, x + bw, top + chartH), 3 * d, 3 * d, bar);
                if (!labels[i].isEmpty()) c.drawText(labels[i], x + bw / 2, getHeight() - 4 * d, text);
            }
            // Max value marker.
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(String.valueOf(max), 0, top - 6 * d, text);
            text.setTextAlign(Paint.Align.CENTER);
        }
    }
}

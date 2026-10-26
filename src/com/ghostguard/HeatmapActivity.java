package com.ghostguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Shows where ghost touches land and turns hotspots into custom zones. */
public class HeatmapActivity extends Activity {
    private SharedPreferences prefs;
    private Ui ui;
    private HeatView heat;
    private TextView summary;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        ui = new Ui(this);
        LinearLayout root = ui.page();
        root.addView(ui.title("Ghost heatmap", "Where blocked touches land", true));

        LinearLayout card = ui.card(null);
        heat = new HeatView(this, ui.muted, ui.bg);
        card.addView(heat);
        summary = ui.label("", 14, ui.muted, false);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, ui.dp(14), 0, ui.dp(4));
        card.addView(summary);
        root.addView(card);

        LinearLayout legend = ui.card(null);
        legend.addView(legendRow(HeatView.heatColor(0.15f), "Few ghost touches"));
        legend.addView(legendRow(HeatView.heatColor(1f), "Many ghost touches"));
        legend.addView(legendRow(0xFFF28B82, "Custom zone (outline)"));
        legend.addView(legendRow(0xFF81C995, "Top zone limit (dashed)"));
        root.addView(legend);

        root.addView(ui.button("Create zones from hotspots", true, v -> createZones()));
        root.addView(ui.button("Clear heatmap", false, v ->
                new AlertDialog.Builder(this)
                        .setMessage("Erase all recorded ghost touches?")
                        .setPositiveButton("Clear", (d, w) -> {
                            prefs.edit().remove(Prefs.HEAT).apply();
                            refresh();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        heat.grid = Heat.load(prefs);
        heat.zones = Zones.load(prefs);
        heat.topFrac = topFrac();
        heat.invalidate();
        int total = 0;
        for (int v : heat.grid) total += v;
        summary.setText(total == 0
                ? "Nothing recorded yet. Use your phone normally or run learning mode."
                : total + " ghost touches recorded");
    }

    private float topFrac() {
        float h = getSystemService(android.view.WindowManager.class)
                .getMaximumWindowMetrics().getBounds().height();
        return prefs.getInt(Prefs.ZONE_DP, 120) * getResources().getDisplayMetrics().density / h;
    }

    private void createZones() {
        List<RectF> hot = Heat.hotZones(Heat.load(prefs), 0.25f);
        float top = topFrac();
        hot.removeIf(r -> r.bottom <= top);          // already covered by the top zone
        if (hot.isEmpty()) {
            Toast.makeText(this, "No hotspots outside the top zone", Toast.LENGTH_SHORT).show();
            return;
        }
        List<RectF> zones = Zones.load(prefs);
        zones.addAll(hot);
        Zones.save(prefs, zones);
        MainActivity.needsRebuild = true;
        Toast.makeText(this, "Added " + hot.size() + (hot.size() == 1 ? " zone" : " zones"),
                Toast.LENGTH_SHORT).show();
        refresh();
    }

    private LinearLayout legendRow(int color, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(5), 0, ui.dp(5));
        android.view.View sw = new android.view.View(this);
        sw.setBackground(ui.rounded(color, ui.dp(4)));
        row.addView(sw, new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        TextView t = ui.label(text, 14, ui.onSurface, false);
        t.setPadding(ui.dp(12), 0, 0, 0);
        row.addView(t);
        return row;
    }
}

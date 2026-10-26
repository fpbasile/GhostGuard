package com.ghostguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    /** Set by other screens after they change settings shown here. */
    static boolean needsRebuild;

    private SharedPreferences prefs;
    private Ui ui;
    private TextView statusText, statusSub, blockedNum, passedNum, zonesSub, rulesSub, heatSub, statsSub;
    private View statusDot;
    private Button accessButton, pauseButton;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        ui = new Ui(this);
        LinearLayout root = ui.page();

        root.addView(header());
        root.addView(heroCard());
        root.addView(learnCard());

        LinearLayout zones = ui.card("Zones & tools");
        View z = ui.navRow("▦", "Custom zones", null,
                v -> startActivity(new Intent(this, ZoneEditorActivity.class)));
        zonesSub = sub(z);
        zones.addView(z);
        zones.addView(ui.divider());
        View h = ui.navRow("◉", "Ghost heatmap", null,
                v -> startActivity(new Intent(this, HeatmapActivity.class)));
        heatSub = sub(h);
        zones.addView(h);
        zones.addView(ui.divider());
        View r = ui.navRow("☰", "Per-app rules", null,
                v -> startActivity(new Intent(this, AppRulesActivity.class)));
        rulesSub = sub(r);
        zones.addView(r);
        zones.addView(ui.divider());
        View st = ui.navRow("▮", "Statistics", null,
                v -> startActivity(new Intent(this, StatsActivity.class)));
        statsSub = sub(st);
        zones.addView(st);
        root.addView(zones);

        LinearLayout prot = ui.card("Protection");
        prot.addView(toggle("Keyboard lock", "Block the whole top zone while typing",
                Prefs.KEYBOARD_LOCK, true));
        prot.addView(ui.divider());
        prot.addView(toggle("Flash red when blocking", "Brief red flash on each blocked touch",
                Prefs.FLASH, true));
        prot.addView(ui.divider());
        prot.addView(toggle("Show zones", "Green tint over protected areas", Prefs.SHOW, false));
        prot.addView(ui.divider());
        prot.addView(toggle("Block system gestures", "No back swipe or shade pull from zones",
                Prefs.NAV_BLOCK, false));
        prot.addView(ui.divider());
        prot.addView(toggle("Filter whole screen", "Slower: gestures replay after release",
                Prefs.FULL, false));
        root.addView(prot);

        LinearLayout tune = ui.card("Tuning");
        tune.addView(slider("Top zone height", 40, 400, Prefs.ZONE_DP, 120, "%d dp"));
        tune.addView(slider("Strictness", 1, 5, Prefs.LEVEL, 3, "%d / 5"));
        tune.addView(slider("Cascade guard", 0, 2000, Prefs.CASCADE_MS, 700, "%d ms"));
        TextView cg = ui.label("Blocks the top zone for a moment after you touch elsewhere — "
                + "ghosts that your own taps set off.", 13, ui.muted, false);
        cg.setPadding(0, 0, 0, ui.dp(4));
        tune.addView(cg);
        root.addView(tune);

        LinearLayout disp = ui.card("Display");
        disp.addView(toggle("Shrink screen", "Two equal black bars hide the broken area",
                Prefs.SHRINK, false));
        disp.addView(slider("Bar height", 20, 300, Prefs.BAR_DP, 110, "%d dp"));
        root.addView(disp);
        prefs.registerOnSharedPreferenceChangeListener(listener);

        TextView foot = ui.label("No internet · nothing leaves your phone\n"
                + "Tip: add the GhostGuard tile to Quick Settings", 13, ui.muted, false);
        foot.setGravity(Gravity.CENTER);
        foot.setLineSpacing(0, 1.3f);
        foot.setPadding(ui.dp(8), ui.dp(12), ui.dp(8), 0);
        root.addView(foot);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsRebuild) {
            needsRebuild = false;
            recreate();
            return;
        }
        refresh();
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
        if (Prefs.SHRINK.equals(key) || Prefs.BAR_DP.equals(key)) applyShrink(sp);
        if (Prefs.BLOCKED.equals(key) || Prefs.PASSED.equals(key) || Prefs.ENABLED.equals(key)
                || Prefs.LEVEL.equals(key) || Prefs.ZONE_DP.equals(key)) refresh();
    };

    private void applyShrink(SharedPreferences sp) {
        int bar = sp.getBoolean(Prefs.SHRINK, false) ? ui.dp(sp.getInt(Prefs.BAR_DP, 110)) : 0;
        if (!DisplayResizer.apply(this, bar)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Connect the phone to a computer and run once:\n\n"
                            + "adb shell pm grant com.ghostguard android.permission.WRITE_SECURE_SETTINGS")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void refresh() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = enabled != null && enabled.contains(getPackageName());
        boolean paused = !prefs.getBoolean(Prefs.ENABLED, true);
        int color = !on ? ui.warn : paused ? ui.muted : ui.good;
        statusText.setText(!on ? "Service is off" : paused ? "Paused" : "Protecting your screen");
        int zoneCount = Zones.load(prefs).size();
        statusSub.setText(prefs.getInt(Prefs.ZONE_DP, 120) + " dp top zone · strictness "
                + prefs.getInt(Prefs.LEVEL, 3) + "/5 · " + zoneCount
                + (zoneCount == 1 ? " custom zone" : " custom zones"));
        ((GradientDrawable) statusDot.getBackground()).setColor(color);
        accessButton.setVisibility(on ? View.GONE : View.VISIBLE);
        pauseButton.setVisibility(on ? View.VISIBLE : View.GONE);
        pauseButton.setText(paused ? "Resume protection" : "Pause protection");
        blockedNum.setText(String.valueOf(prefs.getLong(Prefs.BLOCKED, 0)));
        passedNum.setText(String.valueOf(prefs.getLong(Prefs.PASSED, 0)));

        zonesSub.setText(zoneCount == 0 ? "Draw areas where every touch is blocked"
                : zoneCount + (zoneCount == 1 ? " zone" : " zones") + " · every touch inside is blocked");
        int hits = 0;
        for (int v : Heat.load(prefs)) hits += v;
        heatSub.setText(hits == 0 ? "Where ghost touches land — nothing recorded yet"
                : hits + " ghost touches recorded · tap to view");
        int rules = 0;
        for (String k : prefs.getAll().keySet()) if (k.startsWith(Prefs.APP_PREFIX)) rules++;
        int[] week = Stats.lastDays(Stats.load(prefs), 7);
        int sum = 0;
        for (int v : week) sum += v;
        statsSub.setText(sum == 0 ? "Ghosts per day, hour and app"
                : week[6] + " today · " + sum + " this week");
        rulesSub.setText(rules == 0 ? "Turn off or tighten the filter per app"
                : rules + (rules == 1 ? " app" : " apps") + " with a custom rule");
    }

    // --- Sections --------------------------------------------------------

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(20));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher_fg);
        icon.setScaleX(1.6f);
        icon.setScaleY(1.6f);
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(ui.dp(14), 0, 0, 0);
        col.addView(ui.label("GhostGuard", 28, ui.onSurface, true));
        col.addView(ui.label("Ghost touch blocker", 14, ui.muted, false));
        row.addView(col);
        return row;
    }

    private View heroCard() {
        LinearLayout c = ui.card(null);
        c.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(18));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        statusDot.setBackground(dot);
        row.addView(statusDot, new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)));
        statusText = ui.label("", 19, ui.onSurface, true);
        statusText.setPadding(ui.dp(12), 0, 0, 0);
        row.addView(statusText);
        c.addView(row);

        statusSub = ui.label("", 13, ui.muted, false);
        statusSub.setPadding(ui.dp(24), ui.dp(4), 0, ui.dp(16));
        c.addView(statusSub);

        LinearLayout stats = new LinearLayout(this);
        blockedNum = ui.label("0", 34, ui.warn, true);
        passedNum = ui.label("0", 34, ui.good, true);
        stats.addView(stat(blockedNum, "Blocked"), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(stat(passedNum, "Passed"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView reset = ui.label("Reset", 14, ui.accent, true);
        reset.setPadding(ui.dp(12), ui.dp(8), ui.dp(4), ui.dp(8));
        reset.setOnClickListener(v -> {
            prefs.edit().putLong(Prefs.BLOCKED, 0).putLong(Prefs.PASSED, 0).apply();
            refresh();
        });
        stats.addView(reset);
        c.addView(stats);

        accessButton = ui.button("Open accessibility settings", true,
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        pauseButton = ui.button("", false, v ->
                prefs.edit().putBoolean(Prefs.ENABLED, !prefs.getBoolean(Prefs.ENABLED, true)).apply());
        ((LinearLayout.LayoutParams) accessButton.getLayoutParams()).topMargin = ui.dp(16);
        ((LinearLayout.LayoutParams) pauseButton.getLayoutParams()).topMargin = ui.dp(16);
        c.addView(accessButton);
        c.addView(pauseButton);
        return c;
    }

    private View learnCard() {
        LinearLayout c = ui.card(null);
        c.setBackground(ui.rounded(ui.accentSoft, ui.dp(24)));
        c.addView(ui.label("Auto-tune in 3 minutes", 18, ui.onSurface, true));
        TextView t = ui.label("Leave the phone alone while it flashes colors and vibrates. "
                + "GhostGuard records every ghost touch and suggests the best settings.",
                14, ui.muted, false);
        t.setPadding(0, ui.dp(4), 0, ui.dp(6));
        t.setLineSpacing(0, 1.2f);
        c.addView(t);
        c.addView(ui.button("Start learning mode", true,
                v -> startActivity(new Intent(this, LearnActivity.class))));
        return c;
    }

    // --- Building blocks -------------------------------------------------

    /** The subtitle TextView of a navRow, to update later. */
    private TextView sub(View navRow) {
        LinearLayout col = (LinearLayout) ((LinearLayout) navRow).getChildAt(1);
        TextView t = ui.label("", 13, ui.muted, false);
        col.addView(t);
        return t;
    }

    private View stat(TextView num, String name) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(num);
        col.addView(ui.label(name, 13, ui.muted, false));
        return col;
    }

    private static String fmtValue(String fmt, int v) {
        return v == 0 ? "Off" : String.format(fmt, v);
    }

    private View toggle(String title, String subtitle, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(ui.label(title, 16, ui.onSurface, false));
        col.addView(ui.label(subtitle, 13, ui.muted, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        Switch s = new Switch(this);
        s.setChecked(prefs.getBoolean(key, def));
        s.setThumbTintList(ui.stateColors(ui.accent, ui.muted));
        s.setTrackTintList(ui.stateColors(ui.accent, ui.muted));
        s.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean(key, c).apply());
        row.addView(s);
        row.setOnClickListener(v -> s.toggle());
        return row;
    }

    private View slider(String title, int min, int max, String key, int def, String fmt) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, ui.dp(8), 0, ui.dp(8));
        LinearLayout row = new LinearLayout(this);
        row.addView(ui.label(title, 16, ui.onSurface, false), new LinearLayout.LayoutParams(0, -2, 1));
        int cur = prefs.getInt(key, def);
        TextView value = ui.label(fmtValue(fmt, cur), 15, ui.accent, true);
        row.addView(value);
        col.addView(row);

        SeekBar sb = new SeekBar(this);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(cur);
        sb.setProgressTintList(ColorStateList.valueOf(ui.accent));
        sb.setThumbTintList(ColorStateList.valueOf(ui.accent));
        sb.setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(4));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                value.setText(fmtValue(fmt, p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putInt(key, s.getProgress()).apply();
            }
        });
        col.addView(sb);
        return col;
    }
}

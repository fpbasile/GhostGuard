package com.ghostguard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView statusText, blockedNum, passedNum;
    private View statusDot;
    private Button statusButton;

    // Material You colors, picked for light or dark mode.
    private int bg, surface, onSurface, muted, accent, onAccent, good, warn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        loadColors();
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(32));
        root.setBackgroundColor(bg);

        root.addView(header());
        root.addView(statusCard());

        LinearLayout prot = card("Protection");
        prot.addView(toggle("Filter enabled", "Analyze touches and drop ghost taps",
                Prefs.ENABLED, true));
        prot.addView(divider());
        prot.addView(toggle("Show protected zone", "Green tint, flashes red when blocking",
                Prefs.SHOW, true));
        prot.addView(divider());
        prot.addView(toggle("Filter whole screen", "Slower: gestures replay after release",
                Prefs.FULL, false));
        root.addView(prot);

        LinearLayout tune = card("Tuning");
        tune.addView(slider("Top zone height", 40, 400, Prefs.ZONE_DP, 120, "%d dp"));
        tune.addView(slider("Strictness", 1, 5, Prefs.LEVEL, 3, "%d / 5"));
        root.addView(tune);

        LinearLayout disp = card("Display");
        disp.addView(toggle("Shrink screen", "Two equal black bars hide the broken area",
                Prefs.SHRINK, false));
        disp.addView(slider("Bar height", 20, 300, Prefs.BAR_DP, 110, "%d dp"));
        root.addView(disp);
        prefs.registerOnSharedPreferenceChangeListener(shrinkListener);

        root.addView(statsCard());

        TextView tip = label("Tip: add the GhostGuard tile to Quick Settings to pause it quickly.",
                13, muted, false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(8), dp(16), dp(8), 0);
        root.addView(tip);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        sv.setFitsSystemWindows(true);
        sv.addView(root);
        setContentView(sv);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener shrinkListener = (sp, key) -> {
        if (!Prefs.SHRINK.equals(key) && !Prefs.BAR_DP.equals(key)) return;
        int bar = sp.getBoolean(Prefs.SHRINK, false) ? dp(sp.getInt(Prefs.BAR_DP, 110)) : 0;
        if (!DisplayResizer.apply(this, bar)) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("Connect the phone to a computer and run once:\n\n"
                            + "adb shell pm grant com.ghostguard android.permission.WRITE_SECURE_SETTINGS")
                    .setPositiveButton("OK", null)
                    .show();
        }
    };

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(shrinkListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = enabled != null && enabled.contains(getPackageName());
        boolean paused = !prefs.getBoolean(Prefs.ENABLED, true);
        int color = !on ? warn : paused ? muted : good;
        statusText.setText(!on ? "Service is off" : paused ? "Paused" : "Protecting your screen");
        ((GradientDrawable) statusDot.getBackground()).setColor(color);
        statusButton.setVisibility(on ? View.GONE : View.VISIBLE);
        blockedNum.setText(String.valueOf(prefs.getLong(Prefs.BLOCKED, 0)));
        passedNum.setText(String.valueOf(prefs.getLong(Prefs.PASSED, 0)));
    }

    // --- Sections --------------------------------------------------------

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, 0, dp(20));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher_fg);
        icon.setScaleX(1.6f);
        icon.setScaleY(1.6f);
        row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(label("GhostGuard", 26, onSurface, true));
        col.addView(label("Ghost touch blocker", 14, muted, false));
        row.addView(col);
        return row;
    }

    private View statusCard() {
        LinearLayout c = card(null);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        statusDot.setBackground(dot);
        row.addView(statusDot, new LinearLayout.LayoutParams(dp(12), dp(12)));
        statusText = label("", 17, onSurface, true);
        statusText.setPadding(dp(12), 0, 0, 0);
        row.addView(statusText);
        c.addView(row);

        statusButton = new Button(this);
        statusButton.setText("Open accessibility settings");
        statusButton.setAllCaps(false);
        statusButton.setTextColor(onAccent);
        statusButton.setBackground(rounded(accent, dp(24)));
        statusButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(14);
        c.addView(statusButton, lp);
        return c;
    }

    private View statsCard() {
        LinearLayout c = card("Activity");
        LinearLayout row = new LinearLayout(this);
        blockedNum = label("0", 32, warn, true);
        passedNum = label("0", 32, good, true);
        row.addView(stat(blockedNum, "Blocked"), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(stat(passedNum, "Passed"), new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(row);

        TextView reset = label("Reset counters", 14, accent, true);
        reset.setPadding(0, dp(14), 0, dp(4));
        reset.setOnClickListener(v -> {
            prefs.edit().putLong(Prefs.BLOCKED, 0).putLong(Prefs.PASSED, 0).apply();
            refresh();
        });
        c.addView(reset);
        return c;
    }

    // --- Building blocks -------------------------------------------------

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(rounded(surface, dp(20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        if (title != null) {
            TextView t = label(title.toUpperCase(), 12, accent, true);
            t.setLetterSpacing(0.08f);
            t.setPadding(0, 0, 0, dp(8));
            c.addView(t);
        }
        return c;
    }

    private View toggle(String title, String sub, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(label(title, 16, onSurface, false));
        col.addView(label(sub, 13, muted, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        Switch s = new Switch(this);
        s.setChecked(prefs.getBoolean(key, def));
        s.setThumbTintList(stateColors(accent, muted));
        s.setTrackTintList(stateColors(accent, muted));
        s.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean(key, c).apply();
            refresh();
        });
        row.addView(s);
        row.setOnClickListener(v -> s.toggle());
        return row;
    }

    private View slider(String title, int min, int max, String key, int def, String fmt) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(8), 0, dp(8));
        LinearLayout row = new LinearLayout(this);
        row.addView(label(title, 16, onSurface, false), new LinearLayout.LayoutParams(0, -2, 1));
        int cur = prefs.getInt(key, def);
        TextView value = label(String.format(fmt, cur), 15, accent, true);
        row.addView(value);
        col.addView(row);

        SeekBar sb = new SeekBar(this);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(cur);
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setPadding(dp(4), dp(10), dp(4), dp(4));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                value.setText(String.format(fmt, p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putInt(key, s.getProgress()).apply();
            }
        });
        col.addView(sb);
        return col;
    }

    private View stat(TextView num, String name) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(num);
        col.addView(label(name, 13, muted, false));
        return col;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor((muted & 0x00FFFFFF) | 0x26000000);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2)));
        return d;
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private ColorStateList stateColors(int on, int off) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{on, off});
    }

    private void loadColors() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = c(night ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
        surface = c(night ? android.R.color.system_neutral2_800 : android.R.color.system_neutral2_100);
        onSurface = c(night ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900);
        muted = c(night ? android.R.color.system_neutral2_400 : android.R.color.system_neutral2_600);
        accent = c(night ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        onAccent = c(night ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        good = night ? 0xFF81C995 : 0xFF1E8E3E;
        warn = night ? 0xFFF28B82 : 0xFFD93025;
    }

    private int c(int res) { return getColor(res); }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

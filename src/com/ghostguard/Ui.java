package com.ghostguard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared Material You look for every screen: colors, cards, rows, buttons. */
final class Ui {
    final Activity a;
    final int bg, surface, surfaceHi, onSurface, muted, accent, onAccent, accentSoft, good, warn;

    Ui(Activity a) {
        this.a = a;
        boolean night = (a.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = c(night ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
        surface = c(night ? android.R.color.system_neutral2_800 : android.R.color.system_neutral2_100);
        surfaceHi = c(night ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_200);
        onSurface = c(night ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900);
        muted = c(night ? android.R.color.system_neutral2_400 : android.R.color.system_neutral2_600);
        accent = c(night ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        onAccent = c(night ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
        accentSoft = c(night ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100);
        good = night ? 0xFF81C995 : 0xFF1E8E3E;
        warn = night ? 0xFFF28B82 : 0xFFD93025;
        a.getWindow().setStatusBarColor(bg);
        a.getWindow().setNavigationBarColor(bg);
    }

    /** Scrolling page with standard padding; returns the column to fill. */
    LinearLayout page() {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(40));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(bg);
        sv.setFitsSystemWindows(true);
        sv.addView(root);
        a.setContentView(sv);
        return root;
    }

    /** Big title with an optional back arrow and subtitle. */
    View title(String text, String sub, boolean back) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(20));
        if (back) {
            TextView arrow = label("←", 26, onSurface, false);
            arrow.setGravity(Gravity.CENTER);
            arrow.setBackground(ripple(surface, dp(22)));
            arrow.setOnClickListener(v -> a.finish());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.rightMargin = dp(14);
            row.addView(arrow, lp);
        }
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(label(text, 26, onSurface, true));
        if (sub != null) col.addView(label(sub, 14, muted, false));
        row.addView(col);
        return row;
    }

    LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(rounded(surface, dp(24)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        if (title != null) c.addView(section(title));
        return c;
    }

    TextView section(String title) {
        TextView t = label(title.toUpperCase(), 12, accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    /** Tappable list row: round glyph badge, title, subtitle, chevron. */
    View navRow(String glyph, String title, String sub, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.setBackground(ripple(0, dp(16)));
        row.setOnClickListener(l);

        TextView badge = label(glyph, 18, onSurface, false);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(accentSoft, dp(20)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        col.addView(label(title, 16, onSurface, false));
        if (sub != null) col.addView(label(sub, 13, muted, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("›", 24, muted, false));
        return row;
    }

    Button button(String text, boolean primary, View.OnClickListener l) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? onAccent : onSurface);
        b.setBackground(ripple(primary ? accent : surfaceHi, dp(24)));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    View divider() {
        View d = new View(a);
        d.setBackgroundColor((muted & 0x00FFFFFF) | 0x26000000);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2)));
        return d;
    }

    TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    RippleDrawable ripple(int color, int radius) {
        GradientDrawable mask = rounded(0xFFFFFFFF, radius);
        return new RippleDrawable(ColorStateList.valueOf((muted & 0x00FFFFFF) | 0x33000000),
                color == 0 ? null : rounded(color, radius), mask);
    }

    ColorStateList stateColors(int on, int off) {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{on, off});
    }

    int dp(float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private int c(int res) { return a.getColor(res); }
}

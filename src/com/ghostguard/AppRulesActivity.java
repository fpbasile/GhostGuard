package com.ghostguard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Per-app filter mode: tap an app to cycle Default → Strict → Off. */
public class AppRulesActivity extends Activity {
    private SharedPreferences prefs;
    private Ui ui;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        ui = new Ui(this);
        LinearLayout root = ui.page();
        root.addView(ui.title("Per-app rules", "Tap an app to change its mode", true));

        LinearLayout help = ui.card(null);
        help.addView(ui.label("Default — your normal settings\n"
                + "Strict — strictness 5/5 in this app\n"
                + "Off — no filtering at all in this app", 14, ui.muted, false));
        root.addView(help);

        LinearLayout list = ui.card(null);
        list.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        root.addView(list);

        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        apps.removeIf(r -> r.activityInfo.packageName.equals(getPackageName()));
        apps.sort((x, y) -> {
            // Apps with a rule first, then alphabetical.
            boolean rx = !AppRules.DEFAULT.equals(AppRules.get(prefs, x.activityInfo.packageName));
            boolean ry = !AppRules.DEFAULT.equals(AppRules.get(prefs, y.activityInfo.packageName));
            if (rx != ry) return rx ? -1 : 1;
            return x.loadLabel(pm).toString().compareToIgnoreCase(y.loadLabel(pm).toString());
        });
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) list.addView(ui.divider());
            list.addView(row(pm, apps.get(i)));
        }
    }

    private View row(PackageManager pm, ResolveInfo r) {
        String pkg = r.activityInfo.packageName;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(6), ui.dp(10), ui.dp(6), ui.dp(10));
        row.setBackground(ui.ripple(0, ui.dp(14)));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(r.loadIcon(pm));
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));

        TextView name = ui.label(r.loadLabel(pm).toString(), 16, ui.onSurface, false);
        name.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView chip = ui.label("", 13, ui.onSurface, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6));
        row.addView(chip);
        style(chip, AppRules.get(prefs, pkg));

        row.setOnClickListener(v -> {
            String next = AppRules.next(AppRules.get(prefs, pkg));
            AppRules.set(prefs, pkg, next);
            style(chip, next);
        });
        return row;
    }

    private void style(TextView chip, String mode) {
        switch (mode) {
            case AppRules.STRICT:
                chip.setText("Strict");
                chip.setTextColor(0xFF1F1F1F);
                chip.setBackground(ui.rounded(ui.warn, ui.dp(14)));
                break;
            case AppRules.OFF:
                chip.setText("Off");
                chip.setTextColor(ui.onSurface);
                chip.setBackground(ui.rounded(ui.surfaceHi, ui.dp(14)));
                break;
            default:
                chip.setText("Default");
                chip.setTextColor(ui.muted);
                chip.setBackground(ui.rounded(0, ui.dp(14)));
        }
    }
}

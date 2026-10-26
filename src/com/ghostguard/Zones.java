package com.ghostguard;

import android.content.SharedPreferences;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Custom block zones, stored as screen fractions: "l,t,r,b;l,t,r,b". */
final class Zones {

    static List<RectF> load(SharedPreferences p) {
        ArrayList<RectF> out = new ArrayList<>();
        String s = p.getString(Prefs.ZONES, "");
        for (String part : s.split(";")) {
            String[] v = part.split(",");
            if (v.length != 4) continue;
            try {
                out.add(new RectF(Float.parseFloat(v[0]), Float.parseFloat(v[1]),
                        Float.parseFloat(v[2]), Float.parseFloat(v[3])));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    static void save(SharedPreferences p, List<RectF> zones) {
        StringBuilder sb = new StringBuilder();
        for (RectF r : zones) {
            if (sb.length() > 0) sb.append(';');
            sb.append(String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", r.left, r.top, r.right, r.bottom));
        }
        p.edit().putString(Prefs.ZONES, sb.toString()).apply();
    }

    private Zones() {}
}

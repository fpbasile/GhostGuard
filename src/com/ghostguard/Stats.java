package com.ghostguard;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Blocked-touch history, stored as JSON:
 * {"days": {"2026-10-26": n}, "hours": [24 counts], "apps": {"pkg": n}}.
 * Days older than 60 are dropped.
 */
final class Stats {
    static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    static JSONObject load(SharedPreferences p) {
        try {
            JSONObject o = new JSONObject(p.getString(Prefs.STATS, "{}"));
            if (!o.has("days")) o.put("days", new JSONObject());
            if (!o.has("apps")) o.put("apps", new JSONObject());
            if (!o.has("hours")) {
                JSONArray h = new JSONArray();
                for (int i = 0; i < 24; i++) h.put(0);
                o.put("hours", h);
            }
            return o;
        } catch (JSONException e) {
            p.edit().remove(Prefs.STATS).apply();
            return load(p);
        }
    }

    /** Adds one blocked touch per entry; each entry is the foreground app's package. */
    static void record(SharedPreferences p, List<String> pkgs, long now) {
        try {
            JSONObject o = load(p);
            JSONObject days = o.getJSONObject("days"), apps = o.getJSONObject("apps");
            JSONArray hours = o.getJSONArray("hours");
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            String day = DAY.format(c.getTime());
            int hour = c.get(Calendar.HOUR_OF_DAY);
            for (String pkg : pkgs) {
                days.put(day, days.optInt(day) + 1);
                hours.put(hour, hours.optInt(hour) + 1);
                String key = pkg.isEmpty() ? "unknown" : pkg;
                apps.put(key, apps.optInt(key) + 1);
            }
            c.add(Calendar.DAY_OF_YEAR, -60);
            String cutoff = DAY.format(c.getTime());
            for (Iterator<String> it = days.keys(); it.hasNext(); ) {
                if (it.next().compareTo(cutoff) < 0) it.remove();
            }
            p.edit().putString(Prefs.STATS, o.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /** Counts for the last n days, oldest first. */
    static int[] lastDays(JSONObject o, int n) {
        int[] out = new int[n];
        JSONObject days = o.optJSONObject("days");
        Calendar c = Calendar.getInstance();
        for (int i = n - 1; i >= 0; i--) {
            out[i] = days == null ? 0 : days.optInt(DAY.format(c.getTime()));
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return out;
    }

    static String dayLabel(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return new SimpleDateFormat("EEE", Locale.US).format(new Date(c.getTimeInMillis())).substring(0, 1);
    }

    private Stats() {}
}

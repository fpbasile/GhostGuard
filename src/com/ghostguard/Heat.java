package com.ghostguard;

import android.content.SharedPreferences;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Ghost-touch heatmap: hit counts on a coarse grid over the screen. */
final class Heat {
    static final int COLS = 12, ROWS = 26;

    static int[] load(SharedPreferences p) {
        int[] g = new int[COLS * ROWS];
        String[] v = p.getString(Prefs.HEAT, "").split(",");
        if (v.length == g.length) {
            for (int i = 0; i < g.length; i++) {
                try { g[i] = Integer.parseInt(v[i]); } catch (NumberFormatException ignored) {}
            }
        }
        return g;
    }

    static void save(SharedPreferences p, int[] g) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < g.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(g[i]);
        }
        p.edit().putString(Prefs.HEAT, sb.toString()).apply();
    }

    /** fx, fy: position as screen fractions. */
    static void add(int[] g, float fx, float fy) {
        int c = Math.max(0, Math.min(COLS - 1, (int) (fx * COLS)));
        int r = Math.max(0, Math.min(ROWS - 1, (int) (fy * ROWS)));
        g[r * COLS + c]++;
    }

    static int max(int[] g) {
        int m = 0;
        for (int v : g) m = Math.max(m, v);
        return m;
    }

    /**
     * Turns hot cells (>= frac of the hottest, and at least 2 hits) into rectangles:
     * horizontal runs per row, merged downward when a run repeats the same span.
     */
    static List<RectF> hotZones(int[] g, float frac) {
        int threshold = Math.max(2, (int) Math.ceil(max(g) * frac));
        ArrayList<int[]> open = new ArrayList<>();   // {c0, c1, r0, r1}
        ArrayList<int[]> done = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            ArrayList<int[]> next = new ArrayList<>();
            int c = 0;
            while (c < COLS) {
                if (g[r * COLS + c] < threshold) { c++; continue; }
                int c0 = c;
                while (c < COLS && g[r * COLS + c] >= threshold) c++;
                int[] run = null;
                for (int[] o : open) if (o[0] == c0 && o[1] == c - 1) run = o;
                if (run != null) { open.remove(run); run[3] = r; next.add(run); }
                else next.add(new int[]{c0, c - 1, r, r});
            }
            done.addAll(open);
            open = next;
        }
        done.addAll(open);

        ArrayList<RectF> out = new ArrayList<>();
        float pw = 0.5f / COLS, ph = 0.5f / ROWS;    // half-cell margin
        for (int[] z : done) {
            out.add(new RectF(
                    Math.max(0, (float) z[0] / COLS - pw), Math.max(0, (float) z[2] / ROWS - ph),
                    Math.min(1, (float) (z[1] + 1) / COLS + pw), Math.min(1, (float) (z[3] + 1) / ROWS + ph)));
        }
        return out;
    }

    private Heat() {}
}

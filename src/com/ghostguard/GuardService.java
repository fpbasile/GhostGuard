package com.ghostguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Pose une vitre invisible (overlay d'accessibilité) sur la zone protégée.
 * Chaque geste qui la touche est enregistré, analysé, puis :
 *  - rejoué tel quel via dispatchGesture() s'il a l'air humain,
 *  - jeté s'il ressemble à une touche fantôme (rafale, trop bref, téléportation).
 */
public class GuardService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences prefs;
    private final ArrayList<Pane> panes = new ArrayList<>();
    private float density;

    // Détection de rafales : instants des derniers appuis.
    private final ArrayDeque<Long> topDowns = new ArrayDeque<>();
    private final ArrayDeque<Long> allDowns = new ArrayDeque<>();
    private long burstUntil;

    private int pendingBlocked, pendingPassed;

    @Override
    protected void onServiceConnected() {
        wm = getSystemService(WindowManager.class);
        prefs = Prefs.get(this);
        density = getResources().getDisplayMetrics().density;
        prefs.registerOnSharedPreferenceChangeListener(this);
        applyPrefs();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        removeShields();
        flushStats();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (Prefs.BLOCKED.equals(key) || Prefs.PASSED.equals(key)) return;
        applyPrefs();
    }

    private void applyPrefs() {
        removeShields();
        // Pendant l'édition des zones, on s'efface pour laisser dessiner.
        if (!prefs.getBoolean(Prefs.ENABLED, true) || prefs.getBoolean(Prefs.EDITING, false)) return;

        boolean full = prefs.getBoolean(Prefs.FULL, false);
        addPane(new Shield(), 0, 0, WindowManager.LayoutParams.MATCH_PARENT,
                full ? WindowManager.LayoutParams.MATCH_PARENT : zonePx());

        Rect screen = wm.getMaximumWindowMetrics().getBounds();
        for (RectF z : Zones.load(prefs)) {
            int l = Math.round(z.left * screen.width()), t = Math.round(z.top * screen.height());
            int r = Math.round(z.right * screen.width()), b = Math.round(z.bottom * screen.height());
            addPane(new Blocker(), l, t, Math.max(1, r - l), Math.max(1, b - t));
        }
    }

    private void addPane(Pane v, int x, int y, int w, int h) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = x;
        lp.y = y;
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        wm.addView(v, lp);
        panes.add(v);
    }

    private void removeShields() {
        for (Pane p : panes) {
            try { wm.removeView(p); } catch (Exception ignored) {}
        }
        panes.clear();
    }

    private int zonePx() {
        return Math.round(prefs.getInt(Prefs.ZONE_DP, 120) * density);
    }

    /** Rend toutes les vitres traversables le temps de rejouer un geste. */
    private void setTouchable(boolean touchable) {
        for (Pane p : panes) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) p.getLayoutParams();
            if (touchable) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(p, lp); } catch (Exception ignored) {}
        }
    }

    // --- Analyse ---------------------------------------------------------

    /** Enregistre un appui et dit si on est en pleine rafale de fantômes. */
    private boolean noteDownAndCheckBurst(long now, boolean inTop) {
        int level = prefs.getInt(Prefs.LEVEL, 3);
        long window = 700 + level * 100;               // 0.8 s .. 1.2 s
        int topLimit = Math.max(2, 5 - level / 2);     // 4 .. 3 appuis
        int allLimit = topLimit + 3;

        ArrayDeque<Long> q = inTop ? topDowns : allDowns;
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > window) q.removeFirst();

        if (q.size() >= (inTop ? topLimit : allLimit)) {
            burstUntil = now + 800 + level * 300;      // pause de sécurité
        }
        return now < burstUntil;
    }

    private void reportBlocked(Pane p) {
        pendingBlocked++;
        p.flash();
        scheduleFlush();
    }

    private void reportPassed() {
        pendingPassed++;
        scheduleFlush();
    }

    private final Runnable flushRunnable = this::flushStats;

    private void scheduleFlush() {
        handler.removeCallbacks(flushRunnable);
        handler.postDelayed(flushRunnable, 1500);
    }

    private void flushStats() {
        if (prefs == null || (pendingBlocked == 0 && pendingPassed == 0)) return;
        prefs.edit()
                .putLong(Prefs.BLOCKED, prefs.getLong(Prefs.BLOCKED, 0) + pendingBlocked)
                .putLong(Prefs.PASSED, prefs.getLong(Prefs.PASSED, 0) + pendingPassed)
                .apply();
        pendingBlocked = 0;
        pendingPassed = 0;
    }

    // --- Rejeu -----------------------------------------------------------

    private void replay(ArrayList<float[]> pts, long durationMs, boolean fromTop) {
        float[] a = pts.get(0);
        float[] b = pts.get(pts.size() - 1);
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float moved = (float) Math.hypot(dx, dy);

        // Glissé vers le bas depuis le tout haut : ouvrir le volet directement,
        // c'est plus fiable qu'un geste rejoué en retard.
        if (fromTop && dy > 80 * density && Math.abs(dy) > Math.abs(dx) * 1.5f
                && a[1] < 60 * density) {
            performGlobalAction(dy > 300 * density
                    ? GLOBAL_ACTION_QUICK_SETTINGS : GLOBAL_ACTION_NOTIFICATIONS);
            return;
        }

        Path path = new Path();
        path.moveTo(Math.max(0, a[0]), Math.max(0, a[1]));
        if (moved > 8 * density) {
            int step = Math.max(1, pts.size() / 40);
            for (int i = step; i < pts.size(); i += step) {
                float[] p = pts.get(i);
                path.lineTo(Math.max(0, p[0]), Math.max(0, p[1]));
            }
            path.lineTo(Math.max(0, b[0]), Math.max(0, b[1]));
        }
        long dur = Math.max(1, Math.min(durationMs, GestureDescription.getMaxGestureDuration()));

        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, dur))
                .build();

        setTouchable(false);
        GestureResultCallback cb = new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) { restore(); }
            @Override public void onCancelled(GestureDescription d) { restore(); }
        };
        if (!dispatchGesture(g, cb, handler)) restore();
    }

    private void restore() {
        handler.postDelayed(() -> setTouchable(true), 40);
    }

    // --- La vitre --------------------------------------------------------

    /** Vitre de base : teinte verte (optionnelle) et flash rouge au blocage. */
    private abstract class Pane extends View {
        private long flashUntil;

        Pane() { super(GuardService.this); updateTint(); }

        void flash() {
            if (!prefs.getBoolean(Prefs.FLASH, true)) return;
            flashUntil = SystemClock.uptimeMillis() + 250;
            updateTint();
            postDelayed(this::updateTint, 260);
        }

        boolean showGreen() { return prefs.getBoolean(Prefs.SHOW, false); }

        void updateTint() {
            if (SystemClock.uptimeMillis() < flashUntil) {
                setBackgroundColor(Color.argb(70, 255, 40, 40));
            } else if (showGreen()) {
                setBackgroundColor(Color.argb(28, 76, 175, 80));
            } else {
                setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    /** Zone personnalisée : bloque tout, sans analyse. */
    private final class Blocker extends Pane {
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
                reportBlocked(this);
            }
            return true;
        }
    }

    private final class Shield extends Pane {
        private final ArrayList<float[]> pts = new ArrayList<>();
        private int trackedId = -1;
        private long downTime;
        private boolean suspect;
        private boolean startInTop;

        @Override
        boolean showGreen() {
            return super.showGreen() && !prefs.getBoolean(Prefs.FULL, false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            long now = SystemClock.uptimeMillis();
            int level = prefs.getInt(Prefs.LEVEL, 3);
            int zone = zonePx();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    pts.clear();
                    trackedId = e.getPointerId(0);
                    downTime = e.getEventTime();
                    float x = e.getRawX(), y = e.getRawY();
                    startInTop = y < zone;
                    suspect = noteDownAndCheckBurst(now, startInTop);
                    pts.add(new float[]{x, y, 0});
                    return true;
                }
                case MotionEvent.ACTION_POINTER_DOWN: {
                    // Un 2e doigt qui apparaît dans la zone du haut : presque sûrement un fantôme.
                    int i = e.getActionIndex();
                    float y = e.getY(i) + (e.getRawY() - e.getY());
                    if (y < zone) noteDownAndCheckBurst(now, true);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    int i = e.findPointerIndex(trackedId);
                    if (i < 0 || pts.isEmpty()) return true;
                    float offX = e.getRawX() - e.getX(), offY = e.getRawY() - e.getY();
                    float x = e.getX(i) + offX, y = e.getY(i) + offY;
                    float[] last = pts.get(pts.size() - 1);
                    long t = e.getEventTime() - downTime;
                    float jump = (float) Math.hypot(x - last[0], y - last[1]);
                    long dt = Math.max(1, t - (long) last[2]);
                    // Un doigt ne parcourt pas 1/3 d'écran en une seule frame.
                    if (jump > (260 - level * 25) * density && dt < 40) suspect = true;
                    pts.add(new float[]{x, y, t});
                    return true;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    if (e.getPointerId(e.getActionIndex()) != trackedId) return true;
                    finish(e.getEventTime() - downTime, level);
                    trackedId = -1;
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (trackedId == -1) return true;
                    finish(e.getEventTime() - downTime, level);
                    trackedId = -1;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    pts.clear();
                    trackedId = -1;
                    return true;
            }
            return true;
        }

        private void finish(long duration, int level) {
            if (pts.isEmpty()) return;
            // Les fantômes sont souvent des contacts ultra-brefs.
            long minMs = startInTop ? 20 + level * 10 : 10 + level * 5;
            if (SystemClock.uptimeMillis() < burstUntil && startInTop) suspect = true;
            if (suspect || duration < minMs) {
                reportBlocked(this);
            } else {
                reportPassed();
                replay(new ArrayList<>(pts), duration, startInTop);
            }
            pts.clear();
        }
    }
}

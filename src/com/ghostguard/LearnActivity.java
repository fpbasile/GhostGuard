package com.ghostguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 3-minute stress test: cycles through screen and vibration patterns known to trigger
 * ghost touches on damaged digitizers, records every touch, then suggests settings.
 */
public class LearnActivity extends Activity {
    private static final long PHASE_MS = 15_000;

    // What the user is asked to do during a phase.
    private static final int NONE = 0, TAP = 1, HOLD = 2, SWIPE = 3, MOVING = 4;

    /** One stress pattern. Flags combine. */
    private static final class Phase {
        final String name, hint;
        final int bg, task;      // base color, user task
        final boolean colors, strobe, sweep, brightPulse, vibrate, heavy;
        final float brightness;  // -1 = system
        Phase(String name, String hint, int task, int bg, float brightness, boolean colors,
              boolean strobe, boolean sweep, boolean brightPulse, boolean vibrate, boolean heavy) {
            this.name = name; this.hint = hint; this.task = task; this.bg = bg;
            this.brightness = brightness; this.colors = colors; this.strobe = strobe;
            this.sweep = sweep; this.brightPulse = brightPulse; this.vibrate = vibrate; this.heavy = heavy;
        }
    }

    private static final String HANDS_OFF = "Don't touch the screen";
    private static final Phase[] PHASES = {
            // Hands off: the screen and motor alone.
            new Phase("Calm", HANDS_OFF, NONE, Color.BLACK, -1, false, false, false, false, false, false),
            new Phase("Color flashes", HANDS_OFF, NONE, Color.BLACK, -1, true, false, false, false, false, false),
            new Phase("Strobe + vibration", HANDS_OFF, NONE, Color.BLACK, -1, false, true, false, false, true, false),
            new Phase("Brightness pulse", HANDS_OFF, NONE, Color.WHITE, -1, false, false, true, true, false, false),
            new Phase("In your hand", "Hold the phone normally · don't touch the screen", NONE,
                    Color.BLACK, -1, false, false, false, false, false, false),
            // Your touches: ghosts that your own finger triggers elsewhere.
            new Phase("Tapping", "Tap the green circle again and again", TAP,
                    Color.BLACK, -1, false, false, false, false, false, false),
            new Phase("Holding", "Keep your finger on the green circle", HOLD,
                    Color.BLACK, -1, false, false, false, false, false, false),
            new Phase("Swiping", "Swipe back and forth inside the green box", SWIPE,
                    Color.BLACK, -1, false, false, false, false, false, false),
            new Phase("Chasing", "Tap the green circle wherever it goes", MOVING,
                    Color.BLACK, -1, false, false, false, false, false, false),
            new Phase("Tapping + flashes", "Tap the green circle again and again", TAP,
                    Color.BLACK, -1, true, false, false, false, false, false),
            new Phase("Holding + vibration", "Keep your finger on the green circle", HOLD,
                    Color.BLACK, -1, false, false, false, false, true, true),
            new Phase("Everything at once", "Tap the green circle wherever it goes", MOVING,
                    Color.BLACK, -1, true, false, true, true, true, false),
    };

    /** A recorded touch. Positions are screen fractions. */
    private static final class Touch {
        float x, y, moved;
        long down, duration;
        boolean real;            // landed on the target during a touch phase
    }

    // Current target in pixels (circle bounds or swipe box), and the previous one for MOVING.
    private final RectF target = new RectF(), prevTarget = new RectF();
    private long targetMovedAt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private final ArrayList<Touch> touches = new ArrayList<>();
    private final SparseArray<Touch> active = new SparseArray<>();
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private Stage stage;
    private long startedAt;
    private int phase = -1;
    private boolean running, finished;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
        new AlertDialog.Builder(this)
                .setTitle("Learning mode · 3 min")
                .setMessage("Follow the instructions on screen: first hands off, then hold "
                        + "the phone, then tap, hold and swipe on a green target.\n\n"
                        + "Any touch that lands outside the target is a ghost touch. "
                        + "The screen will flash and the phone will vibrate.\n\n"
                        + "⚠️ Contains rapid flashing lights. Don't look at it if you are "
                        + "sensitive to flashing.\n\nUse the back button to stop at any time.")
                .setPositiveButton("Start", (d, w) -> begin())
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void begin() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        stage = new Stage(this);
        setContentView(stage);
        // The service steps aside so the raw touches reach us.
        prefs.edit().putBoolean(Prefs.EDITING, true).apply();
        running = true;
        startedAt = SystemClock.uptimeMillis();
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (stage.getWidth() == 0) { handler.postDelayed(this, 40); return; }   // not laid out yet
            long t = SystemClock.uptimeMillis() - startedAt;
            int p = (int) (t / PHASE_MS);
            if (p >= PHASES.length) { done(); return; }
            if (p != phase) enterPhase(p);
            if (PHASES[p].task == MOVING && SystemClock.uptimeMillis() - targetMovedAt > 1500) {
                placeTarget(MOVING);
            }
            stage.frame(t);
            handler.postDelayed(this, 40);
        }
    };

    private void enterPhase(int p) {
        phase = p;
        Phase ph = PHASES[p];
        vibrator.cancel();
        if (ph.vibrate) {
            long[] timings = ph.heavy ? new long[]{0, 400, 60} : new long[]{0, 40, 30, 40, 30, 90, 70};
            int[] amps = ph.heavy ? new int[]{0, 255, 0} : new int[]{0, 255, 0, 200, 0, 255, 0};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, 0));
        }
        setBrightness(ph.brightness);
        placeTarget(ph.task);
        if (p > 0) {
            // Short buzz so a hands-off user notices the switch to a touch phase.
            if (!ph.vibrate && PHASES[p - 1].task != ph.task) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, 255));
            }
        }
    }

    /** Targets live in the lower part of the screen, away from the broken top. */
    private void placeTarget(int task) {
        float w = stage.getWidth(), h = stage.getHeight();
        float d = getResources().getDisplayMetrics().density, r = 48 * d;
        prevTarget.set(target);
        targetMovedAt = SystemClock.uptimeMillis();
        if (task == SWIPE) {
            target.set(w * 0.1f, h * 0.68f, w * 0.9f, h * 0.84f);
        } else if (task == MOVING) {
            float cx = r + rnd.nextFloat() * (w - 2 * r);
            float cy = h * 0.62f + rnd.nextFloat() * (h * 0.26f);
            target.set(cx - r, cy - r, cx + r, cy + r);
        } else if (task != NONE) {
            target.set(w / 2 - r, h * 0.76f - r, w / 2 + r, h * 0.76f + r);
        } else {
            target.setEmpty();
        }
    }

    /** A finger-down counts as real if it lands on (or near) the current target. */
    private boolean onTarget(float x, float y) {
        if (PHASES[phase].task == NONE) return false;
        float m = 28 * getResources().getDisplayMetrics().density;
        if (x >= target.left - m && x <= target.right + m && y >= target.top - m && y <= target.bottom + m) return true;
        // Grace period right after a moving target jumps.
        return SystemClock.uptimeMillis() - targetMovedAt < 400
                && x >= prevTarget.left - m && x <= prevTarget.right + m
                && y >= prevTarget.top - m && y <= prevTarget.bottom + m;
    }

    private void setBrightness(float v) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (lp.screenBrightness == v) return;
        lp.screenBrightness = v < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : v;
        getWindow().setAttributes(lp);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (!running) return super.dispatchTouchEvent(e);
        float w = stage.getWidth(), h = stage.getHeight();
        int i = e.getActionIndex();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                Touch t = new Touch();
                t.x = e.getX(i) / w;
                t.y = e.getY(i) / h;
                t.down = e.getEventTime();
                t.real = onTarget(e.getX(i), e.getY(i));
                active.put(e.getPointerId(i), t);
                touches.add(t);
                if (!t.real) stage.dot(e.getX(i), e.getY(i));
                // Hit a moving target: send it somewhere else.
                else if (PHASES[phase].task == MOVING) placeTarget(MOVING);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < e.getPointerCount(); k++) {
                    Touch t = active.get(e.getPointerId(k));
                    if (t == null) continue;
                    float d = (float) Math.hypot(e.getX(k) / w - t.x, e.getY(k) / h - t.y);
                    t.moved = Math.max(t.moved, d);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Touch t = active.get(e.getPointerId(i));
                if (t != null) t.duration = e.getEventTime() - t.down;
                active.remove(e.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                active.clear();
                break;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running) stop();
        if (!finished) finish();             // leaving early cancels the run
    }

    private void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        vibrator.cancel();
        setBrightness(-1);
        prefs.edit().putBoolean(Prefs.EDITING, false).apply();
    }

    // --- Results ---------------------------------------------------------

    private void done() {
        stop();
        finished = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) c.show(WindowInsets.Type.systemBars());
        getWindow().setDecorFitsSystemWindows(true);

        // Every ghost also goes into the long-term heatmap.
        int[] all = Heat.load(prefs);
        int[] grid = new int[Heat.COLS * Heat.ROWS];
        for (Touch t : touches) {
            if (t.real) continue;
            Heat.add(all, t.x, t.y);
            Heat.add(grid, t.x, t.y);
        }
        Heat.save(prefs, all);
        showResults(grid);
    }

    private void showResults(int[] grid) {
        // Share of ghosts that came within 1 s after one of your real touches.
        ArrayList<Touch> ghosts = new ArrayList<>();
        int afterYou = 0;
        long lastReal = Long.MIN_VALUE / 2;
        for (Touch t : touches) {
            if (t.real) { lastReal = t.down; continue; }
            ghosts.add(t);
            if (t.down - lastReal < 1000) afterYou++;
        }
        touches.clear();
        touches.addAll(ghosts);
        float afterShare = ghosts.isEmpty() ? 0 : (float) afterYou / ghosts.size();

        Ui ui = new Ui(this);
        LinearLayout root = ui.page();
        root.addView(ui.title("Results", touches.size() + " ghost touches in 3 minutes", false));

        if (touches.isEmpty()) {
            LinearLayout c = ui.card(null);
            c.addView(ui.label("No ghost touches detected 🎉", 18, ui.onSurface, true));
            TextView t = ui.label("Your screen behaved during the whole test. Keep your current "
                    + "settings, or run it again later if the problem comes back.", 14, ui.muted, false);
            t.setPadding(0, ui.dp(6), 0, 0);
            c.addView(t);
            root.addView(c);
            root.addView(ui.button("Done", true, v -> finish()));
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float screenH = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds().height();

        // Where: share of ghosts in the top 40% of the screen.
        ArrayList<Float> topYs = new ArrayList<>();
        for (Touch t : touches) if (t.y < 0.4f) topYs.add(t.y);
        float topShare = (float) topYs.size() / touches.size();
        int zoneDp = prefs.getInt(Prefs.ZONE_DP, 120);
        if (topShare >= 0.7f) {
            Collections.sort(topYs);
            float y95 = topYs.get(Math.min(topYs.size() - 1, (int) (topYs.size() * 0.95f)));
            zoneDp = Math.max(40, Math.min(400, Math.round(y95 * screenH / density) + 24));
        }

        // How: durations decide the minimum-length rule; bursts need strictness >= 3.
        ArrayList<Long> durs = new ArrayList<>();
        for (Touch t : touches) durs.add(t.duration);
        Collections.sort(durs);
        long p80 = durs.get(Math.min(durs.size() - 1, (int) (durs.size() * 0.8f)));
        long median = durs.get(durs.size() / 2);
        int level = 5;
        for (int l = 1; l <= 5; l++) if (20 + l * 10 > p80) { level = l; break; }

        ArrayList<Long> gaps = new ArrayList<>();
        for (int i = 1; i < touches.size(); i++) gaps.add(touches.get(i).down - touches.get(i - 1).down);
        Collections.sort(gaps);
        boolean bursty = gaps.size() >= 3 && gaps.get(gaps.size() / 2) < 400;
        if (bursty) level = Math.max(level, 3);
        if (afterShare >= 0.5f) level = Math.max(level, 4);   // your touches set them off

        // Hotspots outside the top zone become custom zones.
        float topFrac = zoneDp * density / screenH;
        List<RectF> hot = Heat.hotZones(grid, 0.25f);
        hot.removeIf(r -> r.bottom <= topFrac);

        // Which phases triggered the most.
        int[] perPhase = new int[PHASES.length];
        for (Touch t : touches) {
            int p = (int) ((t.down - startedAt) / PHASE_MS);
            if (p >= 0 && p < PHASES.length) perPhase[p]++;
        }
        int worst = 0;
        for (int p = 1; p < PHASES.length; p++) if (perPhase[p] > perPhase[worst]) worst = p;

        LinearLayout map = ui.card("Where they landed");
        HeatView hv = new HeatView(this, ui.muted, ui.bg);
        hv.grid = grid;
        hv.zones = hot;
        hv.topFrac = topFrac;
        map.addView(hv);
        root.addView(map);

        LinearLayout facts = ui.card("What we found");
        facts.addView(fact(ui, "In the top of the screen", Math.round(topShare * 100) + "%"));
        facts.addView(fact(ui, "Typical ghost length", median + " ms"));
        facts.addView(fact(ui, "Come in bursts", bursty ? "Yes" : "No"));
        facts.addView(fact(ui, "Right after your touches", Math.round(afterShare * 100) + "%"));
        facts.addView(fact(ui, "Worst trigger", PHASES[worst].name));
        root.addView(facts);

        LinearLayout sug = ui.card("Suggested settings");
        sug.addView(fact(ui, "Top zone height", zoneDp + " dp"));
        sug.addView(fact(ui, "Strictness", level + " / 5"));
        sug.addView(fact(ui, "New custom zones", String.valueOf(hot.size())));
        root.addView(sug);

        final int fZone = zoneDp, fLevel = level;
        root.addView(ui.button("Apply suggestions", true, v -> {
            List<RectF> zones = Zones.load(prefs);
            zones.addAll(hot);
            Zones.save(prefs, zones);
            prefs.edit().putInt(Prefs.ZONE_DP, fZone).putInt(Prefs.LEVEL, fLevel).apply();
            MainActivity.needsRebuild = true;
            finish();
        }));
        root.addView(ui.button("Keep my settings", false, v -> finish()));
        TextView note = ui.label("Ghost touches were added to the heatmap either way.",
                13, ui.muted, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, ui.dp(12), 0, 0);
        root.addView(note);
    }

    private int ghostCount() {
        int n = 0;
        for (Touch t : touches) if (!t.real) n++;
        return n;
    }

    private View fact(Ui ui, String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(ui.label(k, 15, ui.onSurface, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(ui.label(v, 15, ui.accent, true));
        return row;
    }

    // --- The stage -------------------------------------------------------

    private final class Stage extends View {
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG),
                bar = new Paint(), pill = new Paint(Paint.ANTI_ALIAS_FLAG),
                targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<float[]> dots = new ArrayList<>();   // x, y, time
        private int bg = Color.BLACK;
        private long t;

        Stage(Context c) {
            super(c);
            float d = getResources().getDisplayMetrics().density;
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(16 * d);
            dotPaint.setColor(0xFFFF3B30);
            pill.setColor(0xB0000000);
            targetPaint.setStrokeWidth(3 * d);
        }

        void dot(float x, float y) {
            dots.add(new float[]{x, y, SystemClock.uptimeMillis()});
        }

        void frame(long now) {
            t = now;
            Phase ph = PHASES[phase];
            long local = now % PHASE_MS;
            bg = ph.bg;
            if (ph.colors) bg = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
            if (ph.strobe) bg = (local / 50) % 2 == 0 ? Color.WHITE : Color.BLACK;
            if (ph.brightPulse) setBrightness((local / 300) % 2 == 0 ? 1f : 0.01f);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            c.drawColor(bg);
            Phase ph = PHASES[Math.max(0, phase)];
            if (ph.sweep) {
                float w = getWidth(), off = (t / 4f) % (120 * d);
                bar.setColor(Color.WHITE);
                for (float x = -120 * d + off; x < w; x += 120 * d) c.drawRect(x, 0, x + 50 * d, getHeight(), bar);
            }

            long now = SystemClock.uptimeMillis();
            dots.removeIf(p -> now - p[2] > 3000);
            for (float[] p : dots) {
                dotPaint.setAlpha((int) (255 * (1 - (now - p[2]) / 3000f)));
                c.drawCircle(p[0], p[1], 14 * d, dotPaint);
            }

            if (ph.task != NONE && !target.isEmpty()) {
                targetPaint.setStyle(Paint.Style.FILL);
                targetPaint.setColor(0x6634A853);
                if (ph.task == SWIPE) c.drawRoundRect(target, 24 * d, 24 * d, targetPaint);
                else c.drawOval(target, targetPaint);
                targetPaint.setStyle(Paint.Style.STROKE);
                targetPaint.setColor(0xFF34A853);
                if (ph.task == SWIPE) c.drawRoundRect(target, 24 * d, 24 * d, targetPaint);
                else c.drawOval(target, targetPaint);
            }

            long left = Math.max(0, PHASES.length * PHASE_MS - t) / 1000;
            String[] lines = {
                    ph.hint,
                    String.format(Locale.US, "%d:%02d left · phase %d/%d", left / 60, left % 60,
                            phase + 1, PHASES.length),
                    ph.name,
                    ghostCount() + " ghost touches"};
            float cy = getHeight() * 0.42f, lh = 30 * d;
            float half = Math.min(getWidth() / 2f - 12 * d, 180 * d);
            RectF box = new RectF(getWidth() / 2f - half, cy - lh * 2 - 8 * d,
                    getWidth() / 2f + half, cy + lh * 2 + 2 * d);
            c.drawRoundRect(box, 20 * d, 20 * d, pill);
            for (int i = 0; i < lines.length; i++) {
                text.setFakeBoldText(i == 0 || i == 3);
                c.drawText(lines[i], getWidth() / 2f, cy - lh * 1.5f + i * lh + 6 * d, text);
            }
        }
    }
}

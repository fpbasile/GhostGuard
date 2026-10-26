package com.ghostguard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Full-screen editor: drag to draw a block zone, tap a zone to delete it.
 * The service removes its overlays while this is open so drawing works everywhere.
 */
public class ZoneEditorActivity extends Activity {
    private SharedPreferences prefs;
    private Canvas_ canvas;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Prefs.get(this);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setDecorFitsSystemWindows(false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(235, 18, 18, 18));
        canvas = new Canvas_(this);
        canvas.zones.addAll(Zones.load(prefs));
        root.addView(canvas);

        TextView hint = new TextView(this);
        hint.setText("Drag to draw a block zone · Tap a zone to remove it");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        root.addView(hint, hp);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(16), dp(16), dp(16), dp(40));
        bar.addView(button("Clear", 0xFF5F6368, v -> { canvas.zones.clear(); canvas.invalidate(); }));
        bar.addView(button("Cancel", 0xFF5F6368, v -> finish()));
        bar.addView(button("Save", 0xFF1E8E3E, v -> { Zones.save(prefs, canvas.zones); finish(); }));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefs.edit().putBoolean(Prefs.EDITING, true).apply();
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected void onPause() {
        prefs.edit().putBoolean(Prefs.EDITING, false).apply();
        super.onPause();
    }

    private Button button(String text, int color, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(24));
        btn.setBackground(g);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Draws zones in screen fractions so they match the service overlays. */
    private static final class Canvas_ extends View {
        final ArrayList<RectF> zones = new ArrayList<>();
        private final Paint fill = new Paint(), stroke = new Paint();
        private float sx, sy;
        private RectF drawing;

        Canvas_(Context c) {
            super(c);
            fill.setColor(Color.argb(110, 234, 67, 53));
            stroke.setColor(Color.rgb(242, 139, 130));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            for (RectF z : zones) draw(c, new RectF(z.left * w, z.top * h, z.right * w, z.bottom * h));
            if (drawing != null) draw(c, drawing);
        }

        private void draw(Canvas c, RectF r) {
            c.drawRect(r, fill);
            c.drawRect(r, stroke);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY(), w = getWidth(), h = getHeight();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    sx = x; sy = y;
                    drawing = new RectF(x, y, x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (drawing == null) break;
                    drawing.set(Math.min(sx, x), Math.min(sy, y), Math.max(sx, x), Math.max(sy, y));
                    break;
                case MotionEvent.ACTION_UP:
                    if (drawing == null) break;
                    float min = 24 * getResources().getDisplayMetrics().density;
                    if (drawing.width() < min && drawing.height() < min) {
                        // A tap: remove the topmost zone under the finger.
                        for (int i = zones.size() - 1; i >= 0; i--) {
                            RectF z = zones.get(i);
                            if (x >= z.left * w && x <= z.right * w && y >= z.top * h && y <= z.bottom * h) {
                                zones.remove(i);
                                break;
                            }
                        }
                    } else {
                        zones.add(new RectF(drawing.left / w, drawing.top / h,
                                drawing.right / w, drawing.bottom / h));
                    }
                    drawing = null;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    drawing = null;
                    break;
            }
            invalidate();
            return true;
        }
    }
}

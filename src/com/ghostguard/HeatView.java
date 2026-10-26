package com.ghostguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/** Phone-shaped preview of the heatmap, with custom zones and the top zone overlaid. */
final class HeatView extends View {
    int[] grid = new int[Heat.COLS * Heat.ROWS];
    List<RectF> zones = new ArrayList<>();
    float topFrac;                       // top zone height as a screen fraction
    private final float ratio;           // screen height / width
    private final Paint cell = new Paint(), frame = new Paint(), zone = new Paint(), top = new Paint();
    private final int frameColor;

    HeatView(Context c, int frameColor, int bgColor) {
        super(c);
        this.frameColor = frameColor;
        android.graphics.Rect b = c.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics().getBounds();
        ratio = (float) b.height() / b.width();
        frame.setColor(bgColor);
        zone.setStyle(Paint.Style.STROKE);
        zone.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        zone.setColor(0xFFF28B82);
        top.setStyle(Paint.Style.STROKE);
        top.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        top.setColor(0xFF81C995);
        top.setPathEffect(new DashPathEffect(new float[]{18, 12}, 0));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = Math.round(w * 0.62f * ratio);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float pw = getHeight() / ratio, left = (getWidth() - pw) / 2;
        RectF phone = new RectF(left, 0, left + pw, getHeight());
        c.drawRoundRect(phone, 22 * d, 22 * d, frame);
        c.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(phone, 22 * d, 22 * d, android.graphics.Path.Direction.CW);
        c.clipPath(clip);

        int max = Math.max(1, Heat.max(grid));
        float cw = pw / Heat.COLS, ch = (float) getHeight() / Heat.ROWS;
        for (int r = 0; r < Heat.ROWS; r++) {
            for (int col = 0; col < Heat.COLS; col++) {
                int v = grid[r * Heat.COLS + col];
                if (v == 0) continue;
                cell.setColor(heatColor((float) v / max));
                c.drawRect(left + col * cw, r * ch, left + (col + 1) * cw, (r + 1) * ch, cell);
            }
        }
        if (topFrac > 0) {
            float y = topFrac * getHeight();
            c.drawLine(left, y, left + pw, y, top);
        }
        for (RectF z : zones) {
            c.drawRoundRect(new RectF(left + z.left * pw, z.top * getHeight(),
                    left + z.right * pw, z.bottom * getHeight()), 6 * d, 6 * d, zone);
        }
        c.restore();
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2 * d);
        int fill = frame.getColor();
        frame.setColor(frameColor);
        c.drawRoundRect(phone, 22 * d, 22 * d, frame);
        frame.setStyle(Paint.Style.FILL);
        frame.setColor(fill);
    }

    /** 0..1 -> translucent yellow to solid red. */
    static int heatColor(float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (90 + 165 * t);
        int g = (int) (210 * (1 - t) + 40 * t);
        return Color.argb(a, 255, g, 40);
    }
}

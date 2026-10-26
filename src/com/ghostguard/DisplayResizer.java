package com.ghostguard;

import android.content.Context;
import android.view.Display;

/**
 * Shrinks the logical display (like `adb shell wm size`), leaving two equal
 * black bars at the top and bottom. Needs WRITE_SECURE_SETTINGS, granted once over adb.
 */
final class DisplayResizer {

    /** barPx <= 0 restores the full screen. Returns false if not permitted. */
    static boolean apply(Context c, int barPx) {
        try {
            Object wms = Class.forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService").invoke(null);
            if (barPx <= 0) {
                wms.getClass().getMethod("clearForcedDisplaySize", int.class)
                        .invoke(wms, Display.DEFAULT_DISPLAY);
            } else {
                Display.Mode m = c.getDisplay().getMode();
                int w = Math.min(m.getPhysicalWidth(), m.getPhysicalHeight());
                int h = Math.max(m.getPhysicalWidth(), m.getPhysicalHeight());
                wms.getClass().getMethod("setForcedDisplaySize", int.class, int.class, int.class)
                        .invoke(wms, Display.DEFAULT_DISPLAY, w, h - 2 * barPx);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private DisplayResizer() {}
}

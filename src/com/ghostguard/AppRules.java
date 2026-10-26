package com.ghostguard;

import android.content.SharedPreferences;

/** Per-app filter mode, stored as "app:<package>" -> off / default / strict. */
final class AppRules {
    static final String OFF = "off", DEFAULT = "default", STRICT = "strict";

    static String get(SharedPreferences p, String pkg) {
        return p.getString(Prefs.APP_PREFIX + pkg, DEFAULT);
    }

    static void set(SharedPreferences p, String pkg, String mode) {
        if (DEFAULT.equals(mode)) p.edit().remove(Prefs.APP_PREFIX + pkg).apply();
        else p.edit().putString(Prefs.APP_PREFIX + pkg, mode).apply();
    }

    static String next(String mode) {
        return DEFAULT.equals(mode) ? STRICT : STRICT.equals(mode) ? OFF : DEFAULT;
    }

    private AppRules() {}
}

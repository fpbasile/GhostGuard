package com.ghostguard;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NAME = "ghostguard";
    static final String ENABLED = "enabled";        // filtre actif
    static final String ZONE_DP = "zone_dp";        // hauteur de la zone du haut
    static final String FULL = "full_screen";       // filtrer tout l'écran
    static final String LEVEL = "level";            // sévérité 1..5
    static final String SHOW = "show_zone";         // teinter la zone
    static final String SHRINK = "shrink";          // réduire l'écran (bandes noires)
    static final String BAR_DP = "bar_dp";          // hauteur de chaque bande
    static final String BLOCKED = "stat_blocked";
    static final String PASSED = "stat_passed";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private Prefs() {}
}

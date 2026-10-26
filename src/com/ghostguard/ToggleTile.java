package com.ghostguard;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile to pause the filter. */
public class ToggleTile extends TileService {

    @Override
    public void onStartListening() { refresh(); }

    @Override
    public void onClick() {
        SharedPreferences p = Prefs.get(this);
        p.edit().putBoolean(Prefs.ENABLED, !p.getBoolean(Prefs.ENABLED, true)).apply();
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = Prefs.get(this).getBoolean(Prefs.ENABLED, true);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setSubtitle(on ? "On" : "Paused");
        t.updateTile();
    }
}

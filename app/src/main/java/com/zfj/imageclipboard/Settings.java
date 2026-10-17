package com.zfj.imageclipboard;

import android.content.Context;
import android.content.SharedPreferences;

final class Settings {
    final SharedPreferences prefs;
    Settings(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    boolean capturesOnly() { return prefs.getBoolean("capturesOnly", false); }
    int minSide() { return prefs.getInt("minSide", 240); }
    boolean compatibleFormat() { return prefs.getBoolean("compatibleFormat", true); }
    String includes() { return prefs.getString("includes", ""); }
    String excludes() { return prefs.getString("excludes", "/.thumbnails/\n/thumbnails/\n/ads/\n/cache/ad/"); }
    String folders() { return prefs.getString("folders", ""); }
    long folderInterval() { return prefs.getLong("folderInterval", 30000); }
    void setEnabled(boolean enabled) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("enabled", enabled);
        if (enabled && !enabled()) e.putLong("since", System.currentTimeMillis());
        e.apply();
    }
    long since() { return prefs.getLong("since", System.currentTimeMillis()); }
}

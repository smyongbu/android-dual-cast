package com.example.androiddualcast.receiver;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProjectionSettings {
    private static final String PREFS = "projection_settings";

    public final String phoneIp;
    public final int width;
    public final int height;
    public final int bitrateMbps;
    public final int fps;
    public final int densityDpi;
    public final boolean mirrorMode;
    public final boolean navigationBar;

    private ProjectionSettings(String phoneIp, int width, int height, int bitrateMbps,
            int fps, int densityDpi, boolean mirrorMode, boolean navigationBar) {
        this.phoneIp = phoneIp;
        this.width = width;
        this.height = height;
        this.bitrateMbps = bitrateMbps;
        this.fps = fps;
        this.densityDpi = densityDpi;
        this.mirrorMode = mirrorMode;
        this.navigationBar = navigationBar;
    }

    public static ProjectionSettings defaults() {
        return new ProjectionSettings("", 1280, 720, 4, 25, 240, true, true);
    }

    public static ProjectionSettings load(Context context) {
        ProjectionSettings defaults = defaults();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ProjectionSettings(
                prefs.getString("phoneIp", defaults.phoneIp),
                defaults.width,
                defaults.height,
                prefs.getInt("bitrateMbps", defaults.bitrateMbps),
                prefs.getInt("fps", defaults.fps),
                prefs.getInt("densityDpi", defaults.densityDpi),
                prefs.getBoolean("mirrorMode", defaults.mirrorMode),
                prefs.getBoolean("navigationBar", defaults.navigationBar));
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("phoneIp", phoneIp)
                .putInt("bitrateMbps", bitrateMbps)
                .putInt("fps", fps)
                .putInt("densityDpi", densityDpi)
                .putBoolean("mirrorMode", mirrorMode)
                .putBoolean("navigationBar", navigationBar)
                .apply();
    }

    public ProjectionSettings withPhoneIp(String value) {
        return new ProjectionSettings(value, width, height, bitrateMbps, fps, densityDpi,
                mirrorMode, navigationBar);
    }

    public ProjectionSettings withStreamingOptions(int bitrate, int frameRate, int dpi,
            boolean mirror, boolean navBar) {
        return new ProjectionSettings(phoneIp, width, height, bitrate, frameRate, dpi,
                mirror, navBar);
    }
}

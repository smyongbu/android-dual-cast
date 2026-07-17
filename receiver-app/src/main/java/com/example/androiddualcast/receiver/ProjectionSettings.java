package com.example.androiddualcast.receiver;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProjectionSettings {
    private static final String PREFS = "projection_settings";

    public final String phoneIp;
    public final int adbPort;
    public final int width;
    public final int height;
    public final int bitrateMbps;
    public final int fps;
    public final int densityDpi;
    public final boolean mirrorMode;
    public final boolean navigationBar;
    public final int navigationPosition;
    public final int debugPort;
    public final int pairingPort;
    public final String pairingCode;
    public final int displayMode;

    private ProjectionSettings(String phoneIp, int adbPort, int width, int height, int bitrateMbps,
            int fps, int densityDpi, boolean mirrorMode, boolean navigationBar, int debugPort,
            int pairingPort, String pairingCode, int displayMode, int navigationPosition) {
        this.phoneIp = phoneIp;
        this.adbPort = adbPort;
        this.width = width;
        this.height = height;
        this.bitrateMbps = bitrateMbps;
        this.fps = fps;
        this.densityDpi = densityDpi;
        this.mirrorMode = mirrorMode;
        this.navigationBar = navigationBar;
        this.navigationPosition = navigationPosition;
        this.debugPort = debugPort;
        this.pairingPort = pairingPort;
        this.pairingCode = pairingCode;
        this.displayMode = displayMode;
    }

    public static ProjectionSettings defaults() {
        return new ProjectionSettings("192.168.1.", 5555, 1280, 720, 4, 25, 240, false, true,
                0, 0, "", 0, 0);
    }

    public static ProjectionSettings load(Context context) {
        ProjectionSettings defaults = defaults();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ProjectionSettings(
                prefs.getString("phoneIp", defaults.phoneIp),
                prefs.getInt("adbPort", defaults.adbPort),
                prefs.getInt("width", defaults.width),
                prefs.getInt("height", defaults.height),
                prefs.getInt("bitrateMbps", defaults.bitrateMbps),
                prefs.getInt("fps", defaults.fps),
                prefs.getInt("densityDpi", defaults.densityDpi),
                prefs.getBoolean("mirrorMode", defaults.mirrorMode),
                prefs.getBoolean("navigationBar", defaults.navigationBar),
                prefs.getInt("debugPort", defaults.debugPort),
                prefs.getInt("pairingPort", defaults.pairingPort),
                prefs.getString("pairingCode", defaults.pairingCode),
                prefs.getInt("displayMode", defaults.displayMode),
                prefs.getInt("navigationPosition", defaults.navigationPosition));
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("phoneIp", phoneIp)
                .putInt("adbPort", adbPort)
                .putInt("width", width)
                .putInt("height", height)
                .putInt("bitrateMbps", bitrateMbps)
                .putInt("fps", fps)
                .putInt("densityDpi", densityDpi)
                .putBoolean("mirrorMode", mirrorMode)
                .putBoolean("navigationBar", navigationBar)
                .putInt("debugPort", debugPort)
                .putInt("pairingPort", pairingPort)
                .putString("pairingCode", pairingCode)
                .putInt("displayMode", displayMode)
                .putInt("navigationPosition", navigationPosition)
                .apply();
    }

    public ProjectionSettings withPhoneIp(String value) {
        return new ProjectionSettings(value, adbPort, width, height, bitrateMbps, fps, densityDpi,
                mirrorMode, navigationBar, debugPort, pairingPort, pairingCode, displayMode, navigationPosition);
    }

    public ProjectionSettings withAdbPort(int value) {
        return new ProjectionSettings(phoneIp, value, width, height, bitrateMbps, fps, densityDpi,
                mirrorMode, navigationBar, debugPort, pairingPort, pairingCode, displayMode, navigationPosition);
    }

    public ProjectionSettings withStreamingOptions(int maxWidth, int maxHeight, int bitrate,
            int frameRate, int dpi, boolean mirror, boolean navBar, int scaleMode, int navPosition) {
        return new ProjectionSettings(phoneIp, adbPort, maxWidth, maxHeight, bitrate, frameRate, dpi,
                mirror, navBar, debugPort, pairingPort, pairingCode, scaleMode, navPosition);
    }

    public ProjectionSettings withPairingSettings(int wirelessDebugPort, int pairPort,
            String pairCode) {
        int unifiedPort = wirelessDebugPort > 0 ? wirelessDebugPort : adbPort;
        return new ProjectionSettings(phoneIp, unifiedPort, width, height, bitrateMbps, fps,
                densityDpi, mirrorMode, navigationBar, wirelessDebugPort, pairPort, pairCode, displayMode, navigationPosition);
    }
}

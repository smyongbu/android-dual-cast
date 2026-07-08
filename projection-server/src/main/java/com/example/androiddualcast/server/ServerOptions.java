package com.example.androiddualcast.server;

public final class ServerOptions {
    public final String mode;
    public final int width;
    public final int height;
    public final int densityDpi;
    public final int bitrateMbps;
    public final int fps;
    public final String launcherPackage;

    private ServerOptions(String mode, int width, int height, int densityDpi,
            int bitrateMbps, int fps, String launcherPackage) {
        this.mode = mode;
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.bitrateMbps = bitrateMbps;
        this.fps = fps;
        this.launcherPackage = launcherPackage;
    }

    public static ServerOptions parse(String[] args) {
        String mode = value(args, "--mode", "mirror");
        int[] size = parseSize(value(args, "--size", "1280x720"));
        int density = parseInt(value(args, "--dpi", "240"), 240);
        int bitrate = parseBitrate(value(args, "--bitrate", "4M"), 4);
        int fps = parseInt(value(args, "--fps", "25"), 25);
        String launcher = value(args, "--launcher", "com.teslacoilsw.launcher");
        return new ServerOptions(mode, size[0], size[1], density, bitrate, fps, launcher);
    }

    private static String value(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static int[] parseSize(String value) {
        String[] parts = value.split("x");
        if (parts.length != 2) {
            return new int[] {1280, 720};
        }
        return new int[] {parseInt(parts[0], 1280), parseInt(parts[1], 720)};
    }

    private static int parseBitrate(String value, int fallbackMbps) {
        String clean = value.toUpperCase().replace("MBPS", "").replace("M", "");
        return parseInt(clean, fallbackMbps);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}


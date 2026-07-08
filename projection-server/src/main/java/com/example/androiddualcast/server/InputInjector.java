package com.example.androiddualcast.server;

public final class InputInjector {
    private InputInjector() {
    }

    public static void injectTouchPacket(String packet) {
        /*
         * Production implementation should parse the packet and inject into the
         * selected display. scrcpy uses shell-level privileges for this class of
         * operation, which is why this code runs through ADB app_process.
         */
    }

    public static void injectKeyPacket(String packet) {
        /*
         * Production implementation should map receiver navigation keys to Android
         * key events or app launch commands.
         */
    }
}


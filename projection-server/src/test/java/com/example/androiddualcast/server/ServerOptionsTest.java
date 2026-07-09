package com.example.androiddualcast.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ServerOptionsTest {
    @Test
    public void parsesMirrorOptions() {
        ServerOptions options = ServerOptions.parse(new String[] {
                "--mode", "mirror",
                "--size", "1280x720",
                "--dpi", "240",
                "--bitrate", "4M",
                "--fps", "25",
                "--launcher", "com.example.launcher"
        });

        assertEquals("mirror", options.mode);
        assertEquals(1280, options.width);
        assertEquals(720, options.height);
        assertEquals(240, options.densityDpi);
        assertEquals(4, options.bitrateMbps);
        assertEquals(25, options.fps);
        assertEquals("com.example.launcher", options.launcherPackage);
    }

    @Test
    public void usesDefaultsForInvalidValues() {
        ServerOptions options = ServerOptions.parse(new String[] {
                "--size", "bad",
                "--dpi", "bad",
                "--bitrate", "bad",
                "--fps", "bad"
        });

        assertEquals("mirror", options.mode);
        assertEquals(1280, options.width);
        assertEquals(720, options.height);
        assertEquals(240, options.densityDpi);
        assertEquals(4, options.bitrateMbps);
        assertEquals(25, options.fps);
    }
}


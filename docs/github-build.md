# Build APKs On GitHub

This project includes a GitHub Actions workflow at:

```text
.github/workflows/android-build.yml
```

## Steps

1. Create an empty GitHub repository.
2. Upload all files in this folder to the repository.
3. Open the repository on GitHub.
4. Go to `Actions`.
5. Run `Android Build`, or push a commit to `main` / `master`.
6. After the build finishes, open the workflow run.
7. Download the artifact named `android-dual-cast-debug-apks`.

The artifact contains:

- `receiver-app-debug.apk`: install on the Android 4.4 receiver or car unit.
- `host-helper-debug.apk`: install on the high-performance phone.
- `projection-server-debug.aar`: phone-side server skeleton for later ADB launch packaging.

## Current Prototype Status

The receiver APK already contains:

- full-screen projection surface;
- settings for phone IP, bitrate, FPS, DPI, mirror mode and navigation bar;
- wireless-debugging pairing input screen;
- control channel packet sender;
- touch coordinate mapping to 1280x720;
- H.264 stream receiver and `MediaCodec` decoder skeleton.

The host-helper APK already contains:

- overlay permission shortcut;
- developer-options shortcut;
- phone-side setup tips.

The missing production pieces are:

- Android receiver acting as a full ADB host;
- real display capture and H.264 encoding;
- input injection into the selected display;
- extended-display app launching and Nova binding;
- audio stream.

# Android Dual Cast

Android Dual Cast is a planned Android-to-Android projection system inspired by
AndroidCast, scrcpy, Android Auto and CarLife style workflows.

The goal is:

- Phone A runs the real apps and provides the projected display.
- Device B, such as an Android 4.4 car unit or tablet, shows a 1280x720
  projected screen.
- Device B sends touch and key events back to Phone A.
- In extended mode, Phone A and Device B can be operated independently.

This repository currently contains the project skeleton and protocol design.
It is intentionally split into two Android apps:

- `host-helper`: installed on Phone A. It assists with permissions, overlay,
  keep-alive, rotation and extended-display preparation.
- `receiver-app`: installed on Device B. It connects to Phone A through USB ADB
  or wireless debugging, shows video full-screen and sends input events back.

## Target Behavior

1. Install `host-helper` on the phone.
2. Install a launcher such as Nova Launcher on the phone. It can be used as the
   projected desktop in extended display mode.
3. Install `receiver-app` on the Android 4.4 receiver.
4. Enable USB debugging on the phone. Wireless debugging can be enabled after
   the first wired pairing.
5. Receiver connects to the phone and starts one of these modes:
   - Mirror mode: shows the phone's current display.
   - Extended mode: starts an auxiliary or virtual display and projects that
     display instead of the main phone screen.

## Important Limits

This kind of app does not need root when it controls the phone through ADB or
wireless debugging, but the user must explicitly enable debugging and approve
the pairing prompt.

Extended mode depends on Android display-management behavior. Some phones and
ROMs may only work through an auxiliary display window, while others can use a
virtual display. Some third-party apps may still open on the phone's main screen
and need to be moved to the projected display.

## Implementation Stages

1. Receiver UI, settings and touch capture.
2. Wireless debugging pairing flow.
3. ADB transport from Android receiver to phone.
4. Video decoding with `MediaCodec` on Android 4.4.
5. Input event forwarding.
6. Mirror mode.
7. Extended display mode.
8. Navigation bar shortcuts and three-finger app transfer.

## Build On GitHub

This repository contains a GitHub Actions workflow that can build the APKs in
the cloud. See `docs/github-build.md`.

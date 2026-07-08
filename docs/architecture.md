# Architecture

## Components

### Receiver App

Runs on the second Android device, with Android 4.4 as the minimum target.

Responsibilities:

- Full-screen 1280x720 display surface.
- H.264 video decoding through `MediaCodec`.
- Touch, back, home and shortcut capture.
- Settings for phone IP, bitrate, frame rate, DPI, mirror mode and audio.
- ADB or wireless-debugging connection coordinator.

### Host Helper

Runs on the high-performance phone.

Responsibilities:

- Request overlay permission.
- Request notification-listener permission when needed.
- Keep the helper alive in the background.
- Help force landscape orientation for projected apps.
- Create or guide the user to auxiliary/virtual display behavior.
- Broadcast phone availability for automatic receiver connection.

### Phone Projection Service

This is the privileged runtime started on the phone through ADB, similar in
spirit to scrcpy's server process.

Responsibilities:

- Capture the selected display.
- Encode the selected display to H.264.
- Receive input packets from the receiver.
- Inject input events into the selected display.
- Start selected apps on the projected display when the Android version allows
  it.

This repository does not vendor scrcpy code yet. The cleanest route is to keep
the transport and UI independent, then either:

- integrate scrcpy server behavior under its license, or
- implement a small custom server for the subset of features needed here.

## Modes

### Mirror Mode

The phone's main display is captured. This is the easiest and most compatible
mode, but both devices control the same screen.

### Extended Mode

The receiver shows an auxiliary or virtual display. A launcher such as Nova can
act as the projected desktop. This is what makes the phone and receiver feel
independent.

The practical behavior can vary by phone ROM:

- Auxiliary display window: more compatible, but the phone may show a small
  display window.
- Virtual display: cleaner phone-side experience, but app launching can be less
  reliable.

## Protocol

Control messages are small JSON packets sent over TCP. Video is H.264 over a
separate stream.

Initial ports:

- `27183`: control channel.
- `27184`: video channel.
- `27185`: optional audio channel.


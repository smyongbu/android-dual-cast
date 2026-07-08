# ADB Command Plan

The final app needs the receiver to perform the same class of work that desktop
scrcpy performs through ADB.

## Connect

Wired:

```text
adb devices
```

Wireless after pairing:

```text
adb connect PHONE_IP:DEBUG_PORT
```

## Push Phone Server

```text
adb push projection-server.jar /data/local/tmp/projection-server.jar
```

## Start Mirror Mode

```text
adb shell CLASSPATH=/data/local/tmp/projection-server.jar app_process / com.example.androiddualcast.server.Main --mode mirror --bitrate 4M --fps 25
```

## Start Extended Mode

```text
adb shell CLASSPATH=/data/local/tmp/projection-server.jar app_process / com.example.androiddualcast.server.Main --mode extended --size 1280x720 --dpi 240 --launcher com.teslacoilsw.launcher
```

## Why The Phone Server Is Needed

The receiver app cannot directly capture or inject input into the phone. It must
ask the phone, through ADB, to start a small process with shell-level privileges.
That process performs display capture, encoding and input injection.

This is also why the phone does not need root, but it does need USB debugging or
wireless debugging.


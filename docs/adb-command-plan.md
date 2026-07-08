# ADB 命令计划

最终版本中，第二台安卓设备需要完成类似桌面 scrcpy 的 ADB 工作。

## 连接手机

有线连接时，常见命令是：

```text
adb devices
```

经典无线 ADB：

```text
adb connect 手机IP:5555
```

Android 11+ 无线调试配对：

```text
adb pair 手机IP:配对端口
adb connect 手机IP:无线调试端口
```

当前 App 内置 ADB 客户端已经开始实现经典无线 ADB 连接，完整 `adb pair` 协议还在后续阶段。

## 推送手机端服务

目标命令：

```text
adb push projection-server.jar /data/local/tmp/projection-server.jar
```

接收端已经实现 ADB sync 推送骨架。后续会把 `projection-server.jar` 打包进 APK 的 assets 中，再由接收端自动推送。

## 启动镜像模式

目标命令：

```text
adb shell CLASSPATH=/data/local/tmp/projection-server.jar app_process / com.example.androiddualcast.server.Main --mode mirror --bitrate 4M --fps 25
```

## 启动扩展模式

目标命令：

```text
adb shell CLASSPATH=/data/local/tmp/projection-server.jar app_process / com.example.androiddualcast.server.Main --mode extended --size 1280x720 --dpi 240 --launcher com.teslacoilsw.launcher
```

## 为什么要手机端服务

第二台安卓设备不能直接捕获第一台手机画面，也不能直接注入触摸。

所以它必须通过 ADB 在第一台手机上启动一个 shell 级服务，由这个服务完成：

- 画面采集；
- 视频编码；
- 输入注入；
- 扩展屏管理；
- App 启动和流转。

这也是为什么这条路线不需要 root，但必须开启 USB 调试或无线调试。


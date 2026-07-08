# 使用 GitHub 编译 APK

项目已经包含 GitHub Actions 配置：

```text
.github/workflows/android-build.yml
```

## 编译步骤

1. 把项目上传到 GitHub 仓库。
2. 打开仓库页面。
3. 进入 `Actions`。
4. 运行 `Android Build`，或者直接推送一次代码触发编译。
5. 等待构建完成。
6. 打开对应的 workflow run。
7. 下载名为 `android-dual-cast-debug-apks` 的构建产物。

## 产物说明

- `receiver-app-debug.apk`：安装到第二台安卓设备或车机。
- `host-helper-debug.apk`：安装到第一台高性能手机。
- `projection-server-debug.aar`：手机端投屏服务骨架，后续会打包成可通过 ADB 启动的 jar。

## 当前 APK 能做什么

接收端 APK 已经包含：

- 全屏投屏界面；
- 手机 IP、ADB 端口、码率、FPS、DPI、镜像模式、导航栏设置；
- 无线调试配对输入界面；
- ADB 协议客户端；
- ADB RSA 授权握手；
- ADB shell 命令通道；
- ADB sync 文件推送骨架；
- 触摸坐标映射到 1280x720；
- H.264 视频流接收和 `MediaCodec` 解码骨架。

手机助手 APK 已经包含：

- 悬浮窗权限入口；
- 开发者选项入口；
- 手机端准备工作说明。

## 还没完成的部分

- 手机端真实画面采集；
- H.264 实时编码；
- 输入事件注入；
- 扩展屏创建和应用启动；
- Nova 桌面绑定；
- 音频流。


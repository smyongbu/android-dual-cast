这是安卓双端投屏项目的第一个原型版本。

包含：

- `receiver-app-debug.apk`：安装到第二台安卓设备或车机。
- `host-helper-debug.apk`：安装到第一台高性能手机。

当前版本已经包含：

- 接收端界面；
- 设置页；
- H.264 解码骨架；
- 内置 ADB 客户端；
- ADB RSA 授权握手；
- shell 命令通道；
- ADB sync 推送骨架。

注意：这仍是开发原型，还不是完整可用的 AndroidCast 替代品。

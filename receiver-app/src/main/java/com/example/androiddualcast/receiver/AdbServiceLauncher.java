package com.example.androiddualcast.receiver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.androiddualcast.receiver.adb.LibAdbSync;
import com.example.androiddualcast.receiver.adb.WirelessAdbManager;
import com.example.androiddualcast.receiver.adb.AdbPortDiscovery;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;

public final class AdbServiceLauncher {
    public interface Listener { void onStatus(int stringRes); void onDetail(String detail); }
    private static final String REMOTE = "/data/local/tmp/scrcpy-server-v3.3.4";
    private final Context context;
    private volatile ProjectionSettings settings;
    private final ProjectionView projectionView;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private AdbStream serverProcess;
    private AdbStream videoStream;
    private AdbStream controlStream;

    public AdbServiceLauncher(Context context, ProjectionSettings settings, ProjectionView view, Listener listener) {
        this.context = context.getApplicationContext(); this.settings = settings;
        this.projectionView = view; this.listener = listener;
    }

    public void updateSettings(ProjectionSettings updated) { this.settings = updated; }

    public void connectAndStart() {
        connectAndStart(null);
    }

    public void connectAndStart(String hotspotGateway) {
        AppLog.write(context, "投屏", "用户点击启动服务，设置目标=" + settings.phoneIp + ":" + settings.adbPort);
        postStatus(R.string.status_connecting);
        executor.execute(() -> {
            String stage = "连接无线 ADB";
            try {
                WirelessAdbManager manager = WirelessAdbManager.getInstance(context);
                try { manager.disconnect(); } catch (Exception ignored) {}
                String targetIp = settings.phoneIp;
                if (isCompleteIp(hotspotGateway) && isPortOpen(hotspotGateway, 5555, 700)) {
                    targetIp = hotspotGateway;
                    AppLog.write(context, "热点", "检测到热点网关固定端口可用：" + targetIp + ":5555");
                    postDetail("已识别手机热点 " + targetIp + "，正在连接…");
                }
                int port = 5555;
                boolean connected = false;
                try { connected = manager.connect(targetIp, port); }
                catch (Exception fixedError) { AppLog.write(context, "ADB", "固定热点端口尚不可用：" + fixedError.getMessage()); }
                if (!connected) {
                    try { manager.disconnect(); } catch (Exception ignored) {}
                    port = AdbPortDiscovery.discover(context, targetIp, 3500);
                    if (port <= 0) port = settings.adbPort;
                    AppLog.write(context, "ADB", "尝试无线调试连接端口=" + port);
                    connected = manager.connect(targetIp, port);
                }
                if (!connected) throw new Exception("无线 ADB 连接失败，当前端口=" + port);
                AppLog.write(context, "ADB", "TLS 连接成功");
                if (port != 5555) {
                    stage = "初始化热点一键连接";
                    postDetail("首次连接成功，正在开启热点固定端口 5555…");
                    AdbStream tcpip = manager.openStream("tcpip:5555");
                    try { tcpip.close(); } catch (Exception ignored) {}
                    Thread.sleep(1500);
                    try { manager.disconnect(); } catch (Exception ignored) {}
                    boolean fixedConnected = manager.connect(targetIp, 5555);
                    if (!fixedConnected) throw new Exception("固定端口 5555 已请求开启，但重新连接失败");
                    port = 5555;
                    AppLog.write(context, "ADB", "热点固定端口 5555 初始化成功");
                    postDetail("热点一键连接已启用，正在启动投屏…");
                }
                postDetail("ADB 已连接，正在传送画面采集服务…");
                stage = "传送画面采集服务";
                byte[] server;
                try (InputStream in = context.getAssets().open("scrcpy-server-v3.3.4")) { server = readAll(in); }
                LibAdbSync.push(manager, server, REMOTE, 0644);
                AppLog.write(context, "ADB", "服务文件推送成功，字节数=" + server.length);

                stage = "启动手机画面采集服务";
                int scid = (int) (System.nanoTime() & 0x7fffffff);
                String hex = String.format(Locale.US, "%08x", scid);
                String command = "CLASSPATH=" + REMOTE + " app_process / com.genymobile.scrcpy.Server 3.3.4"
                        + " scid=" + hex + " log_level=info audio=false control=true"
                        + " video_codec=h264 video_bit_rate=" + (settings.bitrateMbps * 1000000)
                        + " max_fps=" + settings.fps + " max_size=" + Math.max(settings.width, settings.height)
                        + " tunnel_forward=true send_device_meta=false send_dummy_byte=false"
                        + " send_codec_meta=true send_frame_meta=true cleanup=false";
                if (!settings.mirrorMode) {
                    command += " new_display=" + settings.width + "x" + settings.height + "/" + settings.densityDpi
                            + " vd_system_decorations=false vd_destroy_content=true";
                }
                // LocalServices.SHELL 会把包含空格的单个参数整体加引号，导致手机把整行
                // 当作一个可执行文件名。这里直接使用 ADB shell service 的原始目标格式。
                serverProcess = manager.openStream("shell:" + command);
                AppLog.write(context, "服务", "scrcpy 启动命令已发送，scid=" + hex);

                stage = "建立视频通道";
                Exception last = null;
                for (int i = 0; i < 30; i++) {
                    try {
                        videoStream = manager.openStream(LocalServices.LOCAL_UNIX_SOCKET_ABSTRACT, "scrcpy_" + hex);
                        AppLog.write(context, "视频", "视频通道连接成功，尝试次数=" + (i + 1));
                        last = null; break;
                    } catch (Exception e) { last = e; Thread.sleep(100); }
                }
                if (last != null || videoStream == null) {
                    String serverLog = readAvailable(serverProcess);
                    throw new Exception("手机画面采集服务没有建立视频通道"
                            + (serverLog.isEmpty() ? "" : "；手机日志：" + serverLog));
                }
                controlStream = manager.openStream(LocalServices.LOCAL_UNIX_SOCKET_ABSTRACT, "scrcpy_" + hex);
                projectionView.attachControl(controlStream.openOutputStream());
                AppLog.write(context, "控制", "scrcpy 触摸控制通道连接成功");
                if (!settings.mirrorMode) {
                    projectionView.startVirtualDesktop();
                    AppLog.write(context, "桌面", "已请求在虚拟屏幕启动手机桌面助手");
                }
                InputStream videoInput = videoStream.openInputStream();
                main.post(() -> projectionView.startVideo(videoInput, new H264VideoReceiver.Listener() {
                    @Override public void onVideoSize(int width, int height) { AppLog.write(context, "显示", "按模式 " + settings.displayMode + " 调整 " + width + "x" + height); }
                    @Override public void onInfo(String message) { AppLog.write(context, "解码", message); }
                    @Override public void onFirstFrame() { postDetail("投屏成功"); }
                    @Override public void onError(String message) { AppLog.write(context, "解码", "视频中断：" + message); postDetail("视频中断：" + message); }
                }));
                postDetail("视频通道已连接，等待手机首帧画面…");
            } catch (Exception e) {
                AppLog.write(context, "错误", "失败阶段=" + stage + "，" + android.util.Log.getStackTraceString(e));
                postStatus(R.string.status_failed);
                postDetail("失败阶段：" + stage + "\n"
                        + e.getClass().getSimpleName() + "："
                        + (e.getMessage() == null ? "无详细信息" : e.getMessage()));
            }
        });
    }

    private static boolean isCompleteIp(String ip) {
        return ip != null && ip.trim().length() > 0 && !ip.trim().endsWith(".");
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public void setHostOrientation(boolean landscape) {
        executor.execute(() -> {
            try {
                WirelessAdbManager manager = WirelessAdbManager.getInstance(context);
                String mode = landscape ? "landscape" : "portrait";
                AdbStream stream = manager.openStream("shell:am broadcast -a com.example.androiddualcast.host.SET_ORIENTATION"
                        + " -p com.example.androiddualcast.host --es mode " + mode);
                try { stream.close(); } catch (Exception ignored) {}
                postDetail(landscape ? "已切换为横屏" : "已切换为竖屏");
                AppLog.write(context, "方向", "独立桌面切换为" + (landscape ? "横屏" : "竖屏"));
            } catch (Exception e) {
                postDetail("方向切换失败，请先连接无线投屏");
                AppLog.write(context, "方向", "切换失败：" + e);
            }
        });
    }

    public void close() {
        stopSession();
        executor.shutdownNow();
    }

    public void stopSession() {
        try { if (controlStream != null) controlStream.close(); } catch (Exception ignored) {}
        try { if (videoStream != null) videoStream.close(); } catch (Exception ignored) {}
        try { if (serverProcess != null) serverProcess.close(); } catch (Exception ignored) {}
        controlStream = null; videoStream = null; serverProcess = null;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray();
    }
    private static String readAvailable(AdbStream stream) {
        if (stream == null) return "";
        try {
            InputStream in = stream.openInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            long end = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < end) {
                int available = in.available();
                if (available > 0) {
                    byte[] b = new byte[Math.min(available, 8192)];
                    int n = in.read(b); if (n > 0) out.write(b, 0, n);
                } else Thread.sleep(25);
            }
            return out.toString("UTF-8").trim().replace('\n', ' ');
        } catch (Exception ignored) { return ""; }
    }
    private void postStatus(int id) { main.post(() -> listener.onStatus(id)); }
    private void postDetail(String s) { main.post(() -> listener.onDetail(s)); }
}

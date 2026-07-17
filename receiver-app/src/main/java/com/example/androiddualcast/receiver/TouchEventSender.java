package com.example.androiddualcast.receiver;

import android.view.MotionEvent;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 低延迟 scrcpy 触摸发送器：整包写入并合并连续 MOVE。 */
public final class TouchEventSender {
    private static final class TouchPacket {
        final int action; final long id; final int x, y;
        TouchPacket(int action, long id, int x, int y) { this.action=action; this.id=id; this.x=x; this.y=y; }
    }
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<TouchPacket> latestMove = new AtomicReference<>();
    private volatile OutputStream output;
    private volatile int videoWidth = 1, videoHeight = 1;
    private volatile boolean landscape = true;

    public TouchEventSender(ProjectionSettings ignored) {
        executor.scheduleAtFixedRate(this::sendLatestMove, 0, 16, TimeUnit.MILLISECONDS);
    }
    public void connect() {}
    public void attach(OutputStream stream, int width, int height) {
        latestMove.set(null); output = stream; videoWidth = width; videoHeight = height;
    }
    public void updateVideoSize(int width, int height) { videoWidth = width; videoHeight = height; }

    public void startApp(String packageName) {
        executor.execute(() -> {
            try {
                byte[] name = packageName.getBytes("UTF-8"); int n = Math.min(255, name.length);
                byte[] packet = new byte[n + 2]; packet[0] = 16; packet[1] = (byte) n;
                System.arraycopy(name, 0, packet, 2, n); writePacket(packet);
            } catch (Exception ignored) {}
        });
    }

    /** scrcpy 的旋转设备控制消息，效果等同于设备方向传感器触发旋转。 */
    public void setOrientation(boolean wantLandscape) {
        executor.execute(() -> {
            if (landscape == wantLandscape) return;
            landscape = wantLandscape;
            writePacket(new byte[]{11});
        });
    }

    public void sendKey(int keyCode) {
        executor.execute(() -> {
            ByteBuffer down=ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN);down.put((byte)0).put((byte)0).putInt(keyCode).putInt(0).putInt(0);writePacket(down.array());
            ByteBuffer up=ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN);up.put((byte)0).put((byte)1).putInt(keyCode).putInt(0).putInt(0);writePacket(up.array());
        });
    }

    public void send(MotionEvent event, int viewWidth, int viewHeight) {
        if (output == null || viewWidth <= 0 || viewHeight <= 0) return;
        int masked = event.getActionMasked();
        int action = masked == MotionEvent.ACTION_POINTER_DOWN ? MotionEvent.ACTION_DOWN
                : masked == MotionEvent.ACTION_POINTER_UP ? MotionEvent.ACTION_UP : masked;
        int index = (masked == MotionEvent.ACTION_POINTER_DOWN || masked == MotionEvent.ACTION_POINTER_UP)
                ? event.getActionIndex() : 0;
        int x = Math.max(0, Math.min(videoWidth - 1, Math.round(event.getX(index) * videoWidth / viewWidth)));
        int y = Math.max(0, Math.min(videoHeight - 1, Math.round(event.getY(index) * videoHeight / viewHeight)));
        TouchPacket packet = new TouchPacket(action, event.getPointerId(index), x, y);
        if (action == MotionEvent.ACTION_MOVE) {
            // 覆盖旧 MOVE，避免网络稍慢时形成无法追回的触摸队列。
            latestMove.set(packet);
        } else {
            executor.execute(() -> {
                sendLatestMove();
                sendTouch(packet);
                if (packet.action == MotionEvent.ACTION_UP || packet.action == MotionEvent.ACTION_CANCEL) latestMove.set(null);
            });
        }
    }

    private void sendLatestMove() {
        TouchPacket packet = latestMove.getAndSet(null);
        if (packet != null) sendTouch(packet);
    }

    private void sendTouch(TouchPacket p) {
        int w = videoWidth, h = videoHeight;
        ByteBuffer b = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 2).put((byte) p.action).putLong(p.id);
        b.putInt(p.x).putInt(p.y).putShort((short) w).putShort((short) h);
        b.putShort((short) (p.action == MotionEvent.ACTION_UP || p.action == MotionEvent.ACTION_CANCEL ? 0 : 0xffff));
        b.putInt(0).putInt(0);
        writePacket(b.array());
    }

    private void writePacket(byte[] packet) {
        OutputStream out = output; if (out == null) return;
        try {
            // 一次 write 对应一个 ADB WRTE，避免把 32 字节拆成多轮 ACK。
            out.write(packet); out.flush();
        } catch (Exception e) { output = null; latestMove.set(null); }
    }
    public void close() { output = null; latestMove.set(null); executor.shutdownNow(); }
}

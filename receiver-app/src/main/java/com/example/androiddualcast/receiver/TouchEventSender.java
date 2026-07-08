package com.example.androiddualcast.receiver;

import android.view.MotionEvent;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TouchEventSender {
    private static final int CONTROL_PORT = 27183;

    private final ProjectionSettings settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Socket socket;
    private DataOutputStream output;

    public TouchEventSender(ProjectionSettings settings) {
        this.settings = settings;
    }

    public void connect() {
        if (settings.phoneIp == null || settings.phoneIp.trim().length() == 0) {
            return;
        }
        executor.execute(() -> {
            try {
                closeSocket();
                socket = new Socket();
                socket.connect(new InetSocketAddress(settings.phoneIp, CONTROL_PORT), 3000);
                output = new DataOutputStream(socket.getOutputStream());
                sendPacket(buildHelloPacket());
                sendPacket(buildModePacket());
            } catch (IOException ignored) {
                closeSocket();
            }
        });
    }

    public void send(MotionEvent event, int viewWidth, int viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        int action = event.getActionMasked();
        for (int i = 0; i < event.getPointerCount(); i++) {
            int projectedX = Math.round(event.getX(i) * settings.width / viewWidth);
            int projectedY = Math.round(event.getY(i) * settings.height / viewHeight);
            String packet = buildTouchPacket(action, event.getPointerId(i), projectedX,
                    projectedY, event.getEventTime());
            executor.execute(() -> sendPacket(packet));
        }
    }

    public void close() {
        executor.execute(this::closeSocket);
        executor.shutdown();
    }

    private String buildTouchPacket(int action, int pointerId, int x, int y, long timeMillis) {
        String actionName;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            actionName = "down";
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            actionName = "up";
        } else if (action == MotionEvent.ACTION_CANCEL) {
            actionName = "cancel";
        } else {
            actionName = "move";
        }
        return "{\"type\":\"touch\",\"action\":\"" + actionName + "\",\"pointerId\":"
                + pointerId + ",\"x\":" + x + ",\"y\":" + y + ",\"displayWidth\":"
                + settings.width + ",\"displayHeight\":" + settings.height
                + ",\"timeMillis\":" + timeMillis + "}";
    }

    private String buildHelloPacket() {
        return "{\"type\":\"hello\",\"client\":\"receiver-app\",\"protocol\":1,\"width\":"
                + settings.width + ",\"height\":" + settings.height + ",\"densityDpi\":"
                + settings.densityDpi + "}";
    }

    private String buildModePacket() {
        String mode = settings.mirrorMode ? "mirror" : "extended";
        return "{\"type\":\"set_mode\",\"mode\":\"" + mode + "\",\"bitrateMbps\":"
                + settings.bitrateMbps + ",\"fps\":" + settings.fps + ",\"densityDpi\":"
                + settings.densityDpi + ",\"navigationBar\":" + settings.navigationBar + "}";
    }

    private void sendPacket(String packet) {
        if (output == null) {
            return;
        }
        try {
            byte[] payload = packet.getBytes(StandardCharsets.UTF_8);
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
        } catch (IOException ignored) {
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        output = null;
        socket = null;
    }
}

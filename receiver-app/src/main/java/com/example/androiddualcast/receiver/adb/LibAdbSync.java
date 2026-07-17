package com.example.androiddualcast.receiver.adb;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;

/** ADB sync 协议的最小推送实现。 */
public final class LibAdbSync {
    private LibAdbSync() {}

    public static void push(WirelessAdbManager manager, byte[] data, String path, int mode) throws Exception {
        try (AdbStream stream = manager.openStream(LocalServices.SYNC);
             InputStream in = stream.openInputStream(); OutputStream out = stream.openOutputStream()) {
            request(out, "SEND", (path + "," + mode).getBytes(StandardCharsets.UTF_8));
            for (int p = 0; p < data.length; p += 65536) {
                int n = Math.min(65536, data.length - p);
                writeHeader(out, "DATA", n); out.write(data, p, n);
            }
            writeHeader(out, "DONE", (int) (System.currentTimeMillis() / 1000L)); out.flush();
            DataInputStream din = new DataInputStream(in);
            byte[] id = new byte[4]; din.readFully(id);
            int length = Integer.reverseBytes(din.readInt());
            String status = new String(id, StandardCharsets.US_ASCII);
            if (!"OKAY".equals(status)) {
                byte[] msg = new byte[Math.max(0, Math.min(length, 65536))]; din.readFully(msg);
                throw new java.io.IOException("推送服务失败：" + new String(msg, StandardCharsets.UTF_8));
            }
        }
    }

    private static void request(OutputStream out, String id, byte[] payload) throws Exception {
        writeHeader(out, id, payload.length); out.write(payload);
    }

    private static void writeHeader(OutputStream out, String id, int value) throws Exception {
        out.write(id.getBytes(StandardCharsets.US_ASCII));
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }
}

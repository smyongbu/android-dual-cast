package com.example.androiddualcast.receiver.adb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class AdbSync {
    private static final int ID_DATA = id("DATA");
    private static final int ID_DONE = id("DONE");
    private static final int ID_FAIL = id("FAIL");
    private static final int ID_OKAY = id("OKAY");
    private static final int ID_QUIT = id("QUIT");
    private static final int ID_SEND = id("SEND");
    private static final int MAX_CHUNK = 64 * 1024;

    private final AdbConnection connection;

    AdbSync(AdbConnection connection) {
        this.connection = connection;
    }

    void push(byte[] content, String remotePath, int mode) throws IOException {
        AdbConnection.Stream stream = connection.open("sync:");
        try {
            byte[] path = (remotePath + "," + mode).getBytes(StandardCharsets.UTF_8);
            writeRequest(stream, ID_SEND, path);

            int offset = 0;
            while (offset < content.length) {
                int length = Math.min(MAX_CHUNK, content.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(content, offset, chunk, 0, length);
                writeRequest(stream, ID_DATA, chunk);
                offset += length;
            }

            ByteBuffer done = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            done.putInt((int) (System.currentTimeMillis() / 1000L));
            writeRequest(stream, ID_DONE, done.array());
            readStatus(stream);
        } finally {
            writeRequest(stream, ID_QUIT, new byte[0]);
            stream.close();
        }
    }

    private static void writeRequest(AdbConnection.Stream stream, int id, byte[] payload)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(id);
        header.putInt(payload.length);
        stream.write(header.array());
        if (payload.length > 0) {
            stream.write(payload);
        }
    }

    private static void readStatus(AdbConnection.Stream stream) throws IOException {
        byte[] header = stream.readExactly(8);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        int length = buffer.getInt();
        byte[] payload = length > 0 ? stream.readExactly(length) : new byte[0];
        if (id == ID_OKAY) {
            return;
        }
        if (id == ID_FAIL) {
            throw new IOException(new String(payload, StandardCharsets.UTF_8));
        }
        throw new IOException("Unexpected sync status");
    }

    private static int id(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        return (bytes[0] & 0xff)
                | ((bytes[1] & 0xff) << 8)
                | ((bytes[2] & 0xff) << 16)
                | ((bytes[3] & 0xff) << 24);
    }

    public static byte[] readAll(java.io.InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}

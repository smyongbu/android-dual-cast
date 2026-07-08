package com.example.androiddualcast.receiver.adb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class AdbMessage {
    static final int AUTH = command("AUTH");
    static final int CLSE = command("CLSE");
    static final int CNXN = command("CNXN");
    static final int OKAY = command("OKAY");
    static final int OPEN = command("OPEN");
    static final int WRTE = command("WRTE");

    static final int AUTH_TOKEN = 1;
    static final int AUTH_SIGNATURE = 2;
    static final int AUTH_PUBLIC_KEY = 3;
    static final int VERSION = 0x01000000;
    static final int MAX_DATA = 4096;

    final int command;
    final int arg0;
    final int arg1;
    final byte[] payload;

    private AdbMessage(int command, int arg0, int arg1, byte[] payload) {
        this.command = command;
        this.arg0 = arg0;
        this.arg1 = arg1;
        this.payload = payload == null ? new byte[0] : payload;
    }

    static AdbMessage of(int command, int arg0, int arg1, byte[] payload) {
        return new AdbMessage(command, arg0, arg1, payload);
    }

    static byte[] stringPayload(String value) {
        return (value + "\0").getBytes(StandardCharsets.UTF_8);
    }

    void write(DataOutputStream output) throws IOException {
        writeIntLe(output, command);
        writeIntLe(output, arg0);
        writeIntLe(output, arg1);
        writeIntLe(output, payload.length);
        writeIntLe(output, checksum(payload));
        writeIntLe(output, command ^ 0xffffffff);
        output.write(payload);
        output.flush();
    }

    static AdbMessage read(DataInputStream input) throws IOException {
        int command = readIntLe(input);
        int arg0 = readIntLe(input);
        int arg1 = readIntLe(input);
        int length = readIntLe(input);
        int expectedChecksum = readIntLe(input);
        int magic = readIntLe(input);
        if ((command ^ 0xffffffff) != magic || length < 0 || length > 1024 * 1024) {
            throw new IOException("Invalid ADB message");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        if (checksum(payload) != expectedChecksum) {
            throw new IOException("Invalid ADB checksum");
        }
        return new AdbMessage(command, arg0, arg1, payload);
    }

    private static int command(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        return (bytes[0] & 0xff)
                | ((bytes[1] & 0xff) << 8)
                | ((bytes[2] & 0xff) << 16)
                | ((bytes[3] & 0xff) << 24);
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) {
            sum += b & 0xff;
        }
        return sum;
    }

    private static int readIntLe(DataInputStream input) throws IOException {
        int b0 = input.readUnsignedByte();
        int b1 = input.readUnsignedByte();
        int b2 = input.readUnsignedByte();
        int b3 = input.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void writeIntLe(DataOutputStream output, int value) throws IOException {
        output.writeByte(value & 0xff);
        output.writeByte((value >> 8) & 0xff);
        output.writeByte((value >> 16) & 0xff);
        output.writeByte((value >> 24) & 0xff);
    }
}


package com.example.androiddualcast.receiver.adb;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdbConnection {
    private final String host;
    private final int port;
    private final AdbCrypto crypto;
    private final AtomicInteger nextLocalId = new AtomicInteger(1);
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public AdbConnection(Context context, String host, int port) throws GeneralSecurityException {
        this.host = host;
        this.port = port;
        this.crypto = AdbCrypto.loadOrCreate(context);
    }

    public void connect() throws IOException, GeneralSecurityException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        AdbMessage.of(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_DATA,
                AdbMessage.stringPayload("host::")).write(output);

        boolean sentPublicKey = false;
        while (true) {
            AdbMessage message = AdbMessage.read(input);
            if (message.command == AdbMessage.CNXN) {
                return;
            }
            if (message.command == AdbMessage.AUTH && message.arg0 == AdbMessage.AUTH_TOKEN) {
                byte[] response = sentPublicKey ? crypto.adbPublicKeyPayload() : crypto.sign(message.payload);
                int type = sentPublicKey ? AdbMessage.AUTH_PUBLIC_KEY : AdbMessage.AUTH_SIGNATURE;
                AdbMessage.of(AdbMessage.AUTH, type, 0, response).write(output);
                sentPublicKey = true;
            }
        }
    }

    public String shell(String command) throws IOException {
        int localId = nextLocalId.getAndIncrement();
        AdbMessage.of(AdbMessage.OPEN, localId, 0,
                AdbMessage.stringPayload("shell:" + command)).write(output);
        int remoteId = -1;
        ByteArrayOutputStream result = new ByteArrayOutputStream();

        while (true) {
            AdbMessage message = AdbMessage.read(input);
            if (message.command == AdbMessage.OKAY && message.arg1 == localId) {
                remoteId = message.arg0;
            } else if (message.command == AdbMessage.WRTE && message.arg1 == localId) {
                result.write(message.payload, 0, message.payload.length);
                AdbMessage.of(AdbMessage.OKAY, localId, message.arg0, new byte[0]).write(output);
            } else if (message.command == AdbMessage.CLSE && message.arg1 == localId) {
                if (remoteId >= 0) {
                    AdbMessage.of(AdbMessage.CLSE, localId, remoteId, new byte[0]).write(output);
                }
                return new String(result.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;
        input = null;
        output = null;
    }
}


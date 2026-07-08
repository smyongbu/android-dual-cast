package com.example.androiddualcast.server;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ControlServer {
    private static final int CONTROL_PORT = 27183;
    private static final int MAX_PACKET_BYTES = 64 * 1024;

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(CONTROL_PORT)) {
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    readClient(socket);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void readClient(Socket socket) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        while (true) {
            int length = input.readInt();
            if (length <= 0 || length > MAX_PACKET_BYTES) {
                return;
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            handlePacket(new String(payload, StandardCharsets.UTF_8));
        }
    }

    private void handlePacket(String packet) {
        if (packet.contains("\"type\":\"touch\"")) {
            InputInjector.injectTouchPacket(packet);
        } else if (packet.contains("\"type\":\"key\"")) {
            InputInjector.injectKeyPacket(packet);
        }
    }
}


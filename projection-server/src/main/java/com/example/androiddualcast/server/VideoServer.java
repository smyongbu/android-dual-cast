package com.example.androiddualcast.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public final class VideoServer {
    private static final int VIDEO_PORT = 27184;

    private final ServerOptions options;

    public VideoServer(ServerOptions options) {
        this.options = options;
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(VIDEO_PORT)) {
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    streamTo(socket);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void streamTo(Socket socket) throws IOException {
        DisplayStreamer streamer = new DisplayStreamer(options, socket.getOutputStream());
        streamer.run();
    }
}


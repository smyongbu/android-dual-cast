package com.example.androiddualcast.server;

public final class ProjectionRuntime {
    private final ServerOptions options;
    private final ControlServer controlServer;
    private final VideoServer videoServer;

    public ProjectionRuntime(ServerOptions options) {
        this.options = options;
        this.controlServer = new ControlServer();
        this.videoServer = new VideoServer(options);
    }

    public void run() {
        Thread controlThread = new Thread(controlServer::run, "control-server");
        Thread videoThread = new Thread(videoServer::run, "video-server");
        controlThread.start();
        videoThread.start();

        try {
            controlThread.join();
            videoThread.join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}


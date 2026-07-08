package com.example.androiddualcast.server;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        ServerOptions options = ServerOptions.parse(args);
        ProjectionRuntime runtime = new ProjectionRuntime(options);
        runtime.run();
    }
}


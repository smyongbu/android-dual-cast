package com.example.androiddualcast.receiver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.androiddualcast.receiver.adb.AdbConnection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdbServiceLauncher {
    public interface Listener {
        void onStatus(int stringRes);
        void onDetail(String detail);
    }

    private final Context context;
    private final ProjectionSettings settings;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AdbServiceLauncher(Context context, ProjectionSettings settings, Listener listener) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.listener = listener;
    }

    public void connectAndStart() {
        postStatus(R.string.status_connecting);
        executor.execute(() -> {
            AdbConnection connection = null;
            try {
                connection = new AdbConnection(context, settings.phoneIp, settings.adbPort);
                connection.connect();
                postStatus(R.string.status_connected);
                String command = buildStartCommand();
                String output = connection.shell(command);
                postDetail(output);
                postStatus(R.string.status_server_started);
            } catch (Exception exception) {
                postDetail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                postStatus(R.string.status_failed);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }

    public void close() {
        executor.shutdownNow();
    }

    private String buildStartCommand() {
        String mode = settings.mirrorMode ? "mirror" : "extended";
        return "CLASSPATH=/data/local/tmp/projection-server.jar "
                + "app_process / com.example.androiddualcast.server.Main"
                + " --mode " + mode
                + " --size " + settings.width + "x" + settings.height
                + " --dpi " + settings.densityDpi
                + " --bitrate " + settings.bitrateMbps + "M"
                + " --fps " + settings.fps;
    }

    private void postStatus(int stringRes) {
        main.post(() -> listener.onStatus(stringRes));
    }

    private void postDetail(String detail) {
        main.post(() -> listener.onDetail(detail));
    }
}

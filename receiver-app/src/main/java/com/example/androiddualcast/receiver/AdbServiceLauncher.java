package com.example.androiddualcast.receiver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.androiddualcast.receiver.adb.AdbConnection;
import com.example.androiddualcast.receiver.adb.AdbSync;

import java.io.IOException;
import java.io.InputStream;
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
                pushServerIfBundled(connection);
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

    private void pushServerIfBundled(AdbConnection connection) throws IOException {
        try (InputStream input = context.getAssets().open("projection-server.jar")) {
            byte[] content = AdbSync.readAll(input);
            connection.push(content, "/data/local/tmp/projection-server.jar", 0644);
        } catch (IOException missingOrFailed) {
            /*
             * The current prototype can still start a server that was pushed manually.
             * Once the build packages projection-server.jar as an asset, this method
             * will automatically refresh it on every connection.
             */
        }
    }

    private void postStatus(int stringRes) {
        main.post(() -> listener.onStatus(stringRes));
    }

    private void postDetail(String detail) {
        main.post(() -> listener.onDetail(detail));
    }
}

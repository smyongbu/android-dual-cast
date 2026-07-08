package com.example.androiddualcast.receiver;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class H264VideoReceiver {
    private static final int VIDEO_PORT = 27184;
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

    private final ProjectionSettings settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private Socket socket;
    private MediaCodec decoder;

    public H264VideoReceiver(ProjectionSettings settings) {
        this.settings = settings;
    }

    public void start(Surface surface) {
        if (settings.phoneIp == null || settings.phoneIp.trim().length() == 0 || running) {
            return;
        }
        running = true;
        executor.execute(() -> receive(surface));
    }

    public void stop() {
        running = false;
        executor.execute(this::close);
        executor.shutdown();
    }

    private void receive(Surface surface) {
        try {
            decoder = MediaCodec.createDecoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc",
                    settings.width, settings.height);
            decoder.configure(format, surface, null, 0);
            decoder.start();

            socket = new Socket();
            socket.connect(new InetSocketAddress(settings.phoneIp, VIDEO_PORT), 3000);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (running) {
                int length = input.readInt();
                if (length <= 0 || length > MAX_FRAME_BYTES) {
                    break;
                }
                byte[] frame = new byte[length];
                input.readFully(frame);
                queueFrame(frame);
                drainDecoder(info);
            }
        } catch (IOException | RuntimeException ignored) {
        } finally {
            close();
        }
    }

    private void queueFrame(byte[] frame) {
        if (decoder == null) {
            return;
        }
        int inputIndex = decoder.dequeueInputBuffer(10_000);
        if (inputIndex < 0) {
            return;
        }
        ByteBuffer buffer;
        if (Build.VERSION.SDK_INT >= 21) {
            buffer = decoder.getInputBuffer(inputIndex);
        } else {
            buffer = decoder.getInputBuffers()[inputIndex];
        }
        if (buffer == null) {
            return;
        }
        buffer.clear();
        buffer.put(frame);
        decoder.queueInputBuffer(inputIndex, 0, frame.length,
                System.nanoTime() / 1000L, 0);
    }

    private void drainDecoder(MediaCodec.BufferInfo info) {
        if (decoder == null) {
            return;
        }
        int outputIndex;
        do {
            outputIndex = decoder.dequeueOutputBuffer(info, 0);
            if (outputIndex >= 0) {
                decoder.releaseOutputBuffer(outputIndex, true);
            }
        } while (outputIndex >= 0);
    }

    private void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        socket = null;

        if (decoder != null) {
            try {
                decoder.stop();
            } catch (RuntimeException ignored) {
            }
            try {
                decoder.release();
            } catch (RuntimeException ignored) {
            }
            decoder = null;
        }
    }
}

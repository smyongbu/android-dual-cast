package com.example.androiddualcast.receiver;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 解码 scrcpy 的 H.264 视频流。 */
public final class H264VideoReceiver {
    public interface Listener { void onVideoSize(int width, int height); void onInfo(String message); void onFirstFrame(); void onError(String message); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private MediaCodec decoder;

    public void start(InputStream source, Surface surface, Listener listener) {
        if (running) return;
        running = true;
        executor.execute(() -> receive(source, surface, listener));
    }

    private void receive(InputStream source, Surface surface, Listener listener) {
        int width = 0, height = 0, lastFrameSize = 0;
        try {
            DataInputStream in = new DataInputStream(source);
            int codec = in.readInt();
            if (codec != 0x68323634) throw new IllegalStateException("手机返回的不是 H.264 视频");
            width = in.readInt(); height = in.readInt();
            listener.onVideoSize(width, height);
            listener.onInfo("视频头：codec=h264，尺寸=" + width + "x" + height + "，Surface有效=" + surface.isValid());
            if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
                throw new IllegalArgumentException("视频尺寸无效：" + width + "x" + height);
            }
            decoder = createCompatibleDecoder(width, height, surface, listener);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean first = true;
            long queuedFrames = 0;
            long renderedFrames = 0;
            while (running) {
                long flagsPts = in.readLong();
                int length = in.readInt();
                lastFrameSize = length;
                if (length <= 0 || length > 8 * 1024 * 1024) throw new IllegalStateException("视频帧长度异常");
                byte[] frame = new byte[length]; in.readFully(frame);
                int index = -1;
                while (running && (index = decoder.dequeueInputBuffer(100000)) < 0) {
                    drainOutput(decoder, info, listener, false);
                }
                if (!running) break;
                ByteBuffer buffer = Build.VERSION.SDK_INT >= 21 ? decoder.getInputBuffer(index) : decoder.getInputBuffers()[index];
                if (buffer == null || buffer.capacity() < length) {
                    throw new IllegalStateException("解码输入缓冲区不足：需要=" + length
                            + "，容量=" + (buffer == null ? 0 : buffer.capacity()));
                }
                buffer.clear(); buffer.put(frame);
                int mediaFlags = (flagsPts & (1L << 63)) != 0 ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0;
                decoder.queueInputBuffer(index, 0, length, flagsPts & ((1L << 62) - 1), mediaFlags);
                queuedFrames++;

                int produced = drainOutput(decoder, info, listener, true);
                if (produced > 0) {
                    renderedFrames += produced;
                    if (first) {
                        first = false;
                        listener.onInfo("首帧已由 MediaCodec 输出；已送入=" + queuedFrames + "，已渲染=" + renderedFrames);
                        listener.onFirstFrame();
                    }
                }
            }
        } catch (Exception e) {
            if (running) listener.onError("尺寸=" + width + "x" + height + "，最后帧=" + lastFrameSize
                    + " 字节\n" + android.util.Log.getStackTraceString(e));
        } finally { running = false; closeDecoder(); }
    }

    public void stop() {
        // 不能在 UI 线程释放 decoder；接收线程可能正在 dequeueInputBuffer()。
        // 先结束循环，ADB 流关闭后接收线程会进入 finally 并安全释放。
        running = false;
    }

    public void release() { running = false; executor.shutdownNow(); }

    private static int drainOutput(MediaCodec decoder, MediaCodec.BufferInfo info,
                                   Listener listener, boolean waitOnce) {
        int rendered = 0;
        long timeout = waitOnce ? 10000 : 0;
        while (true) {
            int out = decoder.dequeueOutputBuffer(info, timeout);
            timeout = 0;
            if (out >= 0) {
                decoder.releaseOutputBuffer(out, true);
                rendered++;
            } else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat changed = decoder.getOutputFormat();
                listener.onInfo("解码输出格式：" + changed);
                if (changed.containsKey(MediaFormat.KEY_WIDTH) && changed.containsKey(MediaFormat.KEY_HEIGHT)) {
                    int changedWidth = changed.getInteger(MediaFormat.KEY_WIDTH);
                    int changedHeight = changed.getInteger(MediaFormat.KEY_HEIGHT);
                    if (changedWidth > 0 && changedHeight > 0) listener.onVideoSize(changedWidth, changedHeight);
                }
            } else if (out == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                continue;
            } else break;
        }
        return rendered;
    }

    private static MediaCodec createCompatibleDecoder(int width, int height, Surface surface, Listener listener) throws Exception {
        List<MediaCodecInfo> codecs = new ArrayList<>();
        MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        for (MediaCodecInfo info : infos) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if ("video/avc".equalsIgnoreCase(type)) { codecs.add(info); break; }
            }
        }
        // 先尝试硬件解码，失败后自动使用 c2.android/OMX.google 软件解码。
        Collections.sort(codecs, new Comparator<MediaCodecInfo>() {
            @Override public int compare(MediaCodecInfo a, MediaCodecInfo b) {
                return Boolean.compare(isSoftware(a.getName()), isSoftware(b.getName()));
            }
        });
        StringBuilder failures = new StringBuilder();
        for (MediaCodecInfo info : codecs) {
            String name = info.getName();
            boolean sizeSupported = true;
            try {
                MediaCodecInfo.VideoCapabilities video = info.getCapabilitiesForType("video/avc").getVideoCapabilities();
                sizeSupported = video.isSizeSupported(width, height);
            } catch (Exception ignored) {}
            listener.onInfo("尝试解码器：" + name + "，尺寸支持=" + sizeSupported);
            MediaCodec candidate = null;
            try {
                candidate = MediaCodec.createByCodecName(name);
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024);
                candidate.configure(format, surface, null, 0);
                candidate.start();
                listener.onInfo("已选用解码器：" + name);
                return candidate;
            } catch (Exception e) {
                failures.append(name).append('=').append(e.getClass().getSimpleName()).append(';');
                if (candidate != null) try { candidate.release(); } catch (Exception ignored) {}
            }
        }
        throw new IllegalArgumentException("所有 H.264 解码器均配置失败：" + failures);
    }

    private static boolean isSoftware(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains("software") || n.contains("sw");
    }
    private synchronized void closeDecoder() {
        if (decoder == null) return;
        try { decoder.stop(); } catch (Exception ignored) {}
        try { decoder.release(); } catch (Exception ignored) {}
        decoder = null;
    }
}

package com.example.androiddualcast.receiver;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class ProjectionView extends SurfaceView implements SurfaceHolder.Callback {
    private final ProjectionSettings settings;
    private final TouchEventSender touchSender;
    private final H264VideoReceiver videoReceiver;

    public ProjectionView(Context context, ProjectionSettings settings, TouchEventSender touchSender) {
        super(context);
        this.settings = settings;
        this.touchSender = touchSender;
        this.videoReceiver = new H264VideoReceiver();
        // 此 Surface 只交给 MediaCodec。部分车机厂商实现不允许同一个 Surface
        // 先连接 Canvas 生产者再连接视频解码器，否则 configure() 会报参数无效。
        getHolder().setFormat(PixelFormat.OPAQUE);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        touchSender.send(event, getWidth(), getHeight());
        return true;
    }

    public void release() {
        videoReceiver.release();
        touchSender.close();
    }

    public void pauseVideo() { videoReceiver.stop(); }

    public void startVideo(java.io.InputStream input, H264VideoReceiver.Listener listener) {
        videoReceiver.start(input, getHolder().getSurface(), new H264VideoReceiver.Listener() {
            @Override public void onVideoSize(int width, int height) {
                post(() -> applyDisplayMode(width, height));
                touchSender.updateVideoSize(width, height);
                listener.onVideoSize(width, height);
            }
            @Override public void onInfo(String message) { listener.onInfo(message); }
            @Override public void onFirstFrame() { listener.onFirstFrame(); }
            @Override public void onError(String message) { listener.onError(message); }
        });
    }

    public void attachControl(java.io.OutputStream output) {
        touchSender.attach(output, 1, 1);
    }

    public void startVirtualDesktop() {
        touchSender.startApp("com.example.androiddualcast.host");
    }

    public void setOrientation(boolean landscape) {
        touchSender.setOrientation(landscape);
    }

    private void applyDisplayMode(int videoWidth, int videoHeight) {
        if (!(getParent() instanceof android.view.View)) return;
        android.view.View parent = (android.view.View) getParent();
        int areaWidth = parent.getWidth(), areaHeight = parent.getHeight();
        if (areaWidth <= 0 || areaHeight <= 0 || settings.displayMode == 2) {
            setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        float sx = areaWidth / (float) videoWidth;
        float sy = areaHeight / (float) videoHeight;
        float scale = settings.displayMode == 1 ? Math.max(sx, sy) : Math.min(sx, sy);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                Math.max(1, Math.round(videoWidth * scale)), Math.max(1, Math.round(videoHeight * scale)));
        params.gravity = android.view.Gravity.CENTER;
        setLayoutParams(params);
    }

}

package com.example.androiddualcast.receiver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class ProjectionView extends SurfaceView implements SurfaceHolder.Callback {
    private final ProjectionSettings settings;
    private final TouchEventSender touchSender;
    private final H264VideoReceiver videoReceiver;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ProjectionView(Context context, ProjectionSettings settings, TouchEventSender touchSender) {
        super(context);
        this.settings = settings;
        this.touchSender = touchSender;
        this.videoReceiver = new H264VideoReceiver(settings);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawWaitingScreen(holder);
        videoReceiver.start(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        drawWaitingScreen(holder);
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
        videoReceiver.stop();
        touchSender.close();
    }

    private void drawWaitingScreen(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        canvas.drawColor(Color.rgb(31, 53, 83));
        paint.setColor(Color.WHITE);
        paint.setTextSize(42f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getResources().getString(R.string.waiting_title),
                canvas.getWidth() / 2f, canvas.getHeight() / 2f - 20f, paint);
        paint.setTextSize(24f);
        paint.setColor(Color.rgb(190, 200, 215));
        canvas.drawText(getResources().getString(R.string.waiting_subtitle) + " "
                        + settings.width + "x" + settings.height,
                canvas.getWidth() / 2f, canvas.getHeight() / 2f + 32f, paint);
        holder.unlockCanvasAndPost(canvas);
    }
}

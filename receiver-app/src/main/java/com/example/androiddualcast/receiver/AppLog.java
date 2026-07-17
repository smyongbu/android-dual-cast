package com.example.androiddualcast.receiver;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 记录可由用户导出的运行日志，不记录配对码和密钥。 */
public final class AppLog {
    private static final Object LOCK = new Object();
    private static final String FILE_NAME = "投屏助手日志.txt";
    private AppLog() {}

    public static void write(Context context, String tag, String message) {
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(file(context), true)) {
                String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).format(new Date());
                String line = time + " [" + tag + "] " + String.valueOf(message) + "\n";
                out.write(line.getBytes("UTF-8"));
            } catch (Exception ignored) {}
        }
    }

    public static String exportToDownloads(Context context) throws Exception {
        write(context, "日志", "用户导出日志；Android=" + Build.VERSION.RELEASE + ", SDK=" + Build.VERSION.SDK_INT);
        String name = "投屏助手日志-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("无法创建下载文件");
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) { copy(file(context), out); }
            return "下载/" + name;
        }
        File target = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name);
        try (OutputStream out = new FileOutputStream(target)) { copy(file(context), out); }
        return target.getAbsolutePath();
    }

    private static File file(Context context) { return new File(context.getFilesDir(), FILE_NAME); }
    private static void copy(File source, OutputStream out) throws Exception {
        try (FileInputStream in = new FileInputStream(source)) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
        }
    }
}

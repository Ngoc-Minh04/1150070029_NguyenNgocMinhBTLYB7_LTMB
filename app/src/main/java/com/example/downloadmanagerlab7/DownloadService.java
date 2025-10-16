package com.example.downloadmanagerlab7;

import android.app.Service;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadService extends Service {

    private boolean isPaused = false;
    private boolean isCanceled = false;
    private int downloadedBytes = 0;
    private String currentUrl;
    private File outputFile;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (action != null) {
            switch (action) {
                case "ACTION_PAUSE":
                    isPaused = true;
                    updateNotificationMessage("⏸️ Tạm dừng tải...");
                    return START_STICKY;

                case "ACTION_RESUME":
                    isPaused = false;
                    isCanceled = false;
                    updateNotificationMessage("▶️ Tiếp tục tải...");
                    new Thread(() -> downloadFile(currentUrl)).start();
                    return START_STICKY;

                case "ACTION_CANCEL":
                    isCanceled = true;
                    updateNotificationMessage("❌ Đã hủy tải");
                    if (outputFile != null && outputFile.exists()) {
                        outputFile.delete();
                    }
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
            }
        }

        currentUrl = intent.getStringExtra("url");
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        builder = createNotificationBuilder();

        startForeground(NOTIFICATION_ID, builder.build());
        new Thread(() -> downloadFile(currentUrl)).start();

        return START_STICKY;
    }

    private void downloadFile(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isEmpty()) {
                showErrorNotification("URL không hợp lệ!");
                return;
            }

            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
            }
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
                showErrorNotification("Lỗi kết nối: " + responseCode);
                return;
            }

            int fileLength = connection.getContentLength() + downloadedBytes;

            String fileName = Uri.parse(fileUrl).getLastPathSegment();
            if (fileName == null || fileName.trim().isEmpty()) fileName = "downloaded_file.bin";

            InputStream input = new BufferedInputStream(connection.getInputStream());
            FileOutputStream output;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    showErrorNotification("Không thể tạo file tải xuống");
                    return;
                }

                output = (FileOutputStream) getContentResolver().openOutputStream(uri);
                outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();
                outputFile = new File(downloadsDir, fileName);
                output = new FileOutputStream(outputFile, downloadedBytes > 0);
            }

            byte[] data = new byte[4096];
            int count;
            int total = downloadedBytes;

            while ((count = input.read(data)) != -1) {
                if (isCanceled) {
                    input.close();
                    output.close();
                    if (outputFile.exists()) outputFile.delete();
                    stopSelf();
                    return;
                }
                if (isPaused) {
                    output.close();
                    input.close();
                    return;
                }

                total += count;
                output.write(data, 0, count);
                downloadedBytes = total;

                int progress = (int) ((total * 100L) / fileLength);
                updateNotification(progress);
            }

            output.flush();
            output.close();
            input.close();

            showCompleteNotification(outputFile);

        } catch (Exception e) {
            Log.e("DownloadService", "Error: " + e.getMessage());
            showErrorNotification("Lỗi tải: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Download Notification", NotificationManager.IMPORTANCE_LOW);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        Intent pauseIntent = new Intent(this, DownloadReceiver.class);
        pauseIntent.setAction("ACTION_PAUSE");
        PendingIntent pausePending = PendingIntent.getBroadcast(this, 0, pauseIntent, flags);

        Intent resumeIntent = new Intent(this, DownloadReceiver.class);
        resumeIntent.setAction("ACTION_RESUME");
        PendingIntent resumePending = PendingIntent.getBroadcast(this, 1, resumeIntent, flags);

        Intent cancelIntent = new Intent(this, DownloadReceiver.class);
        cancelIntent.setAction("ACTION_CANCEL");
        PendingIntent cancelPending = PendingIntent.getBroadcast(this, 2, cancelIntent, flags);

        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Đang tải xuống")
                .setContentText("Đang tải: 0%")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)
                .addAction(android.R.drawable.ic_media_play, "Resume", resumePending)
                .addAction(android.R.drawable.ic_delete, "Cancel", cancelPending)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder;
    }

    private void updateNotification(int progress) {
        builder.setProgress(100, progress, false)
                .setContentText("Đang tải: " + progress + "%");
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showCompleteNotification(File file) {
        Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(fileUri, "*/*");
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent openPending = PendingIntent.getActivity(this, 3, openIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE : 0);

        builder.setContentText("✅ Tải hoàn tất: " + file.getName())
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(openPending);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showErrorNotification(String msg) {
        builder.setContentText("❌ " + msg)
                .setProgress(0, 0, false)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void updateNotificationMessage(String msg) {
        builder.setContentText(msg);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

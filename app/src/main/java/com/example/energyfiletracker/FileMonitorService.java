package com.example.energyfiletracker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileMonitorService extends Service {

    private static final String TAG = "FileMonitorService";
    private static final long EVENT_DEBOUNCE_MS = 1000; // 1 segundo para consolidar eventos

    private FileObserver fileObserver;
    private final Map<String, Long> lastEventTimeMap = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        Handler handler = new Handler();
        new Thread(new FileOperationMonitor(this)).start();

        return START_STICKY;
    }

    private class FileOperationMonitor implements Runnable {

        private Context context;

        public FileOperationMonitor(Context context) {
            this.context = context;
        }

        @Override
        public void run() {
            String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();

            fileObserver = new FileObserver(directoryPath) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path == null) {
                        return;
                    }

                    long currentTime = System.currentTimeMillis();

                    if (shouldLogEvent(path, currentTime)) {
                        String fileType = getFileType(path);
                        String currentDate = getCurrentDate();
                        long startTime = currentTime;
                        long fileSize = getFileSize(path);
                        long energyConsumption = getEnergyConsumption(event);

                        switch (event) {
                            case FileObserver.OPEN:
                                logOperation(fileType, "leitura", startTime, currentDate, fileSize, energyConsumption, path);
                                break;
                            case FileObserver.MODIFY:
                                logOperation(fileType, "gravação", startTime, currentDate, fileSize, energyConsumption, path);
                                break;
                            case FileObserver.DELETE:
                                logOperation(fileType, "remoção", startTime, currentDate, fileSize, energyConsumption, path);
                                break;
                            default:
                                break;
                        }
                    }
                }
            };

            fileObserver.startWatching();

            while (true) {
                SystemClock.sleep(1000);
            }
        }

        private boolean shouldLogEvent(String path, long currentTime) {
            Long lastEventTime = lastEventTimeMap.get(path);
            if (lastEventTime == null || (currentTime - lastEventTime) > EVENT_DEBOUNCE_MS) {
                lastEventTimeMap.put(path, currentTime);
                return true;
            }
            return false;
        }

        private void logOperation(String fileType, String operationType, long startTime, String date, long fileSize, long energyConsumption, String path) {
            File logFile = new File(context.getExternalFilesDir(null), "file_operations_log.txt");
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(String.format(Locale.getDefault(),
                        "%s,%s,%d,%d,%s,%d,%d\n",
                        fileType, operationType, startTime, duration, date, fileSize, energyConsumption));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private long getFileSize(String path) {
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), path);
            return file.exists() ? file.length() : 0;
        }

        private long getEnergyConsumption(int event) {
            return 0; // Placeholder para o consumo de energia
        }

        private String getFileType(String path) {
            String extension = path.substring(path.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault());
            switch (extension) {
                case "txt":
                case "pdf":
                case "docx":
                    return "texto";
                case "mp3":
                case "wav":
                case "aac":
                    return "áudio";
                case "mp4":
                case "mkv":
                case "avi":
                    return "vídeo";
                default:
                    return "outro";
            }
        }

        private String getCurrentDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            return sdf.format(new Date());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fileObserver != null) {
            fileObserver.stopWatching();
        }
        Log.d(TAG, "Service destroyed");
    }
}

package com.devicesync.client;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;
import java.io.File;
import java.text.DecimalFormat;

public class SyncForegroundService extends Service implements LocationListener {
    private static final String TAG = "SyncService";
    private static final String CHANNEL_ID = "device_sync_channel";
    private static final String ALERT_CHANNEL_ID = "device_alert_channel";
    private static final int NOTIF_ID = 1001;

    private FirebaseManager firebaseManager;
    private LocationManager locationManager;
    private Handler handler;
    private Runnable syncRunnable;
    private Ringtone currentRingtone;
    private Vibrator vibrator;

    private Location lastKnownLocation;
    private String deviceId;
    private String dbUrl;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannels();

        SharedPreferences prefs = getSharedPreferences("DeviceSyncPrefs", MODE_PRIVATE);
        deviceId = prefs.getString("deviceId", "device_" + Build.MODEL.replaceAll("\\s+", "_"));
        dbUrl = prefs.getString("dbUrl", "https://your-firebase-project-default-rtdb.firebaseio.com");

        firebaseManager = new FirebaseManager(dbUrl, deviceId);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        startForeground(NOTIF_ID, buildForegroundNotification());
        initLocationUpdates();
        startPeriodicSync();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Device Sync Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps sync active with controller");

            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Admin Priority Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Important alerts sent by the admin controller");
            alertChannel.enableVibration(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Device Sync Active")
                .setContentText("Connected & reporting to Admin Controller")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @SuppressLint("MissingPermission")
    private void initLocationUpdates() {
        try {
            if (locationManager != null) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 5, this);
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15000, 5, this);
                    if (lastKnownLocation == null) {
                        lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Location permission or provider error: " + e.getMessage());
        }
    }

    private void startPeriodicSync() {
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                performSync();
                checkForCommands();
                handler.postDelayed(this, 12000); // Sync & check every 12 seconds
            }
        };
        handler.post(syncRunnable);
    }

    private void performSync() {
        new Thread(() -> {
            try {
                // 1. Send Status (Battery, Network, Storage)
                JSONObject statusObj = new JSONObject();
                statusObj.put("battery", getBatteryLevel());
                statusObj.put("isCharging", isDeviceCharging());
                statusObj.put("network", getNetworkType());
                statusObj.put("storageFree", getAvailableInternalMemorySize());
                statusObj.put("storageTotal", getTotalInternalMemorySize());
                statusObj.put("lastSeen", System.currentTimeMillis());
                firebaseManager.pushStatus(statusObj);

                // 2. Send Location if available
                if (lastKnownLocation != null) {
                    JSONObject locObj = new JSONObject();
                    locObj.put("latitude", lastKnownLocation.getLatitude());
                    locObj.put("longitude", lastKnownLocation.getLongitude());
                    locObj.put("accuracy", lastKnownLocation.getAccuracy());
                    locObj.put("speed", lastKnownLocation.getSpeed());
                    locObj.put("timestamp", lastKnownLocation.getTime());
                    locObj.put("provider", lastKnownLocation.getProvider());
                    firebaseManager.pushLocation(locObj);
                }

                // 3. Send/Update Info
                JSONObject infoObj = new JSONObject();
                infoObj.put("id", deviceId);
                infoObj.put("brand", Build.MANUFACTURER);
                infoObj.put("model", Build.MODEL);
                infoObj.put("androidVersion", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                infoObj.put("isOnline", true);
                infoObj.put("lastSeen", System.currentTimeMillis());
                firebaseManager.pushDeviceInfo(infoObj);

            } catch (Exception e) {
                Log.e(TAG, "Error performing sync: " + e.getMessage());
            }
        }).start();
    }

    private void checkForCommands() {
        firebaseManager.checkCommands((ring, alertMessage, forceSync) -> {
            handler.post(() -> {
                // Handle Ring Siren Command
                if (ring) {
                    playSirenAlarm();
                } else {
                    stopSirenAlarm();
                }

                // Handle Alert Message Command
                if (alertMessage != null && !alertMessage.trim().isEmpty()) {
                    showAdminAlertNotification(alertMessage);
                    firebaseManager.acknowledgeCommand("alertMessage", "");
                }

                // Handle Force Sync Command
                if (forceSync) {
                    performSync();
                    firebaseManager.acknowledgeCommand("forceSync", false);
                }
            });
        });
    }

    private void playSirenAlarm() {
        try {
            if (currentRingtone == null || !currentRingtone.isPlaying()) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
                currentRingtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    currentRingtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                currentRingtone.play();

                if (vibrator != null) {
                    long[] pattern = {0, 500, 200, 500};
                    vibrator.vibrate(pattern, 0); // Repeat indefinitely
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing siren: " + e.getMessage());
        }
    }

    private void stopSirenAlarm() {
        try {
            if (currentRingtone != null && currentRingtone.isPlaying()) {
                currentRingtone.stop();
            }
            if (vibrator != null) {
                vibrator.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping siren: " + e.getMessage());
        }
    }

    private void showAdminAlertNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification alertNotif = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setContentTitle("URGENT: Admin Message")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .build();
            manager.notify((int) System.currentTimeMillis(), alertNotif);
        }
    }

    // Helper functions for device metrics
    private int getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (scale > 0) {
                return (int) ((level / (float) scale) * 100);
            }
        }
        return -1;
    }

    private boolean isDeviceCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    private String getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return "Wi-Fi";
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "Mobile Data";
                }
            }
        }
        return "Unknown";
    }

    private String getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        return formatSize(availableBlocks * blockSize);
    }

    private String getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        return formatSize(totalBlocks * blockSize);
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 MB";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    public void onLocationChanged(Location location) {
        this.lastKnownLocation = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && syncRunnable != null) {
            handler.removeCallbacks(syncRunnable);
        }
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        stopSirenAlarm();
    }
}

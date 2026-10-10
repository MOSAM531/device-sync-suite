package com.devicesync.client;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQ_CODE = 101;

    private EditText etDeviceId;
    private EditText etFirebaseUrl;
    private TextView tvStatus;
    private Button btnToggleService;
    private Button btnSaveConfig;
    private Button btnBatteryOptimization;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("DeviceSyncPrefs", MODE_PRIVATE);

        etDeviceId = findViewById(R.id.etDeviceId);
        etFirebaseUrl = findViewById(R.id.etFirebaseUrl);
        tvStatus = findViewById(R.id.tvStatus);
        btnToggleService = findViewById(R.id.btnToggleService);
        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        btnBatteryOptimization = findViewById(R.id.btnBatteryOptimization);

        // Load saved or default config
        String savedId = prefs.getString("deviceId", "device_" + Build.MODEL.replaceAll("[^a-zA-Z0-9]", "_"));
        String savedUrl = prefs.getString("dbUrl", "https://devicesync-default-rtdb.firebaseio.com");

        etDeviceId.setText(savedId);
        etFirebaseUrl.setText(savedUrl);

        btnSaveConfig.setOnClickListener(v -> saveConfiguration());
        btnToggleService.setOnClickListener(v -> toggleSyncService());
        btnBatteryOptimization.setOnClickListener(v -> requestIgnoreBatteryOptimization());

        checkAndRequestPermissions();
        updateServiceStatusDisplay();
    }

    private void saveConfiguration() {
        String id = etDeviceId.getText().toString().trim();
        String url = etFirebaseUrl.getText().toString().trim();

        if (id.isEmpty()) {
            Toast.makeText(this, "Device ID cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        prefs.edit().putString("deviceId", id).putString("dbUrl", url).apply();
        Toast.makeText(this, "Settings Saved! Restarting service if running...", Toast.LENGTH_SHORT).show();

        // If running, restart to apply new config
        stopSyncService();
        startSyncService();
    }

    private void toggleSyncService() {
        if (isServiceRunning()) {
            stopSyncService();
        } else {
            startSyncService();
        }
    }

    private void startSyncService() {
        Intent serviceIntent = new Intent(this, SyncForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        prefs.edit().putBoolean("isServiceRunning", true).apply();
        updateServiceStatusDisplay();
        Toast.makeText(this, "Device Sync Service Started", Toast.LENGTH_SHORT).show();
    }

    private void stopSyncService() {
        Intent serviceIntent = new Intent(this, SyncForegroundService.class);
        stopService(serviceIntent);
        prefs.edit().putBoolean("isServiceRunning", false).apply();
        updateServiceStatusDisplay();
        Toast.makeText(this, "Device Sync Service Stopped", Toast.LENGTH_SHORT).show();
    }

    private boolean isServiceRunning() {
        return prefs.getBoolean("isServiceRunning", false);
    }

    private void updateServiceStatusDisplay() {
        if (isServiceRunning()) {
            tvStatus.setText("● SYNC STATUS: ACTIVE (ONLINE)");
            tvStatus.setTextColor(0xFF00E676); // Green
            btnToggleService.setText("Stop Background Service");
        } else {
            tvStatus.setText("● SYNC STATUS: STOPPED");
            tvStatus.setTextColor(0xFFFF5252); // Red
            btnToggleService.setText("Start Background Service");
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQ_CODE);
        }
    }

    private void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Please allow background activity in Settings", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Battery optimization already disabled!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show();
                if (!isServiceRunning()) {
                    startSyncService();
                }
            } else {
                Toast.makeText(this, "Location and notification permissions required for sync", Toast.LENGTH_LONG).show();
            }
        }
    }
}

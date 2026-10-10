package com.devicesync.client;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private final String databaseUrl;
    private final String deviceId;

    public interface CommandCallback {
        void onCommandReceived(boolean ring, String alertMessage, boolean forceSync);
    }

    public FirebaseManager(String databaseUrl, String deviceId) {
        // Clean URL to ensure no trailing slash
        if (databaseUrl.endsWith("/")) {
            this.databaseUrl = databaseUrl.substring(0, databaseUrl.length() - 1);
        } else {
            this.databaseUrl = databaseUrl;
        }
        this.deviceId = deviceId;
    }

    public boolean pushDeviceInfo(JSONObject infoJson) {
        return sendPatch("/devices/" + deviceId + "/info.json", infoJson.toString());
    }

    public boolean pushStatus(JSONObject statusJson) {
        return sendPatch("/devices/" + deviceId + "/status.json", statusJson.toString());
    }

    public boolean pushLocation(JSONObject locationJson) {
        return sendPatch("/devices/" + deviceId + "/location.json", locationJson.toString());
    }

    public void checkCommands(CommandCallback callback) {
        new Thread(() -> {
            try {
                String endpoint = databaseUrl + "/devices/" + deviceId + "/commands.json";
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    String jsonStr = response.toString();
                    if (!jsonStr.equals("null") && !jsonStr.isEmpty()) {
                        JSONObject cmdJson = new JSONObject(jsonStr);
                        boolean ring = cmdJson.optBoolean("ring", false);
                        String alertMessage = cmdJson.optString("alertMessage", "");
                        boolean forceSync = cmdJson.optBoolean("forceSync", false);

                        if (callback != null) {
                            callback.onCommandReceived(ring, alertMessage, forceSync);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking commands: " + e.getMessage());
            }
        }).start();
    }

    public void acknowledgeCommand(String key, Object value) {
        new Thread(() -> {
            try {
                JSONObject update = new JSONObject();
                update.put(key, value);
                sendPatch("/devices/" + deviceId + "/commands.json", update.toString());
            } catch (Exception e) {
                Log.e(TAG, "Error acknowledging command: " + e.getMessage());
            }
        }).start();
    }

    private boolean sendPatch(String path, String jsonPayload) {
        try {
            // Firebase Realtime DB REST accepts PATCH to update child values without overwriting entire object
            String endpoint = databaseUrl + path;
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); // Use X-HTTP-Method-Override: PATCH for HttpURLConnection
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            Log.e(TAG, "Error sending patch to " + path + ": " + e.getMessage());
            return false;
        }
    }
}

package com.example.smartbottle;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

public class ThingsBoardSyncManager {
    private static final String TAG = "ThingsBoardSync";

    public static void syncData(Context context, int amountMl, int totalToday) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String baseUrl = prefs.getString("tb_url", "");
        String token = prefs.getString("tb_token", "");

        if (baseUrl.isEmpty() || token.isEmpty()) return;

        new Thread(() -> {
            try {
                URL url = new URL(baseUrl + "/api/v1/" + token + "/telemetry");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Perbaikan format JSON agar sesuai dengan contoh CURL
                // ThingsBoard mengharapkan format {"key": value}
                // Mengirim keduanya agar ThingsBoard bisa memproses event dan total harian
                String payload = String.format(Locale.getDefault(), "{\"konsumsi_air\": %d, \"total_today\": %d}", amountMl, totalToday);
                Log.d(TAG, "Mengirim ke URL: " + url.toString());
                Log.d(TAG, "Payload: " + payload);
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                Log.d(TAG, "Response Code: " + code);
                if (code == 200) {
                    Log.d(TAG, "Sync Berhasil");
                } else {
                    Log.e(TAG, "Sync Gagal: " + code);
                    // TODO: Implement local caching/queue for retry
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Sync Error: " + e.getMessage(), e);
            }
        }).start();
    }
}
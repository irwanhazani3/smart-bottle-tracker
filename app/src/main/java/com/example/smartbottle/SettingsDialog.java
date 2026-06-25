package com.example.smartbottle;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class SettingsDialog {

    public interface OnSettingsSavedListener {
        void onSettingsSaved(int newTarget, int bleInterval);
    }

    public static void show(Context context, OnSettingsSavedListener listener) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Pengaturan Target");

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null);
        EditText etWeight = view.findViewById(R.id.etWeight);
        EditText etAge = view.findViewById(R.id.etAge);
        EditText etManual = view.findViewById(R.id.etManualTarget);
        EditText etStability = view.findViewById(R.id.etStabilityDelay);
        EditText etStabilityTolerance = view.findViewById(R.id.etStabilityTolerance);
        EditText etDrinkThreshold = view.findViewById(R.id.etDrinkThreshold);
        EditText etRefillThreshold = view.findViewById(R.id.etRefillThreshold);
        EditText etBleInterval = view.findViewById(R.id.etBleInterval);
        EditText etTbUrl = view.findViewById(R.id.etTbUrl);
        EditText etTbToken = view.findViewById(R.id.etTbToken);

        // Load existing
        etWeight.setText(String.valueOf(prefs.getFloat("weight", 70.0f)));
        etAge.setText(String.valueOf(prefs.getInt("age", 25)));
        int manual = prefs.getInt("manual_target", 0);
        if (manual > 0) etManual.setText(String.valueOf(manual));
        etStability.setText(String.valueOf(prefs.getInt("stability_delay", 5)));
        etStabilityTolerance.setText(String.valueOf(prefs.getFloat("stability_tolerance", 3.0f)));
        etDrinkThreshold.setText(String.valueOf(prefs.getInt("drink_threshold", 15)));
        etRefillThreshold.setText(String.valueOf(prefs.getInt("refill_threshold", 30)));
        etBleInterval.setText(String.valueOf(prefs.getInt("ble_interval", 5000)));
        etTbUrl.setText(prefs.getString("tb_url", ""));
        etTbToken.setText(prefs.getString("tb_token", ""));

        builder.setView(view);
        builder.setPositiveButton("Simpan", (dialog, which) -> {
            float weight = Float.parseFloat(etWeight.getText().toString());
            int age = Integer.parseInt(etAge.getText().toString());
            String manualStr = etManual.getText().toString();
            int manualTarget = manualStr.isEmpty() ? 0 : Integer.parseInt(manualStr);
            int stability = Integer.parseInt(etStability.getText().toString());
            float tolerance = Float.parseFloat(etStabilityTolerance.getText().toString());
            int drinkThreshold = Integer.parseInt(etDrinkThreshold.getText().toString());
            int refillThreshold = Integer.parseInt(etRefillThreshold.getText().toString());
            int bleInterval = Integer.parseInt(etBleInterval.getText().toString());
            // Validasi: Pastikan interval minimal 100ms agar tidak crash di ESP32/BLE
            if (bleInterval < 100) bleInterval = 100;
            String tbUrl = etTbUrl.getText().toString();
            String tbToken = etTbToken.getText().toString();

            int finalTarget;
            if (manualTarget > 0) {
                finalTarget = manualTarget;
            } else {
                finalTarget = HydrationCalculator.calculateTarget(weight, age);
            }

            prefs.edit().putFloat("weight", weight).putInt("age", age)
                    .putInt("manual_target", manualTarget).putInt("current_target", finalTarget)
                    .putInt("stability_delay", stability)
                    .putFloat("stability_tolerance", tolerance)
                    .putInt("drink_threshold", drinkThreshold)
                    .putInt("refill_threshold", refillThreshold)
                    .putInt("ble_interval", bleInterval)
                    .putString("tb_url", tbUrl).putString("tb_token", tbToken).apply();
            
            listener.onSettingsSaved(finalTarget, bleInterval);
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }
}
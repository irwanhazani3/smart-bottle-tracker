package com.example.smartbottle;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.os.Handler;
import android.os.Looper;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLE_SMARTBOTTLE";

    // UUID wajib sama persis dengan ESP32
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView tvStatus, tvWeight, tvDailyTarget, tvDailyIntake, tvLastDrinkTime;
    private RecyclerView rvDrinkLogs;
    private DrinkLogAdapter logAdapter;
    private Button btnConnect, btnTare, btnManualAddDrink, btnSync;
    private ImageButton btnSettings;
    private MaterialCardView cardCurrentWeight, cardDailyProgress, cardLastDrink;
    private CircularProgressIndicator hydrationProgressIndicator;

    // Smart Bottle Logic Variables
    private float baselineWeight = 0.0f; // Berat stabil terakhir di meja
    private float currentCandidateWeight = 0.0f;
    private boolean isLifted = false;
    private boolean isStabilityTimerRunning = false;
    
    private float drinkThreshold, refillThreshold, stabilityTolerance;
    private final float LIFTED_THRESHOLD_GRAMS = 20.0f; // Di bawah ini dianggap sedang diangkat
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private long STABILITY_TIME_MS = 5000; 
    private Runnable stabilityRunnable;

    // Database and other managers
    private DatabaseHelper dbHelper;
    private AchievementManager achievementManager;
    private int dailyTargetMl;

    private int todayTotalIntakeMl = 0;


    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic targetCharacteristic;
    private boolean isScanning = false;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth ON");
                        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                        if (isScanning) {
                            mulaiScanBLE();
                        }
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "Bluetooth OFF");
                        isScanning = false;
                        tvStatus.setText("Status: Bluetooth Mati");
                        btnConnect.setEnabled(true);
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvWeight = findViewById(R.id.tvWeight);
        tvStatus = findViewById(R.id.tvStatus);
        tvWeight = findViewById(R.id.tvWeight);
        tvDailyTarget = findViewById(R.id.tvDailyTarget);
        tvDailyIntake = findViewById(R.id.tvDailyIntake);
        tvLastDrinkTime = findViewById(R.id.tvLastDrinkTime);
        rvDrinkLogs = findViewById(R.id.rvDrinkLogs);
        rvDrinkLogs.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new DrinkLogAdapter(new ArrayList<>());
        rvDrinkLogs.setAdapter(logAdapter);
        btnConnect = findViewById(R.id.btnConnect);
        btnTare = findViewById(R.id.btnTare);
        btnSettings = findViewById(R.id.btnSettings);
        btnSync = findViewById(R.id.btnSync);
        btnManualAddDrink = findViewById(R.id.btnManualAddDrink);
        cardCurrentWeight = findViewById(R.id.cardCurrentWeight);
        cardDailyProgress = findViewById(R.id.cardDailyProgress);
        cardLastDrink = findViewById(R.id.cardLastDrink);
        hydrationProgressIndicator = findViewById(R.id.hydrationProgressIndicator);

        dbHelper = new DatabaseHelper(this);
        achievementManager = new AchievementManager(this, dbHelper);
        
        // Load target and stability delay
        dailyTargetMl = getSharedPreferences("user_prefs", MODE_PRIVATE).getInt("current_target", 2000);
        loadDetectionSettings();

        // Initialize UI with current data
        updateDailyProgressUI();
        updateLastDrinkTimeUI();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        } else {
            Toast.makeText(this, "Bluetooth tidak tersedia di perangkat ini.", Toast.LENGTH_LONG).show();
            finish();
        }

        btnConnect.setOnClickListener(v -> {
            if (!isScanning) {
                periksaIzinDanScan();
            }
        });

        btnTare.setOnClickListener(v -> sendTareCommand());

        btnSync.setOnClickListener(v -> {
            ThingsBoardSyncManager.syncData(this, 0, todayTotalIntakeMl);
            Toast.makeText(this, "Sinkronisasi dimulai...", Toast.LENGTH_SHORT).show();
        });

        btnSettings.setOnClickListener(v -> {
            SettingsDialog.show(this, (newTarget, bleInterval) -> {
                dailyTargetMl = newTarget;
                loadDetectionSettings();
                
                // Send interval to ESP32
                sendIntervalCommand(bleInterval);

                updateDailyProgressUI();
            });
        });

        btnManualAddDrink.setOnClickListener(v -> {
            ManualAddDialog.show(this, amount -> {
                dbHelper.addDrinkLog(amount);
                achievementManager.checkAchievements(dbHelper.getTodayTotalIntake(), dailyTargetMl);
                
                // Sync manual entry to ThingsBoard
                ThingsBoardSyncManager.syncData(this, amount, dbHelper.getTodayTotalIntake());

                updateDailyProgressUI();
                updateLastDrinkTimeUI();
                Toast.makeText(this, "Berhasil menambah " + amount + " ml", Toast.LENGTH_SHORT).show();
            });
        });

        // Register Bluetooth state receiver
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
    }

    private void loadDetectionSettings() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        STABILITY_TIME_MS = prefs.getInt("stability_delay", 5) * 1000L;
        stabilityTolerance = prefs.getFloat("stability_tolerance", 3.0f);
        drinkThreshold = (float) prefs.getInt("drink_threshold", 15);
        refillThreshold = (float) prefs.getInt("refill_threshold", 30);
        Log.d(TAG, "Settings loaded: Stability=" + STABILITY_TIME_MS + "ms, Tolerance=" + stabilityTolerance + "g");
    }

    private void periksaIzinDanScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 101);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                return;
            }
        }
        mulaiScanBLE();
    }

    private void mulaiScanBLE() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth belum aktif!", Toast.LENGTH_SHORT).show();
            isScanning = false;
            tvStatus.setText("Status: Bluetooth Mati");
            btnConnect.setEnabled(true);
            return;
        }

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Gagal menginisialisasi Bluetooth Scanner", Toast.LENGTH_SHORT).show();
            isScanning = false;
            tvStatus.setText("Status: Error Bluetooth");
            btnConnect.setEnabled(true);
            return;
        }

        isScanning = true;
        tvStatus.setText("Status: Mencari ESP32...");
        btnConnect.setEnabled(false);
        Log.d(TAG, "Memulai pemindaian BLE...");

        try {
            bluetoothLeScanner.startScan(scanCallback);
        } catch (Exception e) {
            Log.e(TAG, "Gagal memulai scan: " + e.getMessage());
            isScanning = false;
            tvStatus.setText("Status: Gagal Memulai Scan");
            btnConnect.setEnabled(true);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    String deviceName = device.getName();
                    if (deviceName != null && deviceName.contains("ESP32_SmartBottle")) {
                        Log.d(TAG, "ESP32 Ditemukan! Menghentikan scan dan mencoba connect...");
                        bluetoothLeScanner.stopScan(scanCallback);
                        isScanning = false;

                        tvStatus.setText("Status: Menghubungkan...");
                        bluetoothGatt = device.connectGatt(MainActivity.this, true, gattCallback);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Eror Keamanan Izin: " + e.getMessage());
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Terhubung ke GATT Server.");
                runOnUiThread(() -> tvStatus.setText("Status: Terhubung! Mencari Service..."));
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) { e.printStackTrace(); }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt.connect();
                Log.i(TAG, "Terputus dari GATT Server.");
                //bluetoothGatt = null;
                targetCharacteristic = null;
                runOnUiThread(() -> {
                    tvStatus.setText("Botol belum terhubung. Silakan tekan Scan.");
                    tvWeight.setText("0 gram");
                    btnConnect.setEnabled(true);
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    targetCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (targetCharacteristic != null) {
                        try {
                            // Mengaktifkan Fitur Notify agar Android bisa menerima data berkala
                            gatt.setCharacteristicNotification(targetCharacteristic, true);
                            BluetoothGattDescriptor descriptor = targetCharacteristic.getDescriptor(DESCRIPTOR_UUID);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                            runOnUiThread(() -> tvStatus.setText("Status: Alat Siap Digunakan"));
                        } catch (SecurityException e) { e.printStackTrace(); }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                // Menerima data string berat dari ESP32
                String weightString = characteristic.getStringValue(0);
                Log.d(TAG, "Data Masuk Mentah: " + weightString);

                try {
                    float currentWeight = Float.parseFloat(weightString);
                    runOnUiThread(() -> {
                        tvWeight.setText(String.format(Locale.getDefault(), "%.1f gram", currentWeight));
                        if (!tvStatus.getText().toString().equals("Status: Alat Siap Digunakan")) {
                             tvStatus.setText("Status: Alat Siap Digunakan");
                        }
                    });
                    processWeightData(currentWeight);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Gagal mengurai data berat: " + weightString, e);
                }

            }
        }
    };

    // FUNGSI BARU: Mengirim perintah TARE ke ESP32 via BLE Write
    private void sendTareCommand() {
        if (bluetoothGatt != null && targetCharacteristic != null) {
            try {
                targetCharacteristic.setValue("TARE");
                targetCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                boolean success = bluetoothGatt.writeCharacteristic(targetCharacteristic);

                if (success) {
                    Toast.makeText(this, "Perintah Tare Dikirim!", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Perintah TARE berhasil dikirim lewat jalur komunikasi data.");
                } else {
                    Toast.makeText(this, "Gagal mengirim perintah Tare", Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Gagal menulis karakteristik (Izin ditolak): " + e.getMessage());
            }
        } else {
            Toast.makeText(this, "Hubungkan Bluetooth terlebih dahulu!", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendIntervalCommand(int intervalMs) {
        if (bluetoothGatt != null && targetCharacteristic != null) {
            try {
                String cmd = "INTERVAL=" + intervalMs;
                targetCharacteristic.setValue(cmd);
                targetCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                bluetoothGatt.writeCharacteristic(targetCharacteristic);
                Log.d(TAG, "Perintah INTERVAL dikirim: " + cmd);
            } catch (SecurityException e) {
                Log.e(TAG, "Izin ditolak: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) { e.printStackTrace(); }
        }
        unregisterReceiver(bluetoothStateReceiver);
        handler.removeCallbacksAndMessages(null); // Stop all pending handlers
    }

    // New method to update UI for daily progress
    private void updateDailyProgressUI() {
        todayTotalIntakeMl = dbHelper.getTodayTotalIntake();
        int progress = (dailyTargetMl > 0) ? (todayTotalIntakeMl * 100 / dailyTargetMl) : 0;
        hydrationProgressIndicator.setProgress(progress);
        tvDailyIntake.setText(String.format(Locale.getDefault(), "%d ml", todayTotalIntakeMl));
        tvDailyTarget.setText(String.format(Locale.getDefault(), "Target: %d ml", dailyTargetMl));
        tvStatus.setText(String.format(Locale.getDefault(), "Status: %s",
                (todayTotalIntakeMl >= dailyTargetMl) ? "Target Tercapai!" : "Terhubung & Siap"));
    }

    // New method to update last drink time UI
    private void updateLastDrinkTimeUI() {
        List<DrinkLog> todayLogs = dbHelper.getTodayLogs();
        logAdapter.updateLogs(todayLogs);
        if (!todayLogs.isEmpty()) {
            DrinkLog lastLog = todayLogs.get(0); 
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            tvLastDrinkTime.setText(String.format(Locale.getDefault(), "Terakhir minum: %s", sdf.format(new Date(lastLog.getTimestamp()))));
        } else {
            tvLastDrinkTime.setText("Terakhir minum: Belum ada");
        }
    }

    private void processWeightData(float currentWeight) {
        // 1. Inisialisasi awal jika baru dinyalakan
        if (baselineWeight == 0.0f && currentWeight > LIFTED_THRESHOLD_GRAMS) {
            baselineWeight = currentWeight;
            currentCandidateWeight = currentWeight;
            Log.d(TAG, "Inisialisasi Baseline: " + baselineWeight);
            return;
        }

        // 2. Cek Stabilitas (Apakah berat saat ini masih dalam rentang toleransi?)
        float diffFromCandidate = Math.abs(currentWeight - currentCandidateWeight);

        if (diffFromCandidate <= stabilityTolerance) {
            if (!isStabilityTimerRunning) {
                isStabilityTimerRunning = true;
                if (stabilityRunnable != null) handler.removeCallbacks(stabilityRunnable);
                
                stabilityRunnable = () -> {
                    onWeightStabilized(currentCandidateWeight);
                    isStabilityTimerRunning = false;
                };
                handler.postDelayed(stabilityRunnable, STABILITY_TIME_MS);
            }
        } else {
            // Berat berubah signifikan (guncangan atau baru saja diangkat/diletakkan)
            currentCandidateWeight = currentWeight;
            isStabilityTimerRunning = false;
            if (stabilityRunnable != null) handler.removeCallbacks(stabilityRunnable);
        }
    }

    private void onWeightStabilized(float stableWeight) {
        Log.d(TAG, "Berat STABIL terdeteksi: " + stableWeight + " g");

        // A. Jika berat sangat rendah -> Botol sedang diangkat atau kosong
        if (stableWeight < LIFTED_THRESHOLD_GRAMS) {
            if (!isLifted) {
                isLifted = true;
                Log.d(TAG, "Status: Botol DIANGKAT. Menunggu diletakkan kembali...");
            }
            // Jangan update baseline dulu, kita butuh baseline lama untuk perbandingan
            return;
        }

        // B. Jika sebelumnya diangkat (isLifted == true) dan sekarang diletakkan kembali
        if (isLifted) {
            detectDrinkOrRefill(baselineWeight, stableWeight);
            isLifted = false;
            baselineWeight = stableWeight;
            return;
        }

        // C. Deteksi Minum Tanpa Diangkat (misal pakai sedotan)
        // Jika ada perubahan besar (> drinkThreshold) meski tidak terdeteksi angkatan
        float staticDiff = baselineWeight - stableWeight;
        if (Math.abs(staticDiff) > drinkThreshold) {
            detectDrinkOrRefill(baselineWeight, stableWeight);
        }

        // Selalu update baseline ke berat stabil terbaru
        baselineWeight = stableWeight;
    }

    private void detectDrinkOrRefill(float weightBefore, float weightAfter) {
        float weightDiff = weightBefore - weightAfter;

        if (weightDiff >= drinkThreshold) {
            // TERDETEKSI MINUM
            int drinkAmount = Math.round(weightDiff);
            dbHelper.addDrinkLog(drinkAmount);
            achievementManager.checkAchievements(dbHelper.getTodayTotalIntake(), dailyTargetMl);
            
            updateDailyProgressUI();
            updateLastDrinkTimeUI();
            
            Log.i(TAG, "MINUM TERDETEKSI: " + drinkAmount + " ml");
            Toast.makeText(this, "Anda baru saja minum " + drinkAmount + " ml!", Toast.LENGTH_SHORT).show();
            
            // Sinkronisasi ke server
            ThingsBoardSyncManager.syncData(this, drinkAmount, dbHelper.getTodayTotalIntake());

        } else if (weightDiff <= -refillThreshold) {
            // TERDETEKSI ISI ULANG
            float refillAmount = Math.abs(weightDiff);
            Log.i(TAG, "ISI ULANG TERDETEKSI: " + refillAmount + " ml");
            Toast.makeText(this, "Botol diisi ulang (" + Math.round(refillAmount) + " ml)", Toast.LENGTH_SHORT).show();
        }
    }

}

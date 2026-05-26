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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLE_SMARTBOTTLE";

    // UUID wajib sama persis dengan ESP32
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView tvStatus, tvWeight;
    private Button btnConnect, btnTare;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic targetCharacteristic;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvWeight = findViewById(R.id.tvWeight);
        btnConnect = findViewById(R.id.btnConnect);
        btnTare = findViewById(R.id.btnTare);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        btnConnect.setOnClickListener(v -> {
            if (!isScanning) {
                periksaIzinDanScan();
            }
        });

        // Logika ketika tombol TARE ditekan
        btnTare.setOnClickListener(v -> sendTareCommand());
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
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Bluetooth/GPS tidak aktif!", Toast.LENGTH_SHORT).show();
            return;
        }

        isScanning = true;
        tvStatus.setText("Status: Mencari ESP32...");
        btnConnect.setEnabled(false);
        Log.d(TAG, "Memulai pemindaian BLE...");

        bluetoothLeScanner.startScan(scanCallback);
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
                    tvStatus.setText("Status: Terputus");
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
                String beratData = characteristic.getStringValue(0);
                Log.d(TAG, "Data Masuk: " + beratData);
                runOnUiThread(() -> tvWeight.setText(beratData + " gram"));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
            } catch (SecurityException e) { e.printStackTrace(); }
        }
    }
}

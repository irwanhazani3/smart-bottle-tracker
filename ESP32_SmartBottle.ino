#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <HX711.h>
#include <Preferences.h> // Untuk menyimpan nilai tare

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Pin Configuration (Sesuai dengan pin ESP32 C3 Supermini Anda)
const int LOADCELL_DOUT_PIN = 2;
const int LOADCELL_SCK_PIN = 3;

HX711 scale;
Preferences preferences;
BLEServer *pServer = NULL;
BLECharacteristic *pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;

float calibration_factor = 1093.4;
long tare_offset = 0; // Nilai mentah ADC saat tare
unsigned long lastSendTime = 0;
unsigned long sendInterval = 5000;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("App Connected");
    };
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("App Disconnected");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String value = String(pCharacteristic->getValue().c_str());
        if (value.length() > 0) {
            Serial.print("Command Received: ");
            Serial.println(value);

            if (value == "TARE") {
                // 1. Lakukan tare langsung menggunakan fungsi bawaan library hx711
                scale.tare();

                // 2. Ambil offset baru hasil tare tersebut untuk disimpan ke memori
                tare_offset = scale.get_offset();
                preferences.putLong("tare_val", tare_offset);

                Serial.print("Tare Successful! New Offset Saved: ");
                Serial.println(tare_offset);
            } else if (value.startsWith("INTERVAL=")) {
                sendInterval = value.substring(9).toInt();
                Serial.print("Send Interval updated to: ");
                Serial.println(sendInterval);
            }
        }
    }
};

void setup() {
    Serial.begin(115200);

    // Inisialisasi Preferences dan ambil tare offset terakhir yang disimpan
    preferences.begin("smartbottle", false);
    tare_offset = preferences.getLong("tare_val", 0);
    Serial.print("Loaded Saved Tare Offset: ");
    Serial.println(tare_offset);

    // Inisialisasi Load Cell
    scale.begin(LOADCELL_DOUT_PIN, LOADCELL_SCK_PIN);
    scale.set_scale(calibration_factor);

    // Gunakan tare offset yang dimuat dari memori
    scale.set_offset(tare_offset);

    // Inisialisasi BLE
    BLEDevice::init("ESP32_SmartBottle");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    BLEService *pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
                        CHARACTERISTIC_UUID,
                        BLECharacteristic::PROPERTY_READ |
                        BLECharacteristic::PROPERTY_WRITE |
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
    pCharacteristic->addDescriptor(new BLE2902());
    pCharacteristic->setCallbacks(new MyCallbacks());
    pService->start();

    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
    Serial.println("ESP32 Smart Bottle Ready!");
}

void loop() {
    // Memberi tahu HP jika data berubah (Notifikasi)
    if (deviceConnected) {
        if (millis() - lastSendTime > sendInterval) {
            // Membaca berat bersih (rata-rata dari 5 pembacaan)
            float weight = scale.get_units(5);
            if (weight < 0) weight = 0; // Filter nilai negatif akibat pembacaan kosong

            String weightStr = String(weight, 1);
            pCharacteristic->setValue(weightStr.c_str());
            pCharacteristic->notify();

            lastSendTime = millis();
            Serial.print("Weight Notify Sent: ");
            Serial.println(weightStr);
        }
    }

    // Menangani putusnya koneksi (Re-advertising agar bisa tersambung lagi)
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // Beri waktu stack bluetooth untuk stabil
        pServer->startAdvertising(); // Mulai iklan lagi
        Serial.println("Advertising restarted...");
        oldDeviceConnected = deviceConnected;
    }

    // Menangani koneksi baru
    if (deviceConnected && !oldDeviceConnected) {
        // Lakukan sesuatu saat baru terhubung
        oldDeviceConnected = deviceConnected;
    }

    delay(10);
}

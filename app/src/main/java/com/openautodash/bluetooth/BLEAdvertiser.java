package com.openautodash.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.MainActivity;
import com.openautodash.interfaces.BluetoothKeyCallback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BLEAdvertiser {
    private static final String TAG = "BLEAdvertiser";

    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("3de187e2-5864-435e-b11b-e1e04ab27579");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int RSSI_CONNECT_THRESHOLD = -65;
    private static final int RSSI_DISCONNECT_THRESHOLD = -80;
    private static final int RECONNECTION_DELAY = 5000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final Context context;
    private final BluetoothKeyCallback keyCallback;
    private boolean currentKeyConnected;
    private final MessageHandler messageHandler;
    private final Handler reconnectionHandler = new Handler(Looper.getMainLooper());

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic characteristic;
    private AdvertiseCallback advertiseCallback;

    private final Map<BluetoothDevice, Integer> deviceRssiMap = new ConcurrentHashMap<>();
    private final Set<BluetoothDevice> connectedDevices = new HashSet<>();
    private boolean isAdvertising = false;
    private int retryCount = 0;

    // Interface for handling different types of messages
    public interface MessageHandler {
        void onRssiUpdate(BluetoothDevice device, int rssi);
        void onLocationPin(double latitude, double longitude, String label);
        void onVehicleCommand(String command, String[] params);
        void onTelemetryRequest(BluetoothDevice device);
    }

    public BLEAdvertiser(Context context, BluetoothKeyCallback keyCallback, MessageHandler messageHandler) {
        this.context = context;
        this.keyCallback = keyCallback;
        this.messageHandler = messageHandler;
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        Log.d(TAG, "Starting BLE advertising");

        if (isAdvertising) {
            Log.d(TAG, "Already advertising");
            return;
        }

        if (!initializeBluetooth()) {
            Log.e(TAG, "Failed to initialize Bluetooth");
            return;
        }

        setupAdvertising();
    }

    private boolean initializeBluetooth() {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "Bluetooth manager not available");
            return false;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            return false;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising not supported");
            return false;
        }

        return true;
    }

    @SuppressLint("MissingPermission")
    private void setupAdvertising() {
        if (!checkPermissions()) {
            Log.e(TAG, "Missing required permissions");
            return;
        }

        bluetoothAdapter.setName("OpenAutoDash");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid.fromString(SERVICE_UUID.toString()))
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "Advertising started successfully");
                isAdvertising = true;
                retryCount = 0;
                setupGattServer();
            }

            @Override
            public void onStartFailure(int errorCode) {
                isAdvertising = false;
                String errorMessage = getAdvertiseErrorMessage(errorCode);
                Log.e(TAG, "Failed to start advertising: " + errorMessage);
                retryAdvertising();
            }
        };

        try {
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (Exception e) {
            Log.e(TAG, "Error starting advertising", e);
            retryAdvertising();
        }
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer() {
        if (gattServer != null) {
            gattServer.close();
        }

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "Failed to create GATT server");
            return;
        }

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        characteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ |
                        BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY |
                        BluetoothGattCharacteristic.PROPERTY_INDICATE,
                BluetoothGattCharacteristic.PERMISSION_READ |
                        BluetoothGattCharacteristic.PERMISSION_WRITE);

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ |
                        BluetoothGattDescriptor.PERMISSION_WRITE);
        characteristic.addDescriptor(descriptor);

        service.addCharacteristic(characteristic);
        if (!gattServer.addService(service)) {
            Log.e(TAG, "Failed to add service to GATT server");
            retryAdvertising();
        }
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Error in connection state change: " + status);
                handleDisconnection(device);
                return;
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                handleConnection(device);
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                handleDisconnection(device);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                                int offset, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                        offset, characteristic.getValue());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            offset, value);
                }
                handleIncomingMessage(device, new String(value));
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId,
                            BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }
        }
    };

    private void handleIncomingMessage(BluetoothDevice device, String message) {
        Log.d(TAG, "Received message: " + message);
        String[] parts = message.split(":", 2);
        if (parts.length != 2) return;

        String type = parts[0];
        String data = parts[1];

        switch (type) {
            case "RSSI":
                try {
                    int rssi = Integer.parseInt(data);
                    updateDeviceRssi(device, rssi);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid RSSI value: " + data);
                }
                break;

            case "PIN":
                handleLocationPin(data);
                break;

            case "CMD":
                handleVehicleCommand(data);
                break;

            case "TELEMETRY":
                messageHandler.onTelemetryRequest(device);
                break;
        }
    }

    private void updateDeviceRssi(BluetoothDevice device, int rssi) {
        deviceRssiMap.put(device, rssi);
        messageHandler.onRssiUpdate(device, rssi);

        if (rssi > RSSI_CONNECT_THRESHOLD && !currentKeyConnected) {  // Signal better than -65
            currentKeyConnected = true;
            keyCallback.onConnected();
        } else if (rssi < RSSI_DISCONNECT_THRESHOLD && currentKeyConnected) {  // Signal worse than -95
            currentKeyConnected = false;
            keyCallback.onDisconnected();
        }
    }

    private void handleLocationPin(String data) {
        String[] parts = data.split(",");
        if (parts.length != 3) return;

        try {
            double latitude = Double.parseDouble(parts[0]);
            double longitude = Double.parseDouble(parts[1]);
            String label = parts[2];
            messageHandler.onLocationPin(latitude, longitude, label);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid location data: " + data);
        }
    }

    private void handleVehicleCommand(String data) {
        String[] parts = data.split(",");
        if (parts.length < 1) return;

        String command = parts[0];
        String[] params = new String[parts.length - 1];
        System.arraycopy(parts, 1, params, 0, parts.length - 1);
        messageHandler.onVehicleCommand(command, params);
    }

    private void handleConnection(BluetoothDevice device) {
        Log.d(TAG, "Device connected: " + device.getAddress());
    }

    private void handleDisconnection(BluetoothDevice device) {
        Log.d(TAG, "Device disconnected: " + device.getAddress());

        keyCallback.onDisconnected();
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        if (characteristic == null || gattServer == null || connectedDevices.isEmpty()) {
            return;
        }

        characteristic.setValue(message.getBytes());
        for (BluetoothDevice device : connectedDevices) {
            try {
                gattServer.notifyCharacteristicChanged(device, characteristic, false);
            } catch (Exception e) {
                Log.e(TAG, "Error sending message to device: " + device.getAddress(), e);
                handleDisconnection(device);
            }
        }
    }

    public void sendTelemetryData(Map<String, String> telemetryData) {
        StringBuilder message = new StringBuilder("TELEMETRY:");
        for (Map.Entry<String, String> entry : telemetryData.entrySet()) {
            message.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append(",");
        }
        // Remove trailing comma
        if (message.length() > 0 && message.charAt(message.length() - 1) == ',') {
            message.setLength(message.length() - 1);
        }
        sendMessage(message.toString());
    }

    private void retryAdvertising() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Log.d(TAG, "Retrying advertising, attempt " + retryCount);
            reconnectionHandler.postDelayed(this::setupAdvertising, RECONNECTION_DELAY);
        } else {
            Log.e(TAG, "Max retry attempts reached");
            retryCount = 0;
        }
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }
    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        Log.d(TAG, "Stopping BLE advertising");

        if (advertiser != null && isAdvertising) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping advertising", e);
            }
        }

        reconnectionHandler.removeCallbacksAndMessages(null);

        if (gattServer != null) {
            try {
                gattServer.close();
                gattServer = null;
            } catch (Exception e) {
                Log.e(TAG, "Error closing GATT server", e);
            }
        }

        connectedDevices.clear();
        deviceRssiMap.clear();
        isAdvertising = false;
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    public Set<BluetoothDevice> getConnectedDevices() {
        return new HashSet<>(connectedDevices);
    }

    public Integer getDeviceRssi(BluetoothDevice device) {
        return deviceRssiMap.get(device);
    }

    private String getAdvertiseErrorMessage(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "ADVERTISE_FAILED_ALREADY_STARTED";
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "ADVERTISE_FAILED_DATA_TOO_LARGE";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "ADVERTISE_FAILED_INTERNAL_ERROR";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
            default:
                return "Unknown error: " + errorCode;
        }
    }
}
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

import com.openautodash.interfaces.BluetoothKeyCallback;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BLEAdvertiser {
    private static final String TAG = "BLEAdvertiser";

    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("3de187e2-5864-435e-b11b-e1e04ab27579");

    private final BluetoothKeyCallback callback;
    private final Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic characteristic;
    private AdvertiseCallback advertiseCallback;

    private final Handler reconnectionHandler = new Handler(Looper.getMainLooper());
    private final Set<BluetoothDevice> connectedDevices = new HashSet<>();
    private boolean isAdvertising = false;
    private static final int RECONNECTION_DELAY = 5000; // 5 seconds
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private int retryCount = 0;
    private static final int RSSI_THRESHOLD = -85; // RSSI threshold in dBm

    public BLEAdvertiser(BluetoothKeyCallback callback) {
        this.callback = callback;
        this.context = (Context) callback;
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising(Context context) {
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

        bluetoothAdapter.setName("Ford Fusion 01");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        ParcelUuid pUuid = ParcelUuid.fromString(SERVICE_UUID.toString());
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(pUuid)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.d(TAG, "Advertising started successfully");
                isAdvertising = true;
                retryCount = 0;
                setupGattServer();
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
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

    private void retryAdvertising() {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Log.d(TAG, "Retrying advertising, attempt " + retryCount);
            reconnectionHandler.postDelayed(() -> {
                if (!isAdvertising) {
                    setupAdvertising();
                }
            }, RECONNECTION_DELAY);
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
    private void setupGattServer() {
        if (gattServer != null) {
            gattServer.close();
        }

        try {
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
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ |
                            BluetoothGattCharacteristic.PERMISSION_WRITE);

            service.addCharacteristic(characteristic);

            if (!gattServer.addService(service)) {
                Log.e(TAG, "Failed to add service to GATT server");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up GATT server", e);
            retryAdvertising();
        }
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);

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
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                 boolean responseNeeded, int offset, byte[] value) {
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                characteristic.setValue(value);
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                            offset, value);
                }
                String receivedData = new String(value);
                Log.d(TAG, "Received data: " + receivedData);
                callback.onDataReceived(receivedData);
            }
        }
    };

    private void handleConnection(BluetoothDevice device) {
        Log.d(TAG, "Device connected: " + device.getAddress());
        connectedDevices.add(device);
        startConnectionMonitoring(device);
        if (connectedDevices.size() == 1) { // First device connected
            callback.onConnected();
        }
    }

    private void handleDisconnection(BluetoothDevice device) {
        Log.d(TAG, "Device disconnected: " + device.getAddress());
        connectedDevices.remove(device);
        reconnectionHandler.removeCallbacksAndMessages(device);

        if (connectedDevices.isEmpty()) {
            callback.onDisconnected();
        }
    }

    private void startConnectionMonitoring(BluetoothDevice device) {
        Runnable monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (connectedDevices.contains(device)) {
                    sendPing(device);
                    reconnectionHandler.postDelayed(this, 5000);
                }
            }
        };
        reconnectionHandler.postDelayed(monitorRunnable, 5000);
    }

    @SuppressLint("MissingPermission")
    private void sendPing(BluetoothDevice device) {
        try {
            if (characteristic != null && gattServer != null) {
                characteristic.setValue("ping".getBytes());
                gattServer.notifyCharacteristicChanged(device, characteristic, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending ping", e);
            handleDisconnection(device);
        }
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
        connectedDevices.clear();

        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }

        isAdvertising = false;
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        if (characteristic != null && gattServer != null && !connectedDevices.isEmpty()) {
            characteristic.setValue(message.getBytes());
            for (BluetoothDevice device : connectedDevices) {
                try {
                    gattServer.notifyCharacteristicChanged(device, characteristic, false);
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message to device: " + device.getAddress(), e);
                }
            }
        }
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
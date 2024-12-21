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
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.openautodash.interfaces.BluetoothKeyCallback;

import java.util.UUID;

public class BLEAdvertiser {
    private static final String TAG = "BLEAdvertiser";

    private BluetoothKeyCallback callback;

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic characteristic;

    // UUIDs for your service and characteristic
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("3de187e2-5864-435e-b11b-e1e04ab27579");

    public BLEAdvertiser(BluetoothKeyCallback callback) {
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void startAdvertising(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        bluetoothAdapter.setName("Ford Fusion 01");

        ParcelUuid pUuid = ParcelUuid.fromString(SERVICE_UUID.toString());
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(pUuid)
                .build();

        // Check data size for debugging (optional)
        int dataSize = data.toString().length();
        Log.d(TAG, "Advertisement data size: " + dataSize);
        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                // Advertising started successfully
                Log.d(TAG, "onStartSuccess");
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                // Advertising failed to start
                Log.e(TAG, "onStartFailure, code: " + errorCode);
            }
        };

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        advertiser.startAdvertising(settings, data, advertiseCallback);

        // Setup GATT server
        setupGattServer(context, bluetoothManager);
    }

    @SuppressLint("MissingPermission")
    private void setupGattServer(Context context, BluetoothManager bluetoothManager) {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);

        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        characteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(characteristic);
        gattServer.addService(service);
    }

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothGattServer.STATE_CONNECTED) {
                Log.d(TAG, "Device connected: " + device.getAddress());
                callback.onConnected();
            } else if (newState == BluetoothGattServer.STATE_DISCONNECTED) {
                Log.d(TAG, "Device disconnected: " + device.getAddress());
                callback.onDisconnected();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                characteristic.setValue(value);
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
                Log.d(TAG, "Characteristic written: " + new String(value));
                callback.onDataReceived(new String(value));
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void stopAdvertising(Context context) {
        if (advertiser != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            advertiser.stopAdvertising(advertiseCallback);
            Log.d(TAG, "stopAdvertising");
        }
    }

    @SuppressLint("MissingPermission")
    public void sendMessage(String message) {
        if (characteristic != null) {
            characteristic.setValue(message.getBytes());
            gattServer.notifyCharacteristicChanged(null, characteristic, false);
        }
    }
}

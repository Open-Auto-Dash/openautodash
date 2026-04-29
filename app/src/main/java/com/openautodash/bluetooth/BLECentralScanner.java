package com.openautodash.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BLECentralScanner {
    private static final String TAG = "BLECentralScanner";
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("3de187e2-5864-435e-b11b-e1e04ab27579");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final int RSSI_CONNECT_THRESHOLD = -95;
    private static final int RSSI_DISCONNECT_THRESHOLD = -80;
    private static final int RSSI_DISCONNECT_CONSECUTIVE_READS = 4;
    private static final long SCAN_WATCHDOG_MS = 15000;
    private static final long DEBUG_EPOCH_MS = SystemClock.elapsedRealtime();

    public interface MessageHandler {
        void onConnectionStateChanged(boolean connected);
        void onRssiUpdate(BluetoothDevice device, int rssi);
        void onLocationPin(double latitude, double longitude, String label, String placeId);
        void onVehicleCommand(String command, String[] params);
        void onTelemetryRequest(BluetoothDevice device);
    }

    private final Context context;
    private final MessageHandler messageHandler;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private boolean scanning;
    private boolean connected;
    private int lowRssiReadCount;
    private BluetoothDevice currentDevice;
    private final Runnable retryStart = this::start;
    private final Map<String, ChunkBuffer> chunkBuffers = new HashMap<>();
    private final Runnable scanWatchdog = () -> {
        if (!scanning || connected) return;
        Log.w(TAG, dbg("Scan watchdog fired: no result yet, restarting scan"));
        restartScan();
    };

    public BLECentralScanner(Context context, MessageHandler handler) {
        this.context = context;
        this.messageHandler = handler;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) adapter = manager.getAdapter();
    }

    @SuppressLint("MissingPermission")
    public void start() {
        Log.d(TAG, dbg("start() called connected=" + connected + " scanning=" + scanning));
        if (connected || scanning) return;
        if (adapter == null) {
            Log.e(TAG, "Bluetooth adapter is null");
            handler.postDelayed(retryStart, 5000);
            return;
        }
        if (!adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter disabled");
            handler.postDelayed(retryStart, 5000);
            return;
        }
        if (!hasScanPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission");
            handler.postDelayed(retryStart, 5000);
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            handler.postDelayed(retryStart, 5000);
            return;
        }

        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanning = true;
        Log.d(TAG, dbg("Starting BLE scan for service " + SERVICE_UUID));
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        handler.removeCallbacks(scanWatchdog);
        handler.postDelayed(scanWatchdog, SCAN_WATCHDOG_MS);
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        handler.removeCallbacks(retryStart);
        handler.removeCallbacks(scanWatchdog);
        if (scanner != null && scanning && hasScanPermission()) scanner.stopScan(scanCallback);
        scanning = false;
        handler.removeCallbacks(rssiLoop);
        if (gatt != null) {
            if (hasConnectPermission()) gatt.disconnect();
            gatt.close();
            gatt = null;
        }
        characteristic = null;
        connected = false;
        lowRssiReadCount = 0;
        currentDevice = null;
        chunkBuffers.clear();
    }

    @SuppressLint("MissingPermission")
    public void sendTelemetryData(java.util.Map<String, String> telemetryData) {
        if (!connected || characteristic == null || gatt == null) return;
        StringBuilder builder = new StringBuilder("TELEMETRY:");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : telemetryData.entrySet()) {
            if (!first) builder.append(",");
            first = false;
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        characteristic.setValue(builder.toString().getBytes());
        gatt.writeCharacteristic(characteristic);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            int rssi = result.getRssi();
            Log.d(TAG, dbg("Found advertiser " + result.getDevice().getAddress() + " RSSI=" + rssi));
            if (rssi >= RSSI_CONNECT_THRESHOLD && !connected) {
                BluetoothDevice device = result.getDevice();
                currentDevice = device;
                Log.d(TAG, dbg("Connecting to advertiser " + device.getAddress()));
                if (scanning && scanner != null && hasScanPermission()) scanner.stopScan(this);
                scanning = false;
                handler.removeCallbacks(scanWatchdog);
                gatt = device.connectGatt(context, false, gattCallback);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            Log.e(TAG, dbg("Scan failed errorCode=" + errorCode));
            handler.removeCallbacks(scanWatchdog);
            handler.postDelayed(retryStart, 3000);
        }
    };

    private final Runnable rssiLoop = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (gatt == null || !connected) return;
            if (hasConnectPermission()) {
                Log.d(TAG, dbg("rssiLoop tick -> readRemoteRssi"));
                gatt.readRemoteRssi();
            }
            handler.postDelayed(this, 2000);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, dbg("GATT disconnected status=" + status + " state=" + newState));
                connected = false;
                lowRssiReadCount = 0;
                messageHandler.onConnectionStateChanged(false);
                handler.removeCallbacks(rssiLoop);
                if (gatt != null) {
                    gatt.close();
                    gatt = null;
                }
                characteristic = null;
                currentDevice = null;
                chunkBuffers.clear();
                handler.postDelayed(BLECentralScanner.this::start, 3000);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, dbg("GATT connected, discovering services"));
                connected = true;
                lowRssiReadCount = 0;
                messageHandler.onConnectionStateChanged(true);
                g.discoverServices();
                handler.post(rssiLoop);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed status=" + status);
                return;
            }
            Log.d(TAG, dbg("Services discovered"));
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) return;
            characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) return;
            g.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            handleMessage(new String(c.getValue()));
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt g, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && currentDevice != null) {
                messageHandler.onRssiUpdate(currentDevice, rssi);
                if (rssi <= RSSI_DISCONNECT_THRESHOLD) {
                    lowRssiReadCount++;
                } else {
                    lowRssiReadCount = 0;
                }
                if (lowRssiReadCount >= RSSI_DISCONNECT_CONSECUTIVE_READS) {
                    Log.w(TAG, dbg("Disconnecting after sustained low RSSI: " + rssi + " count=" + lowRssiReadCount));
                    stop();
                    handler.postDelayed(BLECentralScanner.this::start, 3000);
                }
            }
        }
    };

    private String dbg(String msg) {
        return "[diag t+" + (SystemClock.elapsedRealtime() - DEBUG_EPOCH_MS) + "ms] " + msg;
    }

    private void handleMessage(String message) {
        String[] parts = message.split(":", 2);
        if (parts.length != 2) return;
        String type = parts[0];
        String data = parts[1];
        switch (type) {
            case "CHUNK":
                handleChunk(data);
                break;
            case "PIN":
                String[] pin = data.split(",");
                if (pin.length >= 3) {
                    try {
                        double lat = Double.parseDouble(pin[0]);
                        double lng = Double.parseDouble(pin[1]);
                        String label = pin[2];
                        String placeId = pin.length > 3 ? pin[3] : null;
                        messageHandler.onLocationPin(lat, lng, label, placeId);
                    } catch (Exception ignored) {}
                }
                break;
            case "CMD":
                String[] cmd = data.split(",", 2);
                if (cmd.length > 0) {
                    String[] params = cmd.length > 1 ? new String[]{cmd[1]} : new String[0];
                    messageHandler.onVehicleCommand(cmd[0], params);
                }
                break;
            case "TELEMETRY":
                if (currentDevice != null) messageHandler.onTelemetryRequest(currentDevice);
                break;
        }
    }

    private boolean hasScanPermission() {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void handleChunk(String data) {
        String[] parts = data.split(":", 4);
        if (parts.length != 4) return;
        String chunkId = parts[0];

        int index;
        int total;
        try {
            index = Integer.parseInt(parts[1]);
            total = Integer.parseInt(parts[2]);
        } catch (Exception ignored) {
            return;
        }
        if (index <= 0 || total <= 0 || index > total) return;

        ChunkBuffer buffer = chunkBuffers.get(chunkId);
        if (buffer == null || buffer.totalParts != total) {
            buffer = new ChunkBuffer(total);
            chunkBuffers.put(chunkId, buffer);
        }
        buffer.parts[index - 1] = parts[3];

        if (!buffer.isComplete()) return;

        StringBuilder full = new StringBuilder();
        for (String part : buffer.parts) {
            full.append(part);
        }
        chunkBuffers.remove(chunkId);
        handleMessage(full.toString());
    }

    private static class ChunkBuffer {
        final String[] parts;
        final int totalParts;

        ChunkBuffer(int totalParts) {
            this.totalParts = totalParts;
            this.parts = new String[totalParts];
        }

        boolean isComplete() {
            for (String part : parts) {
                if (part == null) return false;
            }
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    private void restartScan() {
        if (scanner != null && scanning && hasScanPermission()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
        handler.postDelayed(this::start, 250);
    }
}

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
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.openautodash.pairing.CryptoUtils;

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
    private static final long RSSI_POLL_INTERVAL_MS = 5000;
    private static final long SCAN_WATCHDOG_MS = 15000;
    private static final int REQUESTED_MTU = 247;

    public interface MessageHandler {
        void onConnectionStateChanged(boolean connected);
        boolean onAuthResponse(String phoneId, String challenge, String mac);
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
    private boolean notificationsEnabled;
    private boolean authorized;
    private int lowRssiReadCount;
    private BluetoothDevice currentDevice;
    private String lastPinId;
    private String authChallenge;
    private final Runnable retryStart = this::start;
    private final Map<String, ChunkBuffer> chunkBuffers = new HashMap<>();
    private final Runnable scanWatchdog = () -> {
        if (!scanning || connected) return;
        Log.w(TAG, "Scan watchdog fired: no result yet, restarting scan");
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
        Log.d(TAG, "Starting BLE scan for service " + SERVICE_UUID);
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        handler.removeCallbacks(scanWatchdog);
        handler.postDelayed(scanWatchdog, SCAN_WATCHDOG_MS);
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        boolean wasConnected = connected;
        handler.removeCallbacks(retryStart);
        handler.removeCallbacks(scanWatchdog);
        if (scanner != null && scanning && hasScanPermission()) scanner.stopScan(scanCallback);
        scanning = false;
        handler.removeCallbacks(rssiLoop);
        if (gatt != null) {
            if (hasConnectPermission()) {
                gatt.disconnect();
            }
            gatt.close();
            gatt = null;
        }
        characteristic = null;
        connected = false;
        notificationsEnabled = false;
        authorized = false;
        lowRssiReadCount = 0;
        currentDevice = null;
        authChallenge = null;
        chunkBuffers.clear();
        if (wasConnected) messageHandler.onConnectionStateChanged(false);
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

    @SuppressLint("MissingPermission")
    private void sendMessage(String message) {
        if (!connected || characteristic == null || gatt == null || !hasConnectPermission()) return;
        characteristic.setValue(message.getBytes());
        boolean started = gatt.writeCharacteristic(characteristic);
        Log.d(TAG, "write message type=" + message.split(":", 2)[0] + " started=" + started);
    }

    public void requestAuthorization() {
        authorized = false;
        authChallenge = CryptoUtils.randomId();
        sendMessage("AUTH_CHALLENGE:" + authChallenge);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            int rssi = result.getRssi();
            if (rssi >= RSSI_CONNECT_THRESHOLD && !connected) {
                BluetoothDevice device = result.getDevice();
                currentDevice = device;
                Log.d(TAG, "Connecting to advertiser " + device.getAddress() + " RSSI=" + rssi);
                if (scanning && scanner != null && hasScanPermission()) scanner.stopScan(this);
                scanning = false;
                handler.removeCallbacks(scanWatchdog);
                gatt = device.connectGatt(context, false, gattCallback);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            Log.e(TAG, "Scan failed errorCode=" + errorCode);
            handler.removeCallbacks(scanWatchdog);
            handler.postDelayed(retryStart, 3000);
        }
    };

    private final Runnable rssiLoop = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (!connected || gatt == null || !hasConnectPermission()) return;
            gatt.readRemoteRssi();
            handler.postDelayed(this, RSSI_POLL_INTERVAL_MS);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT disconnected status=" + status + " state=" + newState);
                connected = false;
                notificationsEnabled = false;
                authorized = false;
                lowRssiReadCount = 0;
                messageHandler.onConnectionStateChanged(false);
                handler.removeCallbacks(rssiLoop);
                if (gatt != null) {
                    gatt.close();
                    gatt = null;
                }
                characteristic = null;
                currentDevice = null;
                authChallenge = null;
                chunkBuffers.clear();
                handler.postDelayed(BLECentralScanner.this::start, 3000);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT connected, discovering services");
                connected = true;
                lowRssiReadCount = 0;
                g.discoverServices();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed status=" + status);
                return;
            }
            Log.d(TAG, "Services discovered");
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                Log.e(TAG, "Expected service missing: " + SERVICE_UUID);
                return;
            }
            characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            if (characteristic == null) {
                Log.e(TAG, "Expected characteristic missing: " + CHARACTERISTIC_UUID);
                return;
            }
            if (hasConnectPermission() && g.requestMtu(REQUESTED_MTU)) {
                Log.d(TAG, "MTU request started: " + REQUESTED_MTU);
                handler.postDelayed(() -> enableNotifications(g), 1500);
                return;
            }
            enableNotifications(g);
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            Log.d(TAG, "MTU changed mtu=" + mtu + " status=" + status);
            enableNotifications(g);
        }

        @SuppressLint("MissingPermission")
        private void enableNotifications(BluetoothGatt g) {
            if (notificationsEnabled || characteristic == null) return;
            g.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                notificationsEnabled = true;
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean writeStarted = g.writeDescriptor(descriptor);
                Log.d(TAG, "CCCD write requested started=" + writeStarted);
                if (!writeStarted) notificationsEnabled = false;
            } else {
                Log.e(TAG, "CCCD descriptor missing: " + CLIENT_CHARACTERISTIC_CONFIG);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            Log.d(TAG, "Descriptor write callback uuid=" + descriptor.getUuid() + " status=" + status);
            if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                requestAuthorization();
                handler.postDelayed(rssiLoop, RSSI_POLL_INTERVAL_MS);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            String message = new String(c.getValue());
            Log.d(TAG, "notification received type=" + message.split(":", 2)[0] + " length=" + message.length());
            handleMessage(message);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            String message = new String(value);
            Log.d(TAG, "notification received type=" + message.split(":", 2)[0] + " length=" + message.length());
            handleMessage(message);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            Log.d(TAG, "write callback uuid=" + c.getUuid() + " status=" + status);
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
                    Log.w(TAG, "Disconnecting after sustained low RSSI: " + rssi + " count=" + lowRssiReadCount);
                    stop();
                    handler.postDelayed(BLECentralScanner.this::start, 3000);
                }
            }
        }
    };

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
                if (!authorized) {
                    Log.w(TAG, "Ignoring PIN from unauthorized BLE device");
                    return;
                }
                String[] pin = data.split(",");
                if (pin.length >= 3) {
                    try {
                        double lat = Double.parseDouble(pin[0]);
                        double lng = Double.parseDouble(pin[1]);
                        String label = pin[2];
                        String placeId = pin.length > 3 ? pin[3] : null;
                        String pinId = pin.length > 4 ? pin[4] : "";
                        if (pinId.isEmpty() || !pinId.equals(lastPinId)) {
                            lastPinId = pinId;
                            messageHandler.onLocationPin(lat, lng, label, placeId);
                        }
                        handler.post(() -> sendMessage("ACK:PIN," + pinId));
                    } catch (Exception e) {
                        Log.e(TAG, "Invalid PIN payload: " + data, e);
                    }
                } else {
                    Log.e(TAG, "Malformed PIN payload: " + data);
                }
                break;
            case "CMD":
                String[] cmd = data.split(",", 2);
                if (cmd.length > 0) {
                    if (!authorized && !"PAIR_HELLO".equalsIgnoreCase(cmd[0])) {
                        Log.w(TAG, "Ignoring command from unauthorized BLE device: " + cmd[0]);
                        return;
                    }
                    String[] params = cmd.length > 1 ? new String[]{cmd[1]} : new String[0];
                    messageHandler.onVehicleCommand(cmd[0], params);
                }
                break;
            case "AUTH_RESPONSE":
                String[] auth = data.split(",", 2);
                if (auth.length == 2 && authChallenge != null) {
                    authorized = messageHandler.onAuthResponse(auth[0], authChallenge, auth[1]);
                    sendMessage(authorized ? "AUTH_RESULT:OK" : "AUTH_RESULT:FAIL");
                }
                break;
            case "AUTH_CHECK_REQUEST":
                requestAuthorization();
                break;
            case "AUTH_LOGOUT":
                authorized = false;
                messageHandler.onConnectionStateChanged(false);
                break;
            case "TELEMETRY":
                if (!authorized) return;
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
        Log.d(TAG, "Chunk received id=" + chunkId + " index=" + index + "/" + total + " length=" + parts[3].length());

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
        Log.d(TAG, "Chunk complete id=" + chunkId + " messageType=" + full.toString().split(":", 2)[0] + " length=" + full.length());
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

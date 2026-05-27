package com.openautodash.utilities;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class UsbSerialManager {
    private static final String TAG = "UsbSerialManager";
    private static final String ACTION_USB_PERMISSION = "com.openautodash.USB_PERMISSION";

    private final Context context;
    private final UsbManager usbManager;
    private final UsbPermissionListener listener;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint outEndpoint;
    private boolean receiverRegistered;

    public UsbSerialManager(Context context, UsbPermissionListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
    }

    public boolean connectFirstAvailable() {
        if (isConnected()) return true;
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice device : devices.values()) {
            UsbInterface candidate = findSerialInterface(device);
            if (candidate == null) continue;
            usbDevice = device;
            usbInterface = candidate;
            if (usbManager.hasPermission(device)) {
                return openUsbDevice();
            }
            requestUsbPermission(device);
            return false;
        }
        if (listener != null) listener.onCommunicationError("No USB serial device found");
        return false;
    }

    public boolean writeLine(String line) {
        if (!isConnected() && !connectFirstAvailable()) return false;
        byte[] data = (line + "\n").getBytes(StandardCharsets.UTF_8);
        int written = usbConnection.bulkTransfer(outEndpoint, data, data.length, 1000);
        if (written == data.length) return true;
        Log.w(TAG, "USB write failed, written=" + written + " expected=" + data.length);
        closeUsbDevice();
        return false;
    }

    public boolean isConnected() {
        return usbConnection != null && outEndpoint != null;
    }

    private void requestUsbPermission(UsbDevice device) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
        );
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(usbPermissionReceiver, filter);
            }
            receiverRegistered = true;
        }
        usbManager.requestPermission(device, permissionIntent);
    }

    private boolean openUsbDevice() {
        if (usbDevice == null || usbInterface == null) return false;
        usbConnection = usbManager.openDevice(usbDevice);
        if (usbConnection == null || !usbConnection.claimInterface(usbInterface, true)) {
            closeUsbDevice();
            if (listener != null) listener.onCommunicationError("Failed to open USB serial device");
            return false;
        }

        outEndpoint = findOutEndpoint(usbInterface);
        if (outEndpoint == null) {
            closeUsbDevice();
            if (listener != null) listener.onCommunicationError("USB serial output endpoint not found");
            return false;
        }

        if (listener != null) listener.onPermissionGranted();
        Log.d(TAG, "USB serial connected: " + usbDevice.getDeviceName());
        return true;
    }

    public void closeUsbDevice() {
        if (usbConnection != null && usbInterface != null) {
            try {
                usbConnection.releaseInterface(usbInterface);
            } catch (Exception ignored) {}
        }
        if (usbConnection != null) usbConnection.close();
        usbConnection = null;
        outEndpoint = null;
    }

    public void destroy() {
        closeUsbDevice();
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(usbPermissionReceiver);
            } catch (Exception ignored) {}
            receiverRegistered = false;
        }
    }

    private UsbInterface findSerialInterface(UsbDevice device) {
        UsbInterface fallback = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (findOutEndpoint(iface) == null) continue;
            if (iface.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) return iface;
            if (fallback == null) fallback = iface;
        }
        return fallback;
    }

    private UsbEndpoint findOutEndpoint(UsbInterface iface) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = iface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                return endpoint;
            }
        }
        return null;
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                openUsbDevice();
            } else if (listener != null) {
                listener.onPermissionDenied();
            }
        }
    };

    public interface UsbPermissionListener {
        void onPermissionGranted();

        void onPermissionDenied();

        void onCommunicationError(String errorMessage);
    }
}

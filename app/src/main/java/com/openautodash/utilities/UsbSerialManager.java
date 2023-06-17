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

public class UsbSerialManager {
    private static final String ACTION_USB_PERMISSION = "com.example.yourapp.USB_PERMISSION";
    private Context context;
    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint usbEndpoint;
    private UsbPermissionListener permissionListener;

    public UsbSerialManager(Context context, UsbPermissionListener listener) {
        this.context = context;
        this.permissionListener = listener;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public void requestUsbPermission(UsbDevice device) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(usbPermissionReceiver, filter);
        usbDevice = device;
        usbManager.requestPermission(usbDevice, permissionIntent);
    }

    public void openUsbDevice() {
        usbConnection = usbManager.openDevice(usbDevice);
        if (usbConnection != null && usbConnection.claimInterface(usbInterface, true)) {
            UsbEndpoint endpointIn = null;
            UsbEndpoint endpointOut = null;
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        endpointIn = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        endpointOut = endpoint;
                    }
                }
            }
            if (endpointIn != null && endpointOut != null) {
                usbEndpoint = endpointOut;
                permissionListener.onPermissionGranted();
            } else {
                permissionListener.onCommunicationError("Communication endpoints not found.");
            }
        } else {
            permissionListener.onCommunicationError("Failed to open or claim the USB device.");
        }
    }

    public void closeUsbDevice() {
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
        if (usbDevice != null) {
            usbDevice = null;
        }
        context.unregisterReceiver(usbPermissionReceiver);
    }

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openUsbDevice();
                    } else {
                        permissionListener.onPermissionDenied();
                    }
                }
                context.unregisterReceiver(this);
            }
        }
    };

    public interface UsbPermissionListener {
        void onPermissionGranted();

        void onPermissionDenied();

        void onCommunicationError(String errorMessage);
    }
}

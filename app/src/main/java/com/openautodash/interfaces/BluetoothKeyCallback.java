package com.openautodash.interfaces;

public interface BluetoothKeyCallback {
    void onConnected();
    void onDisconnected();
    void onDataReceived(String data);
}

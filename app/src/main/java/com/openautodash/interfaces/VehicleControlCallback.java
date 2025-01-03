package com.openautodash.interfaces;

public interface VehicleControlCallback {
    void onLockCommand();
    void onUnlockCommand();
    void onStartCommand();
    void onStopCommand();
    void onLightsCommand(boolean on);
}

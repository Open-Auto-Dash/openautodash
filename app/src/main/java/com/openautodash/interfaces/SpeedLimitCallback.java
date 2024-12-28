package com.openautodash.interfaces;

public interface SpeedLimitCallback {
    void onSpeedLimitUpdated(String speedLimit);
    void onError(String error);
}

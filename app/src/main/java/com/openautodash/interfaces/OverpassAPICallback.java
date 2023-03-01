package com.openautodash.interfaces;

public interface OverpassAPICallback {
    void onComplete(String result);
    void speedLimitUpdated(String speedLimit);
}

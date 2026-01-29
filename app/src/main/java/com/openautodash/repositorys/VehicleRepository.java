package com.openautodash.repositorys;

import android.app.Application;
import android.content.Context;
import android.location.Location;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.database.DatabaseRepository;
import com.openautodash.database.TelemetryLog;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.object.Weather;

import java.util.List;

public class VehicleRepository {
    private static final String TAG = "VehicleRepository";
    private static VehicleRepository instance;

    // Dependencies
    private final LocalSettings localSettings;
    private final DatabaseRepository databaseRepository;

    // --- Live Data Sources (The Single Source of Truth) ---

    // 1. Location & Weather
    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>();
    private final MutableLiveData<Weather> currentWeather = new MutableLiveData<>();

    // 2. Display Settings (Brightness & Theme)
    private final MutableLiveData<Float> screenBrightness = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNightMode = new MutableLiveData<>();

    // 3. Connectivity
    private final MutableLiveData<Boolean> isBluetoothConnected = new MutableLiveData<>();
    private final MutableLiveData<NetworkStatus> networkStatus = new MutableLiveData<>();

    // 4. Vehicle Telemetry (Sensors & CAN)
    private final MutableLiveData<VehicleTelemetry> liveTelemetry = new MutableLiveData<>();

    // Internal State for Brightness Calculation
    private final int[] brightnessBuffer = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    private long lastBrightnessTime = 0;
    private int[] brightnessThresholds; // From LocalSettings
    private int nightModeThreshold;     // From LocalSettings

    // Internal State for Accelerometer
    private double ax, ay, az;

    private VehicleRepository(Context context) {
        // Initialize local persistence helpers
        this.localSettings = new LocalSettings(context);
        this.databaseRepository = new DatabaseRepository((Application) context.getApplicationContext());

        // Load initial settings into memory
        this.brightnessThresholds = localSettings.getBrightnessSetting();
        this.nightModeThreshold = localSettings.getNightModeSetPoint();

        // Initialize default states to prevent null pointer exceptions in UI
        isNightMode.setValue(localSettings.getIsNight());
        networkStatus.setValue(new NetworkStatus(0, 0, false));
        liveTelemetry.setValue(new VehicleTelemetry());
    }

    public static synchronized VehicleRepository getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleRepository(context);
        }
        return instance;
    }

    // ============================================================================================
    // REGION: Brightness & Display Logic
    // ============================================================================================

    /**
     * Ingests raw lux values from the light sensor.
     * Applies a smoothing buffer and calculates the target screen brightness (0.0 - 1.0).
     */
    public void updateAmbientLight(float rawLux) {
        // Throttle updates to avoid flickering, similar to previous implementation
        if (System.currentTimeMillis() - lastBrightnessTime <= 1000) {
            return;
        }
        lastBrightnessTime = System.currentTimeMillis();

        // Shift buffer values
        for (int i = brightnessBuffer.length - 1; i > 0; i--) {
            brightnessBuffer[i] = brightnessBuffer[i - 1];
        }
        brightnessBuffer[0] = (int) rawLux;

        // Calculate average
        int totalBuffer = 0;
        for (int value : brightnessBuffer) {
            totalBuffer += value;
        }
        int avgBrightness = totalBuffer / brightnessBuffer.length;

        calculateTargetBrightness(avgBrightness);
        determineNightMode(avgBrightness);
    }

    private void calculateTargetBrightness(int avgLux) {
        int targetValue;

        // Select brightness tier based on averaged lux
        if (avgLux > 500) {
            targetValue = brightnessThresholds[5];
        } else if (avgLux > 400) {
            targetValue = brightnessThresholds[4];
        } else if (avgLux > 100) {
            targetValue = brightnessThresholds[3];
        } else if (avgLux > 40) {
            targetValue = brightnessThresholds[2];
        } else if (avgLux > 10) {
            targetValue = brightnessThresholds[1];
        } else {
            targetValue = brightnessThresholds[0];
        }

        // Convert 0-255 integer to 0.0-1.0 float for WindowManager
        float normalizedBrightness = targetValue / 255f;
        screenBrightness.postValue(normalizedBrightness);
    }

    private void determineNightMode(int avgLux) {
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        boolean shouldBeNight = false;

        // Logic to prevent rapid toggling near the threshold
        if (avgLux <= nightModeThreshold) {
            shouldBeNight = true;
        } else if (avgLux > nightModeThreshold + 30) { // Hysteresis of 30 lux
            shouldBeNight = false;
        } else {
            // In the hysteresis zone, keep current state
            return;
        }

        // Only post update if state actually changed
        Boolean current = isNightMode.getValue();
        if (current == null || current != shouldBeNight) {
            isNightMode.postValue(shouldBeNight);
        }
    }

    // ============================================================================================
    // REGION: Telemetry & Sensors
    // ============================================================================================

    public void updateAccelerometer(float x, float y, float z) {
        // Capture peaks
        if (Math.abs(x) > Math.abs(ax)) ax = x;
        if (Math.abs(y) > Math.abs(ay)) ay = y;
        if (Math.abs(z) > Math.abs(az)) az = z;

        updateTelemetryObject();
    }

    public void resetAccelerometerPeaks() {
        ax = 0;
        ay = 0;
        az = 0;
        updateTelemetryObject();
    }

    public void updateVehicleSpeed(int speed) {
        VehicleTelemetry current = liveTelemetry.getValue();
        if (current != null) {
            current.speed = speed;
            liveTelemetry.postValue(current);
        }
    }

    private void updateTelemetryObject() {
        VehicleTelemetry current = liveTelemetry.getValue();
        if (current == null) current = new VehicleTelemetry();

        current.accelX = ax;
        current.accelY = ay;
        current.accelZ = az;

        liveTelemetry.postValue(current);
    }

    // Handles persistent storage for telemetry logs
    public void saveTelemetryLog(TelemetryLog log) {
        databaseRepository.insertTelemetryLog(log);
    }

    public LiveData<List<TelemetryLog>> getHistoryLogs() {
        return databaseRepository.getTelemetryLogs();
    }

    // ============================================================================================
    // REGION: Connectivity & Location
    // ============================================================================================

    public void updateLocation(Location location) {
        currentLocation.postValue(location);
        // If we needed to auto-save track points to DB, we would do it here
    }

    public void updateWeather(Weather weather) {
        currentWeather.postValue(weather);
    }

    public void updateBluetoothState(boolean connected) {
        isBluetoothConnected.postValue(connected);
    }

    public void updateNetworkStatus(int signalStrength, int networkType, boolean isWifi) {
        networkStatus.postValue(new NetworkStatus(signalStrength, networkType, isWifi));
    }

    // ============================================================================================
    // REGION: Getters for ViewModel
    // ============================================================================================

    public LiveData<Float> getScreenBrightness() { return screenBrightness; }
    public LiveData<Boolean> getIsNightMode() { return isNightMode; }
    public LiveData<Location> getLocation() { return currentLocation; }
    public LiveData<Weather> getWeather() { return currentWeather; }
    public LiveData<Boolean> getBluetoothState() { return isBluetoothConnected; }
    public LiveData<NetworkStatus> getNetworkStatus() { return networkStatus; }
    public LiveData<VehicleTelemetry> getLiveTelemetry() { return liveTelemetry; }

    // ============================================================================================
    // REGION: Data Classes
    // ============================================================================================

    /**
     * Simple container for network state to avoid observing multiple LiveDatas for one icon
     */
    public static class NetworkStatus {
        public final int signalStrength; // 0-5
        public final int networkType;    // LTE, 3G, etc
        public final boolean isWifi;

        public NetworkStatus(int signalStrength, int networkType, boolean isWifi) {
            this.signalStrength = signalStrength;
            this.networkType = networkType;
            this.isWifi = isWifi;
        }
    }

    /**
     * Container for real-time vehicle stats
     */
    public static class VehicleTelemetry {
        public int speed = 0;
        public int rpm = 0;
        public double accelX = 0;
        public double accelY = 0;
        public double accelZ = 0;
    }
}
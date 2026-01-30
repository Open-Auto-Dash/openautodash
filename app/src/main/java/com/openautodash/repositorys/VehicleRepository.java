package com.openautodash.repositorys;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.database.DatabaseRepository;
import com.openautodash.database.TelemetryLog;
import com.openautodash.interfaces.WeatherUpdateCallback;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.object.Weather;
import com.openautodash.utilities.WeatherManager;

import java.util.List;

public class VehicleRepository implements WeatherUpdateCallback {
    private static final String TAG = "VehicleRepository";
    private static VehicleRepository instance;

    // Dependencies
    private final LocalSettings localSettings;
    private final DatabaseRepository databaseRepository;
    private final WeatherManager weatherManager;

    // --- Live Data Sources ---
    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>();
    private final MutableLiveData<Weather> currentWeather = new MutableLiveData<>();
    private final MutableLiveData<Float> screenBrightness = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isNightMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isBluetoothConnected = new MutableLiveData<>();
    private final MutableLiveData<NetworkStatus> networkStatus = new MutableLiveData<>();
    private final MutableLiveData<VehicleTelemetry> liveTelemetry = new MutableLiveData<>();

    // Internal State
    private final int[] brightnessBuffer = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    private long lastBrightnessTime = 0;
    private int[] brightnessThresholds;
    private int nightModeThreshold;

    // Accelerometer State
    private double ax, ay, az;

    // Weather Throttling
    private Location lastWeatherLocation;
    private long lastWeatherTime = 0;

    private VehicleRepository(Context context) {
        this.localSettings = new LocalSettings(context);
        this.databaseRepository = new DatabaseRepository((Application) context.getApplicationContext());

        // Initialize your existing WeatherManager
        // Passing 'this' because VehicleRepository implements WeatherUpdateCallback
        this.weatherManager = new WeatherManager(context, null, this);

        // Load settings
        this.brightnessThresholds = localSettings.getBrightnessSetting();
        this.nightModeThreshold = localSettings.getNightModeSetPoint();

        // Init defaults
        isNightMode.setValue(localSettings.getIsNight());
        networkStatus.setValue(new NetworkStatus(0, 0, false));
        liveTelemetry.setValue(new VehicleTelemetry());

        // Init brightness to the lowest setting initially to avoid nulls
        if (brightnessThresholds.length > 0) {
            float normalized = brightnessThresholds[0] / 255f;
            screenBrightness.setValue(normalized);
        }
    }

    public static synchronized VehicleRepository getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleRepository(context);
        }
        return instance;
    }

    public void reloadSettings() {
        this.brightnessThresholds = localSettings.getBrightnessSetting();
        this.nightModeThreshold = localSettings.getNightModeSetPoint();
    }

    // ============================================================================================
    // REGION: Brightness Logic
    // ============================================================================================

    public void updateAmbientLight(float rawLux) {
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
        // Logic matches your original MainActivity logic
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
        boolean shouldBeNight = avgLux <= nightModeThreshold;

        // Hysteresis logic
        if (avgLux > nightModeThreshold + 30) {
            shouldBeNight = false;
        }

        Boolean current = isNightMode.getValue();
        if (current == null || current != shouldBeNight) {
            isNightMode.postValue(shouldBeNight);
        }
    }

    // ============================================================================================
    // REGION: Location & Weather
    // ============================================================================================

    public void updateLocation(Location location) {
        currentLocation.postValue(location);

        // Trigger Weather Manager
        // Logic: Update if we moved > 2km OR it's been > 15 minutes
        // This prevents spamming your API key on every GPS update
        boolean shouldUpdate = false;

        if (lastWeatherLocation == null) {
            Log.d(TAG, "updateLocation: cold start get wether");
            shouldUpdate = true;
        } else {
            float distance = location.distanceTo(lastWeatherLocation);
            long timeDiff = System.currentTimeMillis() - lastWeatherTime;

            if (distance > 2000 || timeDiff > 15 * 60 * 1000) {
                shouldUpdate = true;
            }
        }

        if (shouldUpdate) {
            Log.d(TAG, "updateLocation Getting weather");
            weatherManager.getCurrentWeather(location);
            lastWeatherLocation = location;
            lastWeatherTime = System.currentTimeMillis();
        }
    }

    // WeatherUpdateCallback implementation
    @Override
    public void onComplete(Weather weather) {
        // Sync logic from your original code
        if (weatherManager != null) {
            Log.d(TAG, "onComplete: syncing weather");
            weatherManager.syncWeather();
        }
        Log.d(TAG, "onComplete: Got weather");
        currentWeather.postValue(weather);
    }

    // ============================================================================================
    // REGION: Telemetry & Sensors
    // ============================================================================================

    public void updateAccelerometer(float x, float y, float z) {
        if (Math.abs(x) > Math.abs(ax)) ax = x;
        if (Math.abs(y) > Math.abs(ay)) ay = y;
        if (Math.abs(z) > Math.abs(az)) az = z;
        updateTelemetryObject();
    }

    private void updateTelemetryObject() {
        VehicleTelemetry current = liveTelemetry.getValue();
        if (current == null) current = new VehicleTelemetry();
        current.accelX = ax;
        current.accelY = ay;
        current.accelZ = az;
        liveTelemetry.postValue(current);
    }

    public void updateBluetoothState(boolean connected) {
        isBluetoothConnected.postValue(connected);
    }

    public void updateNetworkStatus(int signalStrength, int networkType, boolean isWifi) {
        networkStatus.postValue(new NetworkStatus(signalStrength, networkType, isWifi));
    }

    // ============================================================================================
    // REGION: Getters
    // ============================================================================================

    public LiveData<Float> getScreenBrightness() { return screenBrightness; }
    public LiveData<Boolean> getIsNightMode() { return isNightMode; }
    public LiveData<Location> getLocation() { return currentLocation; }
    public LiveData<Weather> getWeather() { return currentWeather; }
    public LiveData<Boolean> getBluetoothState() { return isBluetoothConnected; }
    public LiveData<NetworkStatus> getNetworkStatus() { return networkStatus; }
    public LiveData<VehicleTelemetry> getLiveTelemetry() { return liveTelemetry; }

    // Data Classes
    public static class NetworkStatus {
        public final int signalStrength;
        public final int networkType;
        public final boolean isWifi;

        public NetworkStatus(int signalStrength, int networkType, boolean isWifi) {
            this.signalStrength = signalStrength;
            this.networkType = networkType;
            this.isWifi = isWifi;
        }
    }

    public static class VehicleTelemetry {
        public int speed = 0;
        public int rpm = 0;
        public double accelX = 0;
        public double accelY = 0;
        public double accelZ = 0;
    }
}
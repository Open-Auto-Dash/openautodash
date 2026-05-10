package com.openautodash.repositorys;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.database.DatabaseRepository;
import com.openautodash.database.TelemetryLog;
import com.openautodash.database.Trip;
import com.openautodash.interfaces.WeatherUpdateCallback;
import com.openautodash.object.NavigationRequest;
import com.openautodash.utilities.LiveTrackingManager;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.object.Weather;
import com.openautodash.utilities.WeatherManager;

public class VehicleRepository implements WeatherUpdateCallback {
    private static final String TAG = "VehicleRepository";
    private static VehicleRepository instance;

    // Dependencies
    private final LocalSettings localSettings;
    private final DatabaseRepository databaseRepository;
    private final WeatherManager weatherManager;
    private final LiveTrackingManager liveTrackingManager;

    // --- Live Data Sources ---
    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>();
    private final MutableLiveData<Weather> currentWeather = new MutableLiveData<>();
    private final MutableLiveData<Float> screenBrightness = new MutableLiveData<>();

    private final MutableLiveData<String> sensorLux = new MutableLiveData<>("0");
    private final MutableLiveData<Boolean> isNightMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isBluetoothConnected = new MutableLiveData<>(false);
    private final MutableLiveData<NetworkStatus> networkStatus = new MutableLiveData<>();
    private final MutableLiveData<VehicleTelemetry> liveTelemetry = new MutableLiveData<>();
    private final MutableLiveData<NavigationRequest> navigationRequest = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLiveTrackingEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<SpotifyTrack> spotifyTrack = new MutableLiveData<>();

    // --- Trip Logic State ---
    private Trip currentActiveTrip = null;
    private Location lastTripLocation = null;
    private long lastMovementTime = 0;

    // Accumulators to fix "Database Loop" race condition
    private float sessionDistance = 0f;
    private int currentTripId = -1;

    private static final long TRIP_TIMEOUT = 30 * 60 * 1000; // 30 Minutes
    private static final float MOVEMENT_THRESHOLD = 5.0f / 3.6f; // 5 km/h in m/s

    // --- Internal State ---
    private final int[] brightnessBuffer = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    private long lastBrightnessTime = 0;
    private int[] brightnessThresholds;
    private int nightModeThreshold;

    // --- Sensor State ---
    private double ax = 0, ay = 0, az = 0;
    private int rpm = 0;
    private int speed = 0; // OBD Speed

    // --- Weather Throttling ---
    private Location lastWeatherLocation;
    private long lastWeatherTime = 0;

    private VehicleRepository(Context context) {
        this.localSettings = new LocalSettings(context);
        this.databaseRepository = new DatabaseRepository((Application) context.getApplicationContext());

        this.weatherManager = new WeatherManager(context, null, this);
        this.liveTrackingManager = new LiveTrackingManager(context);

        this.brightnessThresholds = localSettings.getBrightnessSetting();
        this.nightModeThreshold = localSettings.getNightModeSetPoint();

        // Init defaults
        isNightMode.setValue(localSettings.getIsNight());
        networkStatus.setValue(new NetworkStatus(0, "", false));
        liveTelemetry.setValue(new VehicleTelemetry());

        if (brightnessThresholds.length > 0) {
            float normalized = brightnessThresholds[0] / 255f;
            screenBrightness.setValue(normalized);
        }

        // --- TRIP SYNC MAGIC (FIXED) ---
        // Only load data from DB if it is a NEW trip ID (or startup).
        // This prevents the DB from overwriting our live local counting.
        databaseRepository.getLiveOpenTrip().observeForever(trip -> {
            if (trip != null) {
                // Only sync if we haven't seen this trip ID yet (e.g. app restart)
                if (this.currentTripId != trip.getId()) {
                    this.currentTripId = trip.getId();
                    this.sessionDistance = trip.getDistanceMeters(); // Restore distance
                    this.currentActiveTrip = trip;
                    Log.d(TAG, "Trip Sync: Loaded Trip " + trip.getId() + " at " + sessionDistance + "m");
                }
            } else {
                // Trip closed or deleted
                this.currentActiveTrip = null;
                this.currentTripId = -1;
                this.sessionDistance = 0f;
                Log.d(TAG, "Trip Sync: No active trip.");
            }
        });

        // Kickstart things
        fetchLastKnownLocation(context);
    }

    public void uploadNavEta(String eta) {
        if (liveTrackingManager != null && Boolean.TRUE.equals(isLiveTrackingEnabled.getValue())) {
            liveTrackingManager.updateNavigation(eta);
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

    @SuppressLint("MissingPermission")
    private void fetchLastKnownLocation(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            Location lastLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLoc == null) {
                lastLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastLoc != null) {
                lastWeatherLocation = null;
                updateLocation(lastLoc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching last location", e);
        }
    }

    // ============================================================================================
    // REGION: Location, Trip Logic & Weather
    // ============================================================================================

    public LiveData<NavigationRequest> getNavigationRequest() {
        return navigationRequest;
    }

    public void postNavigationRequest(double lat, double lng, String label, String placeId) {
        navigationRequest.postValue(new NavigationRequest(lat, lng, label, placeId));
    }

    public void clearNavigationRequest() {
        navigationRequest.postValue(null);
    }

    public void updateLocation(Location location) {
        currentLocation.postValue(location);

        handleTripLogic(location);
        handleLiveTracking(location);

        // Weather Logic
        boolean shouldUpdate = false;
        if (lastWeatherLocation == null) {
            shouldUpdate = true;
        } else {
            float distance = location.distanceTo(lastWeatherLocation);
            long timeDiff = System.currentTimeMillis() - lastWeatherTime;

            if (distance > 2000 || timeDiff > 15 * 60 * 1000) {
                shouldUpdate = true;
            }
        }

        if (shouldUpdate) {
            weatherManager.getCurrentWeather(location);
            lastWeatherLocation = location;
            lastWeatherTime = System.currentTimeMillis();
        }
    }

    private void handleTripLogic(Location location) {
        float gpsSpeed = location.getSpeed();
        long now = System.currentTimeMillis();

        // 1. ALWAYS calculate distance if trip is open (Decoupled from speed check)
        if (currentActiveTrip != null && currentActiveTrip.isOpen()) {
            if (lastTripLocation != null) {
                float distanceDelta = location.distanceTo(lastTripLocation);

                // FILTER: Only add if delta > 2m (Jitter) AND accuracy is good
                if (distanceDelta > 2.0f && location.getAccuracy() < 20) {
                    sessionDistance += distanceDelta; // Update local accumulator

                    currentActiveTrip.setDistanceMeters(sessionDistance);
                    lastTripLocation = location; // Move anchor only on valid distance

                    // Update DB (Safe now, as observer ignores the echo)
                    databaseRepository.updateTrip(currentActiveTrip);
                }
            } else {
                lastTripLocation = location;
            }

            // Always update time/position
            currentActiveTrip.setEndTime(now);
            currentActiveTrip.setEndLat(location.getLatitude());
            currentActiveTrip.setEndLng(location.getLongitude());

            recordTelemetry(location);
        }

        // 2. DETECT MOVEMENT (Only for Starting new trips)
        if (gpsSpeed > MOVEMENT_THRESHOLD) {
            lastMovementTime = now;

            if (currentActiveTrip == null) {
                Log.i(TAG, "Movement detected. Starting new Trip.");
                Trip newTrip = new Trip(now, location.getLatitude(), location.getLongitude());
                databaseRepository.startNewTrip(newTrip);
                // Note: currentActiveTrip/sessionDistance updated by ObserveForever callback

                lastTripLocation = location;
            }
        }

        // 3. TIMEOUT CHECK
        if (currentActiveTrip != null && (now - lastMovementTime > TRIP_TIMEOUT)) {
            checkHomeAndClose(location);
        }
    }

    private void handleLiveTracking(Location location){
        if(isLiveTrackingEnabled.getValue() != null && isLiveTrackingEnabled.getValue()){
            liveTrackingManager.updateLocation(location, liveTelemetry.getValue());
        }
    }

    private void checkHomeAndClose(Location location) {
        double[] homeCoords = localSettings.getHomeLocation();

        if (homeCoords[0] != 0 && homeCoords[1] != 0) {
            float[] results = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), homeCoords[0], homeCoords[1], results);
            float distanceToHome = results[0];

            if (distanceToHome < 200) {
                Log.i(TAG, "Home & Timeout detected. Closing Trip.");
                closeCurrentTrip(location);
            }
        }
    }

    private void recordTelemetry(Location loc) {
        if (currentActiveTrip == null || currentActiveTrip.getId() == 0) return;

        TelemetryLog log = new TelemetryLog(
                currentActiveTrip.getId(),
                0,
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getAltitude(),
                loc.getSpeed(),
                loc.getBearing(),
                0,
                0,
                0,
                ax, ay, az,
                4,
                rpm,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                System.currentTimeMillis()
        );
        databaseRepository.insertTelemetryLog(log);
    }

    public void closeCurrentTrip(Location loc) {
        if (currentActiveTrip != null) {
            currentActiveTrip.setOpen(false);
            currentActiveTrip.setEndTime(System.currentTimeMillis());
            if (loc != null) {
                currentActiveTrip.setEndLat(loc.getLatitude());
                currentActiveTrip.setEndLng(loc.getLongitude());
            }
            databaseRepository.updateTrip(currentActiveTrip);

            currentActiveTrip = null;
            lastTripLocation = null;
            sessionDistance = 0f;
            currentTripId = -1;
        }
    }

    public void setTripBusiness(boolean isBusiness) {
        if (currentActiveTrip != null) {
            currentActiveTrip.setBusiness(isBusiness);
            databaseRepository.updateTrip(currentActiveTrip);
        }
    }

    public LiveData<Trip> getCurrentTripData() {
        return databaseRepository.getLiveOpenTrip();
    }

    // ============================================================================================
    // REGION: Sensors & Telemetry Inputs
    // ============================================================================================

    public void updateAccelerometer(float x, float y, float z) {
        this.ax = x;
        this.ay = y;
        this.az = z;
        updateTelemetryObject();
    }

    public void updateVehicleData(int rpm, int speed) {
        this.rpm = rpm;
        this.speed = speed;
        updateTelemetryObject();
    }

    private void updateTelemetryObject() {
        VehicleTelemetry current = liveTelemetry.getValue();
        if (current == null) current = new VehicleTelemetry();
        current.accelX = ax;
        current.accelY = ay;
        current.accelZ = az;
        current.rpm = rpm;
        current.speed = speed;
        liveTelemetry.postValue(current);
    }

    public void updateBluetoothState(boolean connected) {
        isBluetoothConnected.postValue(connected);
    }

    public void updateNetworkStatus(int signalStrength, String type, boolean isWifi) {
        networkStatus.postValue(new NetworkStatus(signalStrength, type, isWifi));
    }

    // ============================================================================================
    // REGION: Brightness & Other Getters
    // ============================================================================================

    public void updateAmbientLight(float rawLux) {
        if (System.currentTimeMillis() - lastBrightnessTime <= 1000) return;
        lastBrightnessTime = System.currentTimeMillis();
        sensorLux.postValue(String.valueOf((int)rawLux));

        for (int i = brightnessBuffer.length - 1; i > 0; i--) {
            brightnessBuffer[i] = brightnessBuffer[i - 1];
        }
        brightnessBuffer[0] = (int) rawLux;

        int totalBuffer = 0;
        for (int value : brightnessBuffer) totalBuffer += value;
        int avgBrightness = totalBuffer / brightnessBuffer.length;

        calculateTargetBrightness(avgBrightness);
        determineNightMode(avgBrightness);
    }

    private void calculateTargetBrightness(int avgLux) {
        int targetValue;
        if (avgLux > 500) targetValue = brightnessThresholds[5];
        else if (avgLux > 400) targetValue = brightnessThresholds[4];
        else if (avgLux > 100) targetValue = brightnessThresholds[3];
        else if (avgLux > 40) targetValue = brightnessThresholds[2];
        else if (avgLux > 10) targetValue = brightnessThresholds[1];
        else targetValue = brightnessThresholds[0];

        screenBrightness.postValue(targetValue / 255f);
    }

    private void determineNightMode(int avgLux) {
        boolean shouldBeNight = avgLux <= nightModeThreshold;
        Boolean current = isNightMode.getValue();

        if(!shouldBeNight && current!= null && current){
            for (int i = brightnessBuffer.length - 1; i > 0; i--) {
                if(brightnessBuffer[i] < nightModeThreshold + 30){
                    shouldBeNight = true;
                    break;
                }
            }
        }

        if (current == null || current != shouldBeNight) {
            isNightMode.postValue(shouldBeNight);
        }
    }

    @Override
    public void onComplete(Weather weather) {
        if (weatherManager != null) weatherManager.syncWeather();
        currentWeather.postValue(weather);
    }

    public LiveData<Float> getScreenBrightness() { return screenBrightness; }
    public LiveData<String> getSensorLux() { return sensorLux; }
    public LiveData<Boolean> getIsNightMode() { return isNightMode; }
    public LiveData<Location> getLocation() { return currentLocation; }
    public LiveData<Weather> getWeather() { return currentWeather; }
    public LiveData<Boolean> getBluetoothState() { return isBluetoothConnected; }
    public LiveData<NetworkStatus> getNetworkStatus() { return networkStatus; }
    public LiveData<VehicleTelemetry> getLiveTelemetry() { return liveTelemetry; }

    public LiveData<Boolean> getIsLiveTrackingEnabled() {
        return isLiveTrackingEnabled;
    }

    public void setLiveTrackingEnabled(boolean isEnabled) {
        isLiveTrackingEnabled.postValue(isEnabled);
    }

    public LiveData<SpotifyTrack> getSpotifyTrack() {
        return spotifyTrack;
    }

    public void setSpotifyTrack(SpotifyTrack spotifyTrack) {
        this.spotifyTrack.postValue(new SpotifyTrack(spotifyTrack.title, spotifyTrack.artist, spotifyTrack.artUrl, spotifyTrack.progress));
    }

    public void uploadSpotifyUpdate(SpotifyTrack track) {
        if (liveTrackingManager != null) {
            liveTrackingManager.updateSpotify(track);
        }
    }

    // Updated NetworkStatus to include String type
    public static class NetworkStatus {
        public final int signalStrength;
        public final String networkTypeName;
        public final boolean isWifi;
        public NetworkStatus(int s, String t, boolean w) {
            signalStrength = s;
            networkTypeName = t;
            isWifi = w;
        }
    }

    public static class VehicleTelemetry {
        public int speed = 0;
        public int rpm = 0;
        public double accelX = 0;
        public double accelY = 0;
        public double accelZ = 0;
    }

    public static class SpotifyTrack {
        public final String title;
        public final String artist;
        public final String artUrl;
        public final int progress;

        public SpotifyTrack(String title, String artist, String artUrl, int progress) {
            this.title = title;
            this.artist = artist;
            this.artUrl = artUrl;
            this.progress = progress;
        }
    }
}

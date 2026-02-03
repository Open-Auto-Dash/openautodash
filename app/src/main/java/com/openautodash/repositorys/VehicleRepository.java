package com.openautodash.repositorys;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
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
    private final MutableLiveData<Boolean> isBluetoothConnected = new MutableLiveData<>();
    private final MutableLiveData<NetworkStatus> networkStatus = new MutableLiveData<>();
    private final MutableLiveData<VehicleTelemetry> liveTelemetry = new MutableLiveData<>();
    private final MutableLiveData<NavigationRequest> navigationRequest = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLiveTrackingEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<SpotifyTrack> spotifyTrack = new MutableLiveData<>();

    // --- Trip Logic State ---
    private Trip currentActiveTrip = null;
    private Location lastTripLocation = null; // Used for distance calculation
    private long lastMovementTime = 0;
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
    private int speed = 0; // OBD Speed (different from GPS speed)

    // --- Weather Throttling ---
    private Location lastWeatherLocation;
    private long lastWeatherTime = 0;

    private VehicleRepository(Context context) {
        this.localSettings = new LocalSettings(context);
        this.databaseRepository = new DatabaseRepository((Application) context.getApplicationContext());

        // Weather Manager
        this.weatherManager = new WeatherManager(context, null, this);

        // Tracking Manager
        this.liveTrackingManager = new LiveTrackingManager(context);

        // Load settings
        this.brightnessThresholds = localSettings.getBrightnessSetting();
        this.nightModeThreshold = localSettings.getNightModeSetPoint();

        // Init defaults
        isNightMode.setValue(localSettings.getIsNight());
        networkStatus.setValue(new NetworkStatus(0, 0, false));
        liveTelemetry.setValue(new VehicleTelemetry());

        // Init brightness
        if (brightnessThresholds.length > 0) {
            float normalized = brightnessThresholds[0] / 255f;
            screenBrightness.setValue(normalized);
        }

        // --- TRIP SYNC MAGIC ---
        // We observe the Database. When a trip is inserted/updated, this fires.
        // This ensures 'currentActiveTrip' always has the valid ID from the DB.
        databaseRepository.getLiveOpenTrip().observeForever(trip -> {
            this.currentActiveTrip = trip;
            if (trip != null) {
                Log.d(TAG, "Trip Sync: Active Trip ID: " + trip.getId() + " Dist: " + trip.getDistanceMeters());
            } else {
                Log.d(TAG, "Trip Sync: No active trip.");
            }
        });

        // Kickstart things
//        fetchLastKnownLocation(context);
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
                // Fake null so weather updates immediately on boot
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

    // 4. Method to clear the request after the Fragment consumes it (prevents loops)
    public void clearNavigationRequest() {
        navigationRequest.postValue(null);
    }

    public void updateLocation(Location location) {
        currentLocation.postValue(location);

        // 1. Handle Trip Recording
        handleTripLogic(location);

        // Handle live tracking updates
        handleLiveTracking(location);

        // 2. Handle Weather Updates
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
        float gpsSpeed = location.getSpeed(); // m/s
        long now = System.currentTimeMillis();

        // --- 1. DETECT MOVEMENT ---
        if (gpsSpeed > MOVEMENT_THRESHOLD) {
            lastMovementTime = now;

            // Start new trip if we are moving and don't have one
            if (currentActiveTrip == null) {
                Log.i(TAG, "Movement detected. Starting new Trip.");
                Trip newTrip = new Trip(now, location.getLatitude(), location.getLongitude());
                databaseRepository.startNewTrip(newTrip);
                // Note: currentActiveTrip will be updated automatically by the observeForever callback above
                // once the DB insert completes.

                lastTripLocation = location; // Reset distance calculation anchor
            }
        }

        // --- 2. UPDATE ACTIVE TRIP ---
        if (currentActiveTrip != null && currentActiveTrip.isOpen()) {

            // Calculate Distance Delta
            if (lastTripLocation != null) {
                float distanceDelta = location.distanceTo(lastTripLocation);
                // Only add if it makes sense (e.g., > 10m to avoid GPS drift while standing still)
                if (distanceDelta > 0) {
                    float newTotal = currentActiveTrip.getDistanceMeters() + distanceDelta;
                    currentActiveTrip.setDistanceMeters(newTotal);
                }
            }
            lastTripLocation = location;

            // Update End Time & Location (Always keep these current)
            currentActiveTrip.setEndTime(now);
            currentActiveTrip.setEndLat(location.getLatitude());
            currentActiveTrip.setEndLng(location.getLongitude());

            // Persist Trip Updates to DB
            databaseRepository.updateTrip(currentActiveTrip);

            // Record Telemetry Point
            recordTelemetry(location);
        }

        // --- 3. CHECK FOR TIMEOUT / HOME ---
        // If we haven't moved in 30 mins AND we are at home, close it.
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
        double[] homeCoords = localSettings.getHomeLocation(); // You added this to LocalSettings previously

        // Simple 0,0 check to ensure home is actually set
        if (homeCoords[0] != 0 && homeCoords[1] != 0) {
            float[] results = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), homeCoords[0], homeCoords[1], results);
            float distanceToHome = results[0];

            // If within 200 meters of home
            if (distanceToHome < 200) {
                Log.i(TAG, "Home & Timeout detected. Closing Trip.");
                closeCurrentTrip(location);
            }
        }
    }

    private void recordTelemetry(Location loc) {
        // Safety: Do not record if trip hasn't synced with DB yet (ID would be 0 or null)
        if (currentActiveTrip == null || currentActiveTrip.getId() == 0) return;

        // Create fully populated log
        TelemetryLog log = new TelemetryLog(
                currentActiveTrip.getId(),
                0, // Segment (implement if needed)
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getAltitude(),
                loc.getSpeed(),
                loc.getBearing(),
                0, // Heading ( Compass vs GPS bearing)
                0, // SpeedLimit
                0, // RoadType
                ax, ay, az, // Real Accelerometer Data
                4, // Vehicle State (Running) - You could make this dynamic based on ignition
                rpm, // Real RPM
                0, // Voltage
                0, // Gear
                0, // Break
                0, // Accelerator
                0, // Steering
                0, // Cruise
                0, // Occupants
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

            // Clear local references
            currentActiveTrip = null;
            lastTripLocation = null;
        }
    }

    // Call this from ViewModel
    public void setTripBusiness(boolean isBusiness) {
        if (currentActiveTrip != null) {
            currentActiveTrip.setBusiness(isBusiness);
            databaseRepository.updateTrip(currentActiveTrip);
        }
    }

    // Call this from ViewModel to get Live Data
    public LiveData<Trip> getCurrentTripData() {
        return databaseRepository.getLiveOpenTrip();
    }

    // ============================================================================================
    // REGION: Sensors & Telemetry Inputs
    // ============================================================================================

    public void updateAccelerometer(float x, float y, float z) {
        // Update local state for the next Telemetry Record
        this.ax = x;
        this.ay = y;
        this.az = z;

        updateTelemetryObject();
    }

    // Call this if you have OBD/CanBus data coming in
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

    public void updateNetworkStatus(int signalStrength, int networkType, boolean isWifi) {
        networkStatus.postValue(new NetworkStatus(signalStrength, networkType, isWifi));
    }

    // ============================================================================================
    // REGION: Brightness & Other Getters
    // ============================================================================================

    public void updateAmbientLight(float rawLux) {
        if (System.currentTimeMillis() - lastBrightnessTime <= 1000) return;
        lastBrightnessTime = System.currentTimeMillis();
        sensorLux.postValue(String.valueOf((int)rawLux));

        // Shift buffer
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

    public LiveData<String> getSensorLux() {return sensorLux;}
    public LiveData<Boolean> getIsNightMode() { return isNightMode; }
    public LiveData<Location> getLocation() { return currentLocation; }
    public LiveData<Weather> getWeather() { return currentWeather; }
    public LiveData<Boolean> getBluetoothState() { return isBluetoothConnected; }
    public LiveData<NetworkStatus> getNetworkStatus() { return networkStatus; }
    public LiveData<VehicleTelemetry> getLiveTelemetry() { return liveTelemetry; }

    // Tracking and Spotify
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

    public static class NetworkStatus {
        public final int signalStrength;
        public final int networkType;
        public final boolean isWifi;
        public NetworkStatus(int s, int n, boolean w) { signalStrength = s; networkType = n; isWifi = w; }
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

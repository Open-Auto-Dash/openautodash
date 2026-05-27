package com.openautodash.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.bluetooth.BLECentralScanner;
import com.openautodash.enums.VehicleState;
import com.openautodash.interfaces.BluetoothKeyCallback;
import com.openautodash.object.Weather;
import com.openautodash.pairing.DashPairingManager;
import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.utilities.UsbSerialManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainForegroundService extends Service implements SensorEventListener, BluetoothKeyCallback, BLECentralScanner.MessageHandler {
    private static final String TAG = "MainForegroundService";
    private static final String CHANNEL_ID = "OpenAutoDashChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long ALARM_INTERVAL = 5 * 60 * 1000; // 5 minutes

    // The Single Source of Truth
    private VehicleRepository repository;

    // Hardware Managers
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private BLECentralScanner bleScanner;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private DashPairingManager pairingManager;
    private UsbSerialManager usbSerialManager;

    // Location
    private LocationListener locationListener;
    private GnssStatus.Callback gnssCallback;

    // State
    private VehicleState vehicleState = VehicleState.Idle;
    private final Handler handler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        // Initialize Repository
        repository = VehicleRepository.getInstance(getApplicationContext());
        pairingManager = new DashPairingManager(getApplicationContext());

        // Allow strict mode for disk reads if necessary during init
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        initializeHardware();
        createNotificationChannel();
        acquireWakeLocks();
        requestBatteryOptimizationExemption();
        setupPeriodicAlarm();
        setupSpotifyListener();

        // Start BLE scanning/connection (tablet is central)
        bleScanner = new BLECentralScanner(this, this);
        bleScanner.start();

        usbSerialManager = new UsbSerialManager(this, new UsbSerialManager.UsbPermissionListener() {
            @Override
            public void onPermissionGranted() {
                Log.d(TAG, "ESP32 USB serial connected");
            }

            @Override
            public void onPermissionDenied() {
                Log.w(TAG, "ESP32 USB serial permission denied");
            }

            @Override
            public void onCommunicationError(String errorMessage) {
                Log.w(TAG, "ESP32 USB serial error: " + errorMessage);
            }
        });
        usbSerialManager.connectFirstAvailable();
    }

    private void initializeHardware() {
        // 1. Sensors (Accelerometer AND Light)
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Register Accelerometer (moved from original Service logic)
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // Register Light Sensor (moved from MainActivity logic)
        // Now the service handles brightness data collection even if the UI is paused
        Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (light != null) {
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL);
        }

        // 2. Location
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        // Define the listener to pump data straight to the Repository
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // Update the Repository
                repository.updateLocation(location);

                // Also broadcast for legacy systems if needed, or internal logic
                Log.v(TAG, "Location updated: " + location.getSpeed());
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {}
            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        // Start Foreground immediately
        startForeground(NOTIFICATION_ID, createNotification());

        // Start Loops
        handler.post(vehicleStateRunnable);
        startLocationUpdates(1000, 0);

        return START_STICKY;
    }

    // ============================================================================================
    // REGION: Sensor Handling (Accelerometer & Light)
    // ============================================================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Send raw accel data to Repository
            repository.updateAccelerometer(event.values[0], event.values[1], event.values[2]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            // Send raw light data to Repository
            // The Repository handles the "Moving Average" buffer logic now
//            Log.d(TAG, "RAW LIGHT SENSOR: " + event.values[0]);
            repository.updateAmbientLight(event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // ============================================================================================
    // REGION: Location Handling
    // ============================================================================================

    @SuppressLint("MissingPermission")
    private void startLocationUpdates(int minTimeMs, int minDistanceM) {
        if (!hasLocationPermissions()) {
            Log.e(TAG, "Missing location permissions");
            return;
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    locationListener
            );

            // Optional: GNSS Status for advanced debugging
            gnssCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    super.onSatelliteStatusChanged(status);
                    // Could pump satellite count to Repo if needed
                }
            };
            locationManager.registerGnssStatusCallback(gnssCallback, null);

        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates", e);
        }
    }

    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ============================================================================================
    // REGION: BLE & Vehicle Control
    // ============================================================================================

    @Override
    public void onConnectionStateChanged(boolean connected) {
        Log.d(TAG, connected ? "Bluetooth key connected" : "Bluetooth key disconnected");
        repository.updateBluetoothState(connected);
        keepScreenOn(connected);
    }

    @Override
    public boolean onAuthResponse(String phoneId, String challenge, String mac) {
        boolean authorized = pairingManager != null && pairingManager.verifyAuthResponse(phoneId, challenge, mac);
        Log.d(TAG, authorized ? "Bluetooth key authorized: " + phoneId : "Bluetooth key authorization failed: " + phoneId);
        repository.updateBluetoothState(authorized);
        keepScreenOn(authorized);
        return authorized;
    }

    @Override
    public void onRssiUpdate(BluetoothDevice device, int rssi) {
        // RSSI is only used internally by BLECentralScanner for proximity disconnects.
    }

    @Override
    public void onLocationPin(double latitude, double longitude, String label, String placeId) {
        if (!Boolean.TRUE.equals(repository.getBluetoothState().getValue())) {
            Log.w(TAG, "Ignoring PIN because Bluetooth key is not authorized");
            return;
        }
        Log.d(TAG, "Received PIN via BLE. ID: " + placeId + " Label: " + label);

        // Push to Repository (The Bridge)
        if (repository != null) {
            repository.postNavigationRequest(latitude, longitude, label, placeId);
        }

        // Optional: Wake up screen if locked
        wakeUpDevice();
    }

    @Override
    public void onVehicleCommand(String command, String[] params) {
        if (!"PAIR_HELLO".equalsIgnoreCase(command) && !Boolean.TRUE.equals(repository.getBluetoothState().getValue())) {
            Log.w(TAG, "Ignoring vehicle command because Bluetooth key is not authorized: " + command);
            return;
        }
        // This receives commands from the BLE device (Unlock, Start, etc.)
        // Since we removed the Callback to Activity, we should broadcast this
        // or update a specific LiveData in the Repository if the UI needs to react.
        Log.d(TAG, "Received Vehicle Command: " + command);

        switch (command.toUpperCase()) {
            case "LOCK":
                forwardVehicleCommandToUsb(command);
                break;
            case "UNLOCK":
                forwardVehicleCommandToUsb(command);
                wakeUpDevice(); // Usually we want to wake screen on unlock
                break;
            case "REMOTE_START":
            case "POWER_OFF":
            case "POWER_TOGGLE":
                forwardVehicleCommandToUsb(command);
                break;
            case "PAIR_HELLO":
                if (params.length > 0 && pairingManager != null) {
                    try {
                        String phoneHello = new String(Base64.decode(params[0], Base64.NO_WRAP));
                        boolean ok = pairingManager.finalizePairingFromPhoneHello(phoneHello);
                        Log.d(TAG, ok ? "App pairing completed" : "App pairing rejected");
                        if (ok && bleScanner != null) {
                            bleScanner.requestAuthorization();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse PAIR_HELLO", e);
                    }
                }
                break;
        }
    }

    private void forwardVehicleCommandToUsb(String command) {
        if (usbSerialManager == null) return;
        String commandId = "usb_" + System.currentTimeMillis();
        String message = String.format(Locale.US, "CMD:%s,%s", commandId, command.toUpperCase(Locale.US));
        boolean sent = usbSerialManager.writeLine(message);
        Log.d(TAG, sent ? "Forwarded command to ESP32 USB: " + command : "ESP32 USB command send failed: " + command);
    }

    @Override
    public void onTelemetryRequest(BluetoothDevice device) {
        // The BLE Device is asking for data. We pull fresh data from the Repository.
        VehicleRepository.VehicleTelemetry telemetry = repository.getLiveTelemetry().getValue();
        Location loc = repository.getLocation().getValue();
        Weather weather = repository.getWeather().getValue();

        Map<String, String> data = new HashMap<>();

        if (telemetry != null) {
            data.put("vehicle_speed", String.valueOf(telemetry.speed));
            data.put("rpm", String.valueOf(telemetry.rpm));
            data.put("accel_x", String.valueOf(round(telemetry.accelX, 3)));
            data.put("accel_y", String.valueOf(round(telemetry.accelY, 3)));
            data.put("accel_z", String.valueOf(round(telemetry.accelZ, 3)));
        }

        if (loc != null) {
            double gpsSpeedMps = loc.getSpeed();
            data.put("lat", format(loc.getLatitude(), 6));
            data.put("lon", format(loc.getLongitude(), 6));
            data.put("alt", format(loc.getAltitude(), 1));
            data.put("speed_mps", format(gpsSpeedMps, 2));
            data.put("speed_kmh", format(gpsSpeedMps * 3.6d, 1));
            data.put("bearing", format(loc.getBearing(), 1));
            data.put("accuracy", format(loc.getAccuracy(), 1));
        }
        if (weather != null) {
            data.put("outside_temp_c", String.valueOf(weather.getTemp()));
        }
        data.put("updated_at", String.valueOf(System.currentTimeMillis()));

        if (bleScanner != null) {
            bleScanner.sendTelemetryData(data);
        }
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "Bluetooth key connected");
        repository.updateBluetoothState(true);
        keepScreenOn(true);
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Bluetooth key disconnected");
        repository.updateBluetoothState(false);
        keepScreenOn(false);
    }

    @Override
    public void onDataReceived(String data) {
        Log.d(TAG, "Raw data received: " + data);
    }

    // ============================================================================================
    // REGION: System & Lifecycle
    // ============================================================================================

    private final Runnable vehicleStateRunnable = new Runnable() {
        @Override
        public void run() {
            // Reset accelerometer peaks in repository periodically if needed
            // or perform state machine logic here.

            // This is where you would calculate VehicleState (Parked, Driving)
            // based on speed/rpm from the Repository and update the Repo back.

            handler.postDelayed(this, 1000);
        }
    };

    private void keepScreenOn(boolean on) {
        if (on) {
            if (!screenWakeLock.isHeld()) {
                screenWakeLock.acquire();
            }
        } else {
            if (screenWakeLock.isHeld()) {
                screenWakeLock.release();
            }
        }
    }

    public void wakeUpDevice() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && !powerManager.isInteractive()) {
            Intent wakeIntent = new Intent(this, MainActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(wakeIntent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "OpenAutoDash Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background Vehicle Data Service");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoDash Active")
                .setContentText("Monitoring Vehicle Sensors")
                .setSmallIcon(R.drawable.ic_my_location_black_24dp)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void acquireWakeLocks() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenAutoDash:ServiceWakeLock"
        );
        wakeLock.acquire();

        screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "OpenAutoDash:ScreenWakeLock"
        );
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    // Alarm logic for restarting service if it gets killed by OS
    private void setupPeriodicAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MainForegroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ALARM_INTERVAL, pendingIntent);
            } else {
                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ALARM_INTERVAL, ALARM_INTERVAL, pendingIntent);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ALARM_INTERVAL, pendingIntent);
        }
    }

    private void setupSpotifyListener(){
        repository.getSpotifyTrack().observeForever(spotifyTrack -> {
            if (spotifyTrack != null && Boolean.TRUE.equals(repository.getIsLiveTrackingEnabled().getValue())) {
                repository.uploadSpotifyUpdate(spotifyTrack);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Cleanup Hardware
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (screenWakeLock != null && screenWakeLock.isHeld()) screenWakeLock.release();

        if (bleScanner != null) bleScanner.stop();
        if (usbSerialManager != null) {
            usbSerialManager.destroy();
            usbSerialManager = null;
        }

        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            if (gnssCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssCallback);
            }
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        handler.removeCallbacksAndMessages(null);

        // Attempt restart
        Intent restartIntent = new Intent("com.openautodash.RestartService");
        sendBroadcast(restartIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We do not allow binding anymore. The UI must use the Repository.
        return null;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", value);
    }
}

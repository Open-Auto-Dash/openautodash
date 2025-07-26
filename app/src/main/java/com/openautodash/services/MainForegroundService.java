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
import android.location.LocationManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.bluetooth.BLEAdvertiser;
import com.openautodash.enums.VehicleState;
import com.openautodash.interfaces.BluetoothKeyCallback;
import com.openautodash.interfaces.VehicleControlCallback;
import com.openautodash.object.LastKnownCameraPosition;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.LocationListener;

import java.util.HashMap;
import java.util.Map;


public class MainForegroundService extends Service implements SensorEventListener, BluetoothKeyCallback, BLEAdvertiser.MessageHandler {
    private static final String TAG = "MainForegroundService";
    private static final String CHANNEL_ID = "OpenAutoDashChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long ALARM_INTERVAL = 5 * 60 * 1000; // 5 minutes

    private LocalSettings settings;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private BLEAdvertiser bleAdvertiser;

    private SensorManager sensorManager;
    double ax, ay, az;   // acceleration values

    private LocationManager locationManager;
    private LocationListener locationListener;
    private MutableLiveData<Location> locationLiveData = new MutableLiveData<>();
    private GnssStatus.Callback gnssCallback;
    private GnssStatus gnssStatus;

    private MutableLiveData<Integer> keyVisible = new MutableLiveData<>();
    private VehicleState vehicleState = VehicleState.Dead;
    private Handler handler = new Handler();

    private VehicleControlCallback vehicleControlCallback;

    @Override
    public void onRssiUpdate(BluetoothDevice device, int rssi) {
        Log.d(TAG, "RSSI update from " + device.getAddress() + ": " + rssi);
    }

    @Override
    public void onLocationPin(double latitude, double longitude, String label) {
        // Broadcast location pin to MainActivity or handle as needed
        Intent intent = new Intent("location_pin_received");
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("label", label);
        sendBroadcast(intent);
    }

    @Override
    public void onVehicleCommand(String command, String[] params) {
        if (vehicleControlCallback == null) return;

        switch (command.toUpperCase()) {
            case "LOCK":
                vehicleControlCallback.onLockCommand();
                break;
            case "UNLOCK":
                vehicleControlCallback.onUnlockCommand();
                break;
            case "START":
                vehicleControlCallback.onStartCommand();
                break;
            case "STOP":
                vehicleControlCallback.onStopCommand();
                break;
            case "LIGHTS":
                if (params.length > 0) {
                    boolean turnOn = Boolean.parseBoolean(params[0]);
                    vehicleControlCallback.onLightsCommand(turnOn);
                }
                break;
            default:
                Log.w(TAG, "Unknown vehicle command: " + command);
        }
    }

    @Override
    public void onTelemetryRequest(BluetoothDevice device) {
        // Gather current telemetry data
        Map<String, String> telemetryData = new HashMap<>();
        telemetryData.put("speed", "0"); // Replace with actual speed
        telemetryData.put("rpm", "0"); // Replace with actual RPM
        telemetryData.put("temp", "0"); // Replace with actual temperature
        telemetryData.put("fuel", "0"); // Replace with actual fuel level
        telemetryData.put("voltage", "0"); // Replace with actual voltage

        // Add accelerometer data
        telemetryData.put("accel_x", String.valueOf(round(ax, 3)));
        telemetryData.put("accel_y", String.valueOf(round(ay, 3)));
        telemetryData.put("accel_z", String.valueOf(round(az, 3)));

        // Add location data if available
        Location location = locationLiveData.getValue();
        if (location != null) {
            telemetryData.put("lat", String.valueOf(location.getLatitude()));
            telemetryData.put("lon", String.valueOf(location.getLongitude()));
            telemetryData.put("alt", String.valueOf(location.getAltitude()));
            telemetryData.put("speed_gps", String.valueOf(location.getSpeed()));
            telemetryData.put("bearing", String.valueOf(location.getBearing()));
        }

        // Send telemetry data through BLE
        if (bleAdvertiser != null) {
            bleAdvertiser.sendTelemetryData(telemetryData);
        }
    }

    public void setVehicleControlCallback(VehicleControlCallback callback) {
        this.vehicleControlCallback = callback;
    }

    public void sendVehicleState(VehicleState state) {
        if (bleAdvertiser != null) {
            bleAdvertiser.sendMessage("STATE:" + state.name());
        }
    }

    public class MainForegroundServiceBinder extends Binder {
        public MainForegroundService getService() {
            return MainForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        initializeComponents();
        createNotificationChannel();
        acquireWakeLocks();
        requestBatteryOptimizationExemption();
        setupPeriodicAlarm();

        bleAdvertiser = new BLEAdvertiser(this, this, this);
        bleAdvertiser.startAdvertising();
    }

    private void initializeComponents() {
        settings = new LocalSettings(getApplicationContext());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);

        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener(locationLiveData);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "OpenAutoDash Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps OpenAutoDash running in background");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
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

    private void setupPeriodicAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MainForegroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    scheduleExactAlarm(alarmManager, pendingIntent);
                } else {
                    // Fall back to inexact alarm
                    alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + ALARM_INTERVAL,
                            ALARM_INTERVAL,
                            pendingIntent
                    );
                }
            } else {
                scheduleExactAlarm(alarmManager, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when scheduling alarm", e);
            // Fall back to inexact alarm
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + ALARM_INTERVAL,
                    ALARM_INTERVAL,
                    pendingIntent
            );
        }
    }

    private void scheduleExactAlarm(AlarmManager alarmManager, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + ALARM_INTERVAL,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + ALARM_INTERVAL,
                    pendingIntent
            );
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        startForeground(NOTIFICATION_ID, createNotification());

        handler.post(serviceRunnable);
        setLocationListener(1000, 0);

        return START_STICKY;
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("OpenAutoDash Active")
                .setContentText("Running in background")
                .setSmallIcon(R.drawable.ic_my_location_black_24dp)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private boolean setLocationListener(int minTimMs, int minDistanceM) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimMs, minDistanceM, locationListener);

        gnssCallback = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                Log.d(TAG, "onStarted: gnss");
                super.onStarted();
            }

            @Override
            public void onStopped() {
                Log.d(TAG, "onStopped: gnss");
                super.onStopped();
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                Log.d(TAG, "onFirstFix: gnss");
                super.onFirstFix(ttffMillis);
            }

            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                super.onSatelliteStatusChanged(status);
                Log.d(TAG, "onSatelliteStatusChanged: gnss");
                gnssStatus = status;

                Intent intent = new Intent("gnss_update");
                sendBroadcast(intent);
            }
        };
        return true;
    }

    private final Runnable serviceRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "onSensorChanged: X:" + round(ax, 3) + " Y:" + round(ay, 3) + " Z:" + round(az, 3));
            ax = 0;
            ay = 0;
            az = 0;

            switch (vehicleState) {
                case Dead:    // Dead     | GPS off
                    break;
                case Sleep:   // Sleep    | GPS, 10m
                    break;
                case Idle:    // Idle     | GPS, 1m
                    break;
                case Powered: // Powered  | GPS, 10s
                    break;
                case Running: // Running  | GPS, 1s
                    break;
                case Driving: // Driving  | GPS, 1s
                    break;
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((int)event.values[0] > (int)ax) {
                ax = event.values[0];
            }
            if ((int)event.values[1] > (int)ay) {
                ay = event.values[1];
            }
            if ((int)event.values[2] > (int)az) {
                az = event.values[2];
            }
        }
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "Bluetooth key connected");
        keyVisible.postValue(1);
        keepScreenOn(true);
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Bluetooth key disconnected");
        keyVisible.postValue(0);
        keepScreenOn(false);
    }

    @Override
    public void onDataReceived(String data) {
        Log.d(TAG, "Raw data received: " + data);
    }

    private void keepScreenOn(boolean on) {
        if (on) {
            if (!screenWakeLock.isHeld()) {
                screenWakeLock.acquire();
            }
            Log.d(TAG, "Screen keep on enabled");
        } else {
            if (screenWakeLock.isHeld()) {
                screenWakeLock.release();
            }
            Log.d(TAG, "Screen keep on disabled");
        }
    }

    public void wakeUpDevice() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && !powerManager.isInteractive()) {
            Log.d(TAG, "wakeUpDevice");
            Intent wakeIntent = new Intent(this, MainActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(wakeIntent);
        } else {
            Log.d(TAG, "Device already awake");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();

        // Request restart
        Intent restartIntent = new Intent("com.openautodash.RestartService");
        sendBroadcast(restartIntent);
    }

    private void cleanup() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (screenWakeLock != null && screenWakeLock.isHeld()) {
            screenWakeLock.release();
        }
        if (bleAdvertiser != null) {
            bleAdvertiser.stopAdvertising();
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MainForegroundServiceBinder();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used but required by SensorEventListener
    }

    public MutableLiveData<Location> getLocationLiveData() {
        return locationLiveData;
    }

    public MutableLiveData<Integer> getBluetoothState() {
        return keyVisible;
    }

    private static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
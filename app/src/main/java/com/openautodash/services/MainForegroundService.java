package com.openautodash.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.openautodash.R;
import com.openautodash.enums.VehicleState;
import com.openautodash.object.PhoneKey;
import com.openautodash.utilities.LocalSettings;

import static com.openautodash.App.ForegroundService;
import static com.openautodash.App.VehicleInformation;

import java.util.List;
import java.util.stream.Stream;

public class MainForegroundService extends Service implements SensorEventListener{
    private static final String TAG = "MainForegroundService";

    //Configuration stuff
    private LocalSettings settings;


    private SensorManager sensorManager;
    double ax, ay, az;   // these are the acceleration in x,y and z axis


    // GPS / Location stuff
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GnssStatus.Callback gnssCallback;
    private GnssStatus gnssStatus;
    private Location currentLocation;


    // Bluetooth stuff
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private int btStatus;


    //Vehicle State, Security
    private VehicleState vehicleState;

    private long lastSawKey;
    private List<PhoneKey> phoneKeyList;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy); //No stupid strictness for me.

        settings = new LocalSettings(getApplicationContext());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener((SensorEventListener) this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        // init variables
        phoneKeyList = settings.getPhoneKeys();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // If we get killed, after returning from here, restart
        Notification notification = new NotificationCompat.Builder(this, ForegroundService)
                .setContentTitle("Background Service Running")
                .setSmallIcon(R.drawable.ic_my_location_black_24dp)
                .setPriority(NotificationManagerCompat.IMPORTANCE_LOW)
                .build();
        startForeground(32, notification);

        handler.post(runnable);

        initLocationListener();
        initBluetooth();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationManager.unregisterGnssStatusCallback(gnssCallback);
        }
    }


    private final Handler handler = new Handler();
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            VehicleState vehicleState;
            vehicleState = VehicleState.Dead;
            discoverKey();

            Log.d(TAG, "onSensorChanged: X:" + round(ax, 3) + " Y:" + round(ay, 3) + " Z:" + round(az, 3));
            ax = 0;
            ay = 0;
            az = 0;
            handler.postDelayed(this, 1000);


            switch (vehicleState){
                case Dead: // Dead                               | GPS, off
                    break;
                case Sleep: // Sleep                              | GPS, 10m
                    break;
                case Idle: // Idle                               | GPS, 1m
                    break;
                case Powered: // Powered                            | GPS, 10s
                    break;
                case Running: // Running (Engine on, Parked)        | GPS, 1s
                    break;
                case Driving: // Driving (Engine on, in Gear)       | GPS, 1s
                    break;

            }
        }
    };



    private boolean initLocationListener() {
        Log.d(TAG, "initLocationListener");
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                currentLocation = location;
                Log.d(TAG, "onLocationChanged");

                Intent intent = new Intent("location_update");
                intent.putExtra("location", currentLocation);
                sendBroadcast(intent);
            }

            // Make a fuss if location is not turned on.
            @Override
            public void onProviderDisabled(String s) {
                Log.d(TAG, "onProviderDisabled");
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);

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

    private BroadcastReceiver btBroadCastReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "onReceive: FOUND DEVICE: " + device.getName() + " with address, " + device.getAddress());
                if (phoneKeyList.stream().anyMatch(o -> o.getBluetoothMac().equals(device.getAddress()))) {
                    Log.d(TAG, "onReceive: Key was detected");
                    bluetoothAdapter.cancelDiscovery();
                    lastSawKey = System.currentTimeMillis();
                }
            }
        }
    };

    private void initBluetooth() {
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth Not Available", Toast.LENGTH_SHORT).show();
        } else {
            IntentFilter discoveredDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(btBroadCastReceiver, discoveredDevicesIntent);
        }
    }

    @SuppressLint("MissingPermission")
    private void discoverKey(){
        Log.d(TAG, "discoverKey: started");
        if(!bluetoothAdapter.isDiscovering()){
            bluetoothAdapter.startDiscovery();
            Log.d(TAG, "discoverKey: Started Discovery");
        }
        if(System.currentTimeMillis() - lastSawKey < 15000){
            btStatus = 1;
        }
        else{
            btStatus = 0;
        }

        Intent intent = new Intent("data_update");
        sendBroadcast(intent);
        Log.d(TAG, "discoverKey: stopped " + btStatus);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Getting the highest value only.
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
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}

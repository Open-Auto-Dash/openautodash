package com.openautodash;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.openautodash.services.MainForegroundService;
import com.openautodash.ui.MapFragment;
import com.openautodash.ui.TelemetryFragment;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    //Permission request codes
    private static final int LOCATION_REQUEST_CODE = 350;
    private static final int BLUETOOTH_REQUEST_CODE = 351;
    private static final int WRITE_SETTINGS_REQUEST_CODE = 351;

    //Broadcast Receivers
    private BroadcastReceiver clockReceiver;

    //Views
    private TextView clock;
    private TextView gpsStatus;

    private ImageView bluetoothStatusIcon;


    //Fragments...........................
    Fragment fragmentRight;
    Fragment fragmentLeft;
    MenuItem menuItem;

    // Service
    private MainForegroundService mainForegroundService;

    //ViewModel
    private LiveDataViewModel liveDataViewModel;


    //Global Variables..............................
    private SensorEventListener sensorEventListenerLight;
    private final SimpleDateFormat clockTime = new SimpleDateFormat("h:mm a");

    private Location location;
    private GnssStatus gnssStatus;

    private SensorManager sensorManager;
    private long lastBrightnessTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        // Init views
        clock = findViewById(R.id.tv_m_clock);
        bluetoothStatusIcon = findViewById(R.id.iv_m_bluetooth_status);


        // Create a ViewModel instance in the activity scope
        liveDataViewModel = new ViewModelProvider(this).get(LiveDataViewModel.class);

        //Set fullscreen
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        keepScreenOn(true);

        //Check required permissions.
        if(locationPermissionCheck()){
            startAndConnectMainService();
        }

        if(systemWritePermissionCheck()){
//            calculateScreenBrightness();
        }

        fragmentRight = new MapFragment();
        fragmentLeft = new TelemetryFragment();

        getSupportFragmentManager()
                .beginTransaction().replace(R.id.fragmentRightContainer, fragmentRight)
                .commit();
        getSupportFragmentManager()
                .beginTransaction().replace(R.id.fragmentLeftContainer, fragmentLeft)
                .commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unBindService();
    }

    @Override
    protected void onResume() {
        super.onResume();

        clock.setText(clockTime.format(new Date()));

        if(clockReceiver == null){
            clockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0)
                        clock.setText(clockTime.format(new Date()));
                }
            };
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindService();
    }

    public void startAndConnectMainService(){
        Log.d(TAG, "startAndConnectMainService");
        Intent intent = new Intent(getApplicationContext(), MainForegroundService.class);
        if (!isMyServiceRunning(MainForegroundService.class)) {
            ContextCompat.startForegroundService(this, intent);
            Log.d(TAG, "startAndConnectMainService, startForeground");
        }
        connectToMainService();
    }

    public void connectToMainService(){
        Intent intent = new Intent(this, MainForegroundService.class);
        if(mainForegroundService == null){
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "connectToMainService");
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MainForegroundService.MainForegroundServiceBinder mainForegroundServiceBinder = (MainForegroundService.MainForegroundServiceBinder) binder;
            mainForegroundService = mainForegroundServiceBinder.getService();

            mainForegroundService.getLocationLiveData().observe(MainActivity.this, location -> {
                liveDataViewModel.setLocation(location);
                Log.d(TAG, String.format("onServiceConnected: lat: %f, lng: %f ", location.getLatitude(), location.getLongitude()));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mainForegroundService = null;
        }
    };

    private void unBindService(){
        if(mainForegroundService != null){
            Log.d(TAG, "unBindService...");
            unbindService(serviceConnection);
            mainForegroundService = null;
        }
    }

    public LiveDataViewModel getViewModel() {
        return liveDataViewModel;
    }

    public boolean locationPermissionCheck(){
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user
            }
            // Request the permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    public boolean systemWritePermissionCheck(){
        if (!Settings.System.canWrite(this)) {
            // Permission is not granted
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_permission_write_settings);
            dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(this.getResources(), R.drawable.background_dialog, null));
            dialog.getWindow().setLayout((int)(ViewGroup.LayoutParams.WRAP_CONTENT), ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setCancelable(true);

            dialog.findViewById(R.id.b_dialog_write_permission_cancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.cancel();
                }
            });

            dialog.findViewById(R.id.b_dialog_write_permission_continue).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getApplication().getPackageName()));
                    startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE);
                }
            });
            dialog.show();
            return false;
        }
        return true;
    }

    void keepScreenOn(boolean on){
        //Keep screen on call the time.
        if(on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else{
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }


    void calculateScreenBrightness(){
        //Light sensor
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        sensorEventListenerLight = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float floatSensorValue = event.values[0]; // lux
                if(System.currentTimeMillis() - lastBrightnessTime > 1000) {
                    lastBrightnessTime = System.currentTimeMillis();
                    if (floatSensorValue < 50) {
                        setBrightness(55);
                    } else {
                        setBrightness(355);
                    }
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        sensorManager.registerListener(sensorEventListenerLight, sensorLight, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void setBrightness(int brightness){
        // Get the current window attributes
        WindowManager.LayoutParams layoutpars = getWindow().getAttributes();
        // Set the brightness of this window
        layoutpars.screenBrightness = brightness / 255f;
        // Apply attribute changes to this window
        getWindow().setAttributes(layoutpars);
    }

    public boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
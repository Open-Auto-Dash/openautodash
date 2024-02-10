package com.openautodash;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.openautodash.adapters.MenuAdapter;
import com.openautodash.enums.Units;
import com.openautodash.interfaces.WeatherUpdateCallback;
import com.openautodash.object.Weather;
import com.openautodash.services.MainForegroundService;
import com.openautodash.ui.MapFragment;
import com.openautodash.ui.MenuFragment;
import com.openautodash.ui.TelemetryFragment;
import com.openautodash.utilities.ModemInfo;
import com.openautodash.utilities.WeatherManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WeatherUpdateCallback {
    private static final String TAG = "MainActivity";

    //Permission request codes
    private static final int LOCATION_REQUEST_CODE = 350;
    private static final int BLUETOOTH_REQUEST_CODE = 351;
    private static final int WRITE_SETTINGS_REQUEST_CODE = 351;

    //Broadcast Receivers
    private BroadcastReceiver clockReceiver;

    //Views
    private TextView clock;
    private TextView temp;
    private ImageView windDirection;
    private ImageView bluetoothStatusIcon;
    private ImageView lteStatusView;
    private TextView lteNetworkType;
    private View bottomNavBar;


    //Fragments...........................
    Fragment fragmentRight;
    Fragment fragmentLeft;
    Fragment fragmentMenu;

    // Service
    private MainForegroundService mainForegroundService;

    //ViewModel
    private LiveDataViewModel liveDataViewModel;


    //Global Variables..............................
    private SensorEventListener sensorEventListenerLight;
    private final SimpleDateFormat clockTime = new SimpleDateFormat("h:mm a");

    private WeatherManager weatherManager;
    private Location currentLocation;
    private Location lastWeatherUpdateLocation;

    private int currentMenuShowing;

    private Handler handler;
    private Runnable runnable;
    private ModemInfo modemInfo;
    WifiManager wifiManager;
    WifiInfo wifiInfo;

    private SensorManager sensorManager;
    private long lastBrightnessTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        turnScreenOn(this);
        setContentView(R.layout.activity_main);
        fragmentRight = new MapFragment();
        fragmentLeft = new TelemetryFragment();
        fragmentMenu = new MenuFragment();

        getSupportFragmentManager()
                .beginTransaction().replace(R.id.fragmentRightContainer, fragmentRight)
                .commit();
        getSupportFragmentManager()
                .beginTransaction().replace(R.id.fragmentLeftContainer, fragmentLeft)
                .commit();

        // Init views
        clock = findViewById(R.id.tv_m_clock);
        temp = findViewById(R.id.tv_main_temp);
        windDirection = findViewById(R.id.iv_m_wind_dir);
        bluetoothStatusIcon = findViewById(R.id.iv_m_bluetooth_status);
        lteStatusView = findViewById(R.id.iv_main_lte_signal);
        lteNetworkType = findViewById(R.id.tv_main_signal_network_type);

        bottomNavBar = findViewById(R.id.bottomNavBar);
        updateLayoutWidth();


        // Create a ViewModel instance in the activity scope
        liveDataViewModel = new ViewModelProvider(this).get(LiveDataViewModel.class);
        weatherManager = new WeatherManager(this, currentLocation, this);
        modemInfo = new ModemInfo(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        //Click Listeners
        lteStatusView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });

        //Set fullscreen
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        keepScreenOn(true);

        //Check required permissions.
        if (locationPermissionCheck()) {
            startAndConnectMainService();
        }

        if (systemWritePermissionCheck()) {
//            calculateScreenBrightness();
        }


        ///////////////
        startSignalStrengthUpdates();


    }

    @Override
    protected void onPause() {
        super.onPause();
        unBindService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAndConnectMainService();

        clock.setText(clockTime.format(new Date()));
//        temp.setText(weatherManager.getCurrentTemp(currentLocation, Units.Metric));


        if (clockReceiver == null) {
            clockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0)
                        clock.setText(clockTime.format(new Date()));
                }
            };
        }
        registerReceiver(clockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayoutWidth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindService();
    }

    public void toggleSettings(View view){
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch (currentMenuShowing){
            case 1:
                // If the menu is showing, remove it by sliding down
                transaction.setCustomAnimations(R.anim.stay, R.anim.slide_out_bottom);
                transaction.remove(getSupportFragmentManager().findFragmentById(R.id.menuContainer));
                currentMenuShowing = 0;
                break;
            default:
                transaction.setCustomAnimations(R.anim.slide_in_bottom, R.anim.stay);
                transaction.replace(R.id.menuContainer, new MenuFragment());
                currentMenuShowing = 1;
                break;
        }
        transaction.commit();
    }

    public void startAndConnectMainService() {
        Log.d(TAG, "startAndConnectMainService");
        Intent intent = new Intent(getApplicationContext(), MainForegroundService.class);
        if (!isMyServiceRunning(MainForegroundService.class)) {
            ContextCompat.startForegroundService(this, intent);
            Log.d(TAG, "startAndConnectMainService, startForeground");
        }
        connectToMainService();
    }

    public void connectToMainService() {
        Intent intent = new Intent(this, MainForegroundService.class);
        if (mainForegroundService == null) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "connectToMainService");
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MainForegroundService.MainForegroundServiceBinder mainForegroundServiceBinder = (MainForegroundService.MainForegroundServiceBinder) binder;
            mainForegroundService = mainForegroundServiceBinder.getService();

            mainForegroundService.getLocationLiveData().observe(MainActivity.this, location -> {
                liveDataViewModel.setLocation(location);
                currentLocation = location;

                setWeather();
                Log.d(TAG, String.format("onServiceConnected: lat: %f, lng: %f ", location.getLatitude(), location.getLongitude()));
            });
            mainForegroundService.getBluetoothState().observe(MainActivity.this, btStatus ->
            {
                if(btStatus == 1){
                    bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_bluetooth_nearby));
                    keepScreenOn(true);
                }
                else{
                    bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_bluetooth));
                    keepScreenOn(false);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mainForegroundService = null;
        }
    };

    private void unBindService() {
        if (mainForegroundService != null) {
            Log.d(TAG, "unBindService...");
            unbindService(serviceConnection);
            mainForegroundService = null;
        }
    }

    public LiveDataViewModel getViewModel() {
        return liveDataViewModel;
    }


    private void startSignalStrengthUpdates() {
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo.getSSID().contains("My Fusion")) {
                    modemInfo.updateInfo();
                    if(modemInfo.getCurrentNetworkType() != null){
                        int type = Integer.parseInt(modemInfo.getCurrentNetworkType());
                        switch (type){
                            case 0:
                                lteNetworkType.setText("");
                                break;
                            case 1:
                            case 2:
                                lteNetworkType.setText("2G");
                                break;
                            case 3:
                            case 4:
                            case 5:
                            case 6:
                                lteNetworkType.setText("3G");
                                break;
                            case 7:
                                lteNetworkType.setText("3G+");
                                break;
                            case 8:
                            case 9:
                                lteNetworkType.setText("4G");
                                break;
                            case 19:
                                lteNetworkType.setText("LTE");
                                break;
                            default:
                                lteNetworkType.setText("D" + type);
                        }
                    }
                    if (modemInfo.getSignalIcon() != null) {
                        switch (Integer.parseInt(modemInfo.getSignalIcon())) {
                            case 1:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_1));
                                break;
                            case 2:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_2));
                                break;
                            case 3:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_3));
                                break;
                            case 4:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_4));
                                break;
                            case 5:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_5));
                                break;
                            default:
                                lteStatusView.setImageDrawable(getResources().getDrawable(R.drawable.signal_lte_0));
                                break;
                        }
                    }
                }
                else{
                    if(isInternetConnected(getApplicationContext())){
                        lteStatusView.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.signal_wifi_1));
                    }
                    else {
                        lteStatusView.setImageDrawable(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.signal_wifi_0));
                    }
                }
                handler.postDelayed(this, 5000); // 5000 milliseconds = 5 seconds
            }
        };
        handler.post(runnable);
    }

    private void stopSignalStrengthUpdates() {
        handler.removeCallbacks(runnable);
    }

    public boolean locationPermissionCheck() {
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

    public boolean systemWritePermissionCheck() {
        if (!Settings.System.canWrite(this)) {
            // Permission is not granted
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_permission_write_settings);
            dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(this.getResources(), R.drawable.background_dialog, null));
            dialog.getWindow().setLayout((int) (ViewGroup.LayoutParams.WRAP_CONTENT), ViewGroup.LayoutParams.WRAP_CONTENT);
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

    public void updateTemp(View view) {
    }

    public void setWeather(){
        weatherManager.getCurrentWeather(currentLocation);
    }

    @Override
    public void onComplete(Weather weather) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                weatherManager.syncWeather();
                temp.setText(weather.getTemp() + "°C");

                float relativeAngle = (float)weather.getWindDeg() - currentLocation.getBearing();
                if (relativeAngle < 0) {
                    relativeAngle += 360;
                } else if (relativeAngle > 360) {
                    relativeAngle -= 360;
                }
                windDirection.setRotation(relativeAngle);
            }
        });
    }

    private void updateLayoutWidth() {
        Log.d(TAG, "updateLayoutWidth");
        // Calculate screen width
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

        Log.d(TAG, "updateLayoutWidth: Screen Width is: " + screenWidth);

        // Set the desired minimum width in dp
        int desiredMinWidth = 1800;

        // Set the layout_width based on screen width
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) bottomNavBar.getLayoutParams();
        if (screenWidth > desiredMinWidth) {
            Log.d(TAG, "updateLayoutWidth: " + screenWidth);
            layoutParams.width = screenWidth;
        } else {
            Log.d(TAG, "updateLayoutWidth: " + desiredMinWidth);
            layoutParams.width = desiredMinWidth;
        }
        bottomNavBar.setLayoutParams(layoutParams);
    }

    void keepScreenOn(boolean on) {
        //Keep screen on call the time.
        Log.d(TAG, "keepScreenOn: " + on);
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void turnScreenOn(Activity activity) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }


    void calculateScreenBrightness() {
        //Light sensor
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        sensorEventListenerLight = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float floatSensorValue = event.values[0]; // lux
                if (System.currentTimeMillis() - lastBrightnessTime > 1000) {
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


    void setBrightness(int brightness) {
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

    public static boolean isInternetConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetwork() != null && cm.getNetworkCapabilities(cm.getActiveNetwork()) != null;
    }

    public void wakeUpDevice(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "MyApp::WakeLockTag");

        // Release the wake lock after a certain period. You might want to adjust this.
        wakeLock.release();
    }
}
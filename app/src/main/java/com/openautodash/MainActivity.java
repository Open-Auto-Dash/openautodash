package com.openautodash;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

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
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.openautodash.enums.Units;
import com.openautodash.interfaces.VehicleControlCallback;
import com.openautodash.interfaces.WeatherUpdateCallback;
import com.openautodash.object.Weather;
import com.openautodash.services.MainForegroundService;
import com.openautodash.ui.MapFragment;
import com.openautodash.ui.MenuFragment;
import com.openautodash.ui.TelemetryFragment;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.ModemInfo;
import com.openautodash.utilities.WeatherManager;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements WeatherUpdateCallback, VehicleControlCallback {
    private static final String TAG = "MainActivity";

    // Permission request codes
    private static final int LOCATION_REQUEST_CODE = 350;
    private static final int BLUETOOTH_REQUEST_CODE = 351;
    private static final int WRITE_SETTINGS_REQUEST_CODE = 351;

    // UI Components
    private TextView clock;
    private TextView temp;
    private TextView brightnessCrap;
    private TextView lteNetworkType;
    private ImageView windDirection;
    private ImageView bluetoothStatusIcon;
    private ImageView lteStatusView;
    private View bottomNavBar;

    // Control Panel Buttons
    private ImageView menuMain;
    private ImageView menuMusic;
    private ImageView menuSeatLeft;
    private ImageView menuSeatRight;
    private ImageView menuTempLeftUp;
    private ImageView menuTempLeftDown;
    private ImageView menuTempRightUp;
    private ImageView menuTempRightDown;
    private ImageView menuFan;
    private ImageView menuDefrost;
    private ImageView menuVolUp;
    private ImageView menuVolDown;

    // Fragments
    private Fragment fragmentRight;
    private Fragment fragmentLeft;
    private Fragment fragmentMenu;
    private int currentMenuShowing;

    // Service and ViewModel
    private MainForegroundService mainForegroundService;
    private LiveDataViewModel liveDataViewModel;

    // Sensors and Settings
    private SensorManager sensorManager;
    private SensorEventListener sensorEventListenerLight;
    private long lastBrightnessTime;
    private int[] brightnessSetting;
    private int darkModeSetting;
    private boolean isDarkMode;
    private int[] brightnessBuffer = {0, 0, 0, 0, 0, 0, 0, 0, 0};
    private LocalSettings localSettings;

    // Network and Connectivity
    private ModemInfo modemInfo;
    private WifiManager wifiManager;
    private WifiInfo wifiInfo;
    private BroadcastReceiver clockReceiver;
    private Handler handler;
    private Runnable runnable;

    // Weather
    private WeatherManager weatherManager;
    private Location currentLocation;
    private Location lastWeatherUpdateLocation;
    private final SimpleDateFormat clockTime = new SimpleDateFormat("h:mm a");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        initializeWindow();
        initializeFragments();
        initializeViews();
        setupViewModel();
        setupNetworkMonitoring();
        setupControlPanel();

        if (locationPermissionCheck()) {
            startAndConnectMainService();
        }

        if (systemWritePermissionCheck()) {
            calculateScreenBrightness();
        }

        startSignalStrengthUpdates();
    }

    private void initializeWindow() {
        turnScreenOn(this);
        setContentView(R.layout.activity_main);

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);

        keepScreenOn(true);
    }

    private void initializeFragments() {
        fragmentRight = new MapFragment();
        fragmentLeft = new TelemetryFragment();
        fragmentMenu = new MenuFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentRightContainer, fragmentRight)
                .commit();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentLeftContainer, fragmentLeft)
                .commit();
    }

    private void initializeViews() {
        localSettings = new LocalSettings(this);

        // Status bar elements
        clock = findViewById(R.id.tv_m_clock);
        temp = findViewById(R.id.tv_main_temp);
        windDirection = findViewById(R.id.iv_m_wind_dir);
        bluetoothStatusIcon = findViewById(R.id.iv_m_bluetooth_status);
        lteStatusView = findViewById(R.id.iv_main_lte_signal);
        lteNetworkType = findViewById(R.id.tv_main_signal_network_type);
        brightnessCrap = findViewById(R.id.brightesscrap);
        bottomNavBar = findViewById(R.id.bottomNavBar);

        updateLayoutWidth();

        lteStatusView.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void setupViewModel() {
        liveDataViewModel = new ViewModelProvider(this).get(LiveDataViewModel.class);
        weatherManager = new WeatherManager(this, currentLocation, this);
    }

    private void setupNetworkMonitoring() {
        modemInfo = new ModemInfo(this);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private void setupControlPanel() {
        initializeControlButtons();
        setupControlButtonListeners();
    }

    private void initializeControlButtons() {
        menuMain = findViewById(R.id.iv_bottom_nav_bar_settings);
        menuMusic = findViewById(R.id.iv_bottom_nav_bar_music);
        menuSeatLeft = findViewById(R.id.iv_bottom_nav_bar_left_seat_heater);
        menuTempLeftUp = findViewById(R.id.iv_bottom_nav_bar_left_temp_up);
        menuTempLeftDown = findViewById(R.id.iv_bottom_nav_bar_left_temp_down);
        menuFan = findViewById(R.id.iv_bottom_nav_bar_fan_setting_icon);
        menuTempRightUp = findViewById(R.id.iv_bottom_nav_bar_right_temp_up);
        menuTempRightDown = findViewById(R.id.iv_bottom_nav_bar_right_temp_down);
        menuSeatRight = findViewById(R.id.iv_bottom_nav_bar_right_seat_heater);
        menuDefrost = findViewById(R.id.iv_bottom_nav_bar_defrost);
        menuVolUp = findViewById(R.id.iv_bottom_nav_bar_vol_up);
        menuVolDown = findViewById(R.id.iv_bottom_nav_bar_vol_down);
    }

    private void setupControlButtonListeners() {
        menuMain.setOnClickListener(v -> {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            if (currentMenuShowing == 1) {
                transaction.setCustomAnimations(R.anim.stay, R.anim.slide_out_bottom);
                transaction.remove(getSupportFragmentManager().findFragmentById(R.id.menuContainer));
                currentMenuShowing = 0;
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_bottom, R.anim.stay);
                transaction.replace(R.id.menuContainer, new MenuFragment());
                currentMenuShowing = 1;
            }
            transaction.commit();
        });
        menuMain.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showEngineDialog();
                return false;
            }
        });
        menuMusic.setOnClickListener(v -> {
            try {
                Intent intent = this.getPackageManager().getLaunchIntentForPackage("com.spotify.music");
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(intent);
                } else {
                    // Spotify isn't installed
                    // Optionally open Play Store to install Spotify
                    try {
                        this.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.spotify.music")));
                    } catch (android.content.ActivityNotFoundException e) {
                        this.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        menuSeatLeft.setOnClickListener(v -> {
        });
        menuTempLeftUp.setOnClickListener(v -> {
        });
        menuTempLeftDown.setOnClickListener(v -> {
        });
        menuFan.setOnClickListener(v -> {
        });
        menuTempRightUp.setOnClickListener(v -> {
        });
        menuTempRightDown.setOnClickListener(v -> {
        });
        menuSeatRight.setOnClickListener(v -> {
        });
        menuDefrost.setOnClickListener(v -> toggleDarkMode());
        menuVolUp.setOnClickListener(v -> setVolume(AudioManager.ADJUST_RAISE));
        menuVolDown.setOnClickListener(v -> setVolume(AudioManager.ADJUST_LOWER));
    }

    private void setVolume(int dir) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
    }

    public void toggleDarkMode() {
        if (sensorManager != null && sensorEventListenerLight != null) {
            Log.d(TAG, "calculateScreenBrightness: unregisterListener");
            sensorManager.unregisterListener(sensorEventListenerLight);
            sensorEventListenerLight = null;
        }

        isDarkMode = !isDarkMode;
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        Log.d(TAG, "calculateScreenBrightness: " + (isDarkMode ? "ON" : "OFF"));
        localSettings.isNight(isDarkMode);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unBindService();
        if (clockReceiver != null) {
            unregisterReceiver(clockReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startAndConnectMainService();
        brightnessSetting = localSettings.getBrightnessSetting();
        darkModeSetting = localSettings.getNightModeSetPoint();
        clock.setText(clockTime.format(new Date()));

        setupClockReceiver();
    }

    private void setupClockReceiver() {
        if (clockReceiver == null) {
            clockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0) {
                        clock.setText(clockTime.format(new Date()));
                    }
                }
            };
        }
        registerReceiver(clockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindService();
        stopSignalStrengthUpdates();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayoutWidth();
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
            MainForegroundService.MainForegroundServiceBinder mainForegroundServiceBinder =
                    (MainForegroundService.MainForegroundServiceBinder) binder;
            mainForegroundService = mainForegroundServiceBinder.getService();
            mainForegroundService.setVehicleControlCallback(MainActivity.this);

            setupLocationObserver();
            setupBluetoothObserver();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mainForegroundService = null;
        }
    };

    private void setupLocationObserver() {
        mainForegroundService.getLocationLiveData().observe(MainActivity.this, location -> {
            liveDataViewModel.setLocation(location);
            currentLocation = location;
            setWeather();
            Log.d(TAG, String.format("Location updated: lat: %f, lng: %f",
                    location.getLatitude(), location.getLongitude()));
        });
    }

    private void setupBluetoothObserver() {
        mainForegroundService.getBluetoothState().observe(MainActivity.this, btStatus -> {
            if (btStatus == 1) {
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(
                        getApplicationContext(), R.drawable.ic_bluetooth_nearby));
                keepScreenOn(true);
            } else {
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(
                        getApplicationContext(), R.drawable.ic_bluetooth));
                keepScreenOn(false);
            }
        });
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
                    updateModemSignal();
                } else {
                    updateWifiSignal();
                }
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(runnable);
    }

    private void updateModemSignal() {
        modemInfo.updateInfo();
        if (modemInfo.getCurrentNetworkType() != null) {
            updateNetworkTypeDisplay(Integer.parseInt(modemInfo.getCurrentNetworkType()));
        }
        if (modemInfo.getSignalIcon() != null) {
            updateSignalIconDisplay(Integer.parseInt(modemInfo.getSignalIcon()));
        }
    }

    private void updateNetworkTypeDisplay(int type) {
        switch (type) {
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

    private void updateSignalIconDisplay(int signalStrength) {
        int iconResource;
        switch (signalStrength) {
            case 1:
                iconResource = R.drawable.signal_lte_1;
                break;
            case 2:
                iconResource = R.drawable.signal_lte_2;
                break;
            case 3:
                iconResource = R.drawable.signal_lte_3;
                break;
            case 4:
                iconResource = R.drawable.signal_lte_4;
                break;
            case 5:
                iconResource = R.drawable.signal_lte_5;
                break;
            default:
                iconResource = R.drawable.signal_lte_0;
                break;
        }
        lteStatusView.setImageDrawable(getResources().getDrawable(iconResource));
    }

    private void updateWifiSignal() {
        if (isInternetConnected(getApplicationContext())) {
            lteStatusView.setImageDrawable(AppCompatResources.getDrawable(
                    getApplicationContext(), R.drawable.signal_wifi_1));
        } else {
            lteStatusView.setImageDrawable(AppCompatResources.getDrawable(
                    getApplicationContext(), R.drawable.signal_wifi_0));
        }
    }

    private void stopSignalStrengthUpdates() {
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    public boolean locationPermissionCheck() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show explanation if needed
            }
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    public boolean systemWritePermissionCheck() {
        if (!Settings.System.canWrite(this)) {
            Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.dialog_permission_write_settings);
            dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(
                    this.getResources(), R.drawable.background_dialog, null));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.setCancelable(true);

            dialog.findViewById(R.id.b_dialog_write_permission_cancel)
                    .setOnClickListener(v -> dialog.cancel());

            dialog.findViewById(R.id.b_dialog_write_permission_continue)
                    .setOnClickListener(v -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + getApplication().getPackageName()));
                        startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE);
                    });
            dialog.show();
            return false;
        }
        return true;
    }

    public void showEngineDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_engine_menu);
        dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(
                this.getResources(), R.drawable.background_dialog, null));
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCancelable(true);

        dialog.findViewById(R.id.b_dialog_engine_close)
                .setOnClickListener(v -> dialog.cancel());


        dialog.show();
    }

    public void updateTemp(View view) {
        // Implement temperature update if needed
    }

    public void setWeather() {
        weatherManager.getCurrentWeather(currentLocation);
    }

    @Override
    public void onComplete(Weather weather) {
        runOnUiThread(() -> {
            weatherManager.syncWeather();
            temp.setText(weather.getTemp() + "°C");

            float relativeAngle = (float) weather.getWindDeg() - currentLocation.getBearing();
            if (relativeAngle < 0) {
                relativeAngle += 360;
            } else if (relativeAngle > 360) {
                relativeAngle -= 360;
            }
            windDirection.setRotation(relativeAngle);
        });
    }

    private void updateLayoutWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int desiredMinWidth = 1800;

        ConstraintLayout.LayoutParams layoutParams =
                (ConstraintLayout.LayoutParams) bottomNavBar.getLayoutParams();
        layoutParams.width = screenWidth > desiredMinWidth ? screenWidth : desiredMinWidth;
        bottomNavBar.setLayoutParams(layoutParams);

        Log.d(TAG, "Layout width updated to: " + layoutParams.width);
    }

    void keepScreenOn(boolean on) {
        Log.d(TAG, "keepScreenOn: " + on);
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void turnScreenOn(Activity activity) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    void calculateScreenBrightness() {
        sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor sensorLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        isDarkMode = localSettings.getIsNight();

        if (sensorEventListenerLight == null) {
            Log.d(TAG, "calculateScreenBrightness: init");
            sensorEventListenerLight = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    float floatSensorValue = event.values[0]; // lux
                    if (System.currentTimeMillis() - lastBrightnessTime > 1000) {
                        lastBrightnessTime = System.currentTimeMillis();
                        updateBrightnessDisplay(floatSensorValue);
                        updateBrightnessSettings((int) floatSensorValue);
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    // Not used
                }
            };
        }
        sensorManager.registerListener(sensorEventListenerLight, sensorLight,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void updateBrightnessDisplay(float sensorValue) {
        String crapString = (int) sensorValue + "br";
        brightnessCrap.setText(crapString);
        Log.d(TAG, "Raw brightness: " + sensorValue);
    }

    private void updateBrightnessSettings(int sensorValue) {
        // Update brightness buffer
        for (int i = brightnessBuffer.length - 1; i > 0; i--) {
            brightnessBuffer[i] = brightnessBuffer[i - 1];
        }
        brightnessBuffer[0] = sensorValue;

        // Calculate average brightness
        int totalBuffer = 0;
        for (int value : brightnessBuffer) {
            totalBuffer += value;
        }
        int avgBrightness = totalBuffer / brightnessBuffer.length;
        Log.d(TAG, "Averaged brightness: " + avgBrightness);

        // Set brightness based on thresholds
        if (avgBrightness > 500) {
            setBrightness(brightnessSetting[5]);
        } else if (avgBrightness > 400) {
            setBrightness(brightnessSetting[4]);
        } else if (avgBrightness > 100) {
            setBrightness(brightnessSetting[3]);
        } else if (avgBrightness > 40) {
            setBrightness(brightnessSetting[2]);
        } else if (avgBrightness > 10) {
            setBrightness(brightnessSetting[1]);
        } else {
            setBrightness(brightnessSetting[0]);
        }

        // Update dark mode if needed
        if (avgBrightness <= darkModeSetting) {
            if (!isDarkMode) {
                toggleDarkMode();
            }
        } else {
            if (isDarkMode) {
                toggleDarkMode();
            }
        }
    }

    void setBrightness(int brightness) {
        WindowManager.LayoutParams layoutpars = getWindow().getAttributes();
        layoutpars.screenBrightness = brightness / 255f;
        getWindow().setAttributes(layoutpars);
    }

    public void setBrightnessSettings(int[] settings) {
        brightnessSetting = settings;
    }

    private void unBindService() {
        if (mainForegroundService != null) {
            Log.d(TAG, "unBindService...");
            unbindService(serviceConnection);
            mainForegroundService = null;
        }
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

    public static boolean isKeyConnected() {
        return isKeyConnected();
    }

    @Override
    public void onLockCommand() {

    }

    @Override
    public void onUnlockCommand() {

    }

    @Override
    public void onStartCommand() {

    }

    @Override
    public void onStopCommand() {

    }

    @Override
    public void onLightsCommand(boolean on) {

    }
}
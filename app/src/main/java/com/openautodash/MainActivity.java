package com.openautodash;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.services.MainForegroundService; // Ensure correct import
import com.openautodash.ui.MapFragment;
import com.openautodash.ui.MenuFragment;
import com.openautodash.ui.TelemetryFragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Permission constants
    private static final int LOCATION_REQUEST_CODE = 350;
    private static final int WRITE_SETTINGS_REQUEST_CODE = 351;

    // The Single Source of Truth
    private VehicleRepository repository;

    // --- UI Components: Top Status Bar ---
    private TextView clockView;
    private TextView tempView;
    private ImageView windDirectionView;
    private ImageView bluetoothStatusIcon;
    private ImageView lteStatusView;
    private TextView lteNetworkType;
    private TextView brightnessDebugView; // "brightesscrap"

    // --- UI Components: Bottom Navigation ---
    // (Container logic handled natively by XML now)
    private ImageView menuMain;
    private ImageView menuMusic;
    private ImageView menuVolUp;
    private ImageView menuVolDown;

    // Climate Placeholders (Mapped from XML)
    private ImageView menuSeatLeft;
    private ImageView menuSeatRight;
    private ImageView menuTempLeftUp;
    private ImageView menuTempLeftDown;
    private ImageView menuTempRightUp;
    private ImageView menuTempRightDown;
    private ImageView menuFan;
    private ImageView menuDefrost;

    // Fragments
    private Fragment fragmentRight; // Map
    private Fragment fragmentLeft;  // Telemetry
    private int currentMenuShowing = 0;

    // System State
    private BroadcastReceiver clockReceiver;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("h:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing Dashboard");

        // 1. Initialize Repository
        repository = VehicleRepository.getInstance(this);

        // 2. Setup Window
        initializeWindow();
        setContentView(R.layout.activity_main);

        // 3. Bind Views
        initializeViews();

        // 4. Start Background Service
        startHardwareService();

        // 5. Connect UI to Data
        setupRepositoryObservers();

        // 6. Setup Fragments
        if (savedInstanceState == null) {
            initializeFragments();
        }

        // 7. Setup Button Listeners
        setupControlPanel();

        // 8. Permissions
        checkPermissions();
    }

    private void initializeWindow() {

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void initializeViews() {
        // --- Top Status Bar ---
        clockView = findViewById(R.id.tv_m_clock);
        tempView = findViewById(R.id.tv_main_temp);
        windDirectionView = findViewById(R.id.iv_m_wind_dir);
        bluetoothStatusIcon = findViewById(R.id.iv_m_bluetooth_status);
        lteStatusView = findViewById(R.id.iv_main_lte_signal);
        lteNetworkType = findViewById(R.id.tv_main_signal_network_type);
        brightnessDebugView = findViewById(R.id.brightesscrap);

        // --- Bottom Bar ---
        menuMain = findViewById(R.id.iv_bottom_nav_bar_settings);
        menuMusic = findViewById(R.id.iv_bottom_nav_bar_music);

        menuVolUp = findViewById(R.id.iv_bottom_nav_bar_vol_up);
        menuVolDown = findViewById(R.id.iv_bottom_nav_bar_vol_down);

        menuSeatLeft = findViewById(R.id.iv_bottom_nav_bar_left_seat_heater);
        menuSeatRight = findViewById(R.id.iv_bottom_nav_bar_right_seat_heater);
        menuTempLeftUp = findViewById(R.id.iv_bottom_nav_bar_left_temp_up);
        menuTempLeftDown = findViewById(R.id.iv_bottom_nav_bar_left_temp_down);
        menuTempRightUp = findViewById(R.id.iv_bottom_nav_bar_right_temp_up);
        menuTempRightDown = findViewById(R.id.iv_bottom_nav_bar_right_temp_down);
        menuFan = findViewById(R.id.iv_bottom_nav_bar_fan_setting_icon);
        menuDefrost = findViewById(R.id.iv_bottom_nav_bar_defrost);

        // Quick shortcut to Wifi settings from signal icon
        lteStatusView.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void setupRepositoryObservers() {
        // 1. Screen Brightness & Debug Text
        repository.getScreenBrightness().observe(this, brightness -> {
            // Update Window brightness
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = brightness;
            getWindow().setAttributes(lp);

            // Update Debug TextView ("120br")
            int rawVal = (int) (brightness * 255);
            brightnessDebugView.setText(rawVal + "br");
        });

        // 2. Night Mode (Theme)
        repository.getIsNightMode().observe(this, isNight -> {
            int currentMode = AppCompatDelegate.getDefaultNightMode();
            int targetMode = isNight ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (currentMode != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });

        // 3. Bluetooth Status & Screen Logic
        repository.getBluetoothState().observe(this, isConnected -> {
            if (isConnected) {
                // Key is here: Keep screen ON and show Blue Icon
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_bluetooth_nearby));
            } else {
                // Key is gone: Let screen SLEEP and show Grey Icon
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_bluetooth));
            }
        });

        // 4. Weather & Wind Direction
        repository.getWeather().observe(this, weather -> {
            if (weather != null) {
                tempView.setText(String.format(Locale.US, "%d°C", weather.getTemp()));

                // Calculate relative wind direction based on Car Heading
                Location currentLocation = repository.getLocation().getValue();
                float carBearing = (currentLocation != null) ? currentLocation.getBearing() : 0f;

                float relativeAngle = (float) weather.getWindDeg() - carBearing;
                if (relativeAngle < 0) relativeAngle += 360;
                if (relativeAngle > 360) relativeAngle -= 360;

                windDirectionView.setRotation(relativeAngle);
            }
        });

        // 5. Location Data (Updates every second)
        // FIX: We must observe location here to rotate the arrow when the CAR turns
        repository.getLocation().observe(this, location -> {
            if (location != null) {
                updateWindDirection();
            }
        });

        // 6. Network Signal & Type
        repository.getNetworkStatus().observe(this, status -> {
            updateNetworkUI(status);
        });
    }

    private void updateWindDirection() {
        com.openautodash.object.Weather weather = repository.getWeather().getValue();
        Location location = repository.getLocation().getValue();

        if (weather != null && location != null) {
            float windBearing = (float) weather.getWindDeg();
            float carBearing = location.getBearing();

            // Calculate relative angle (Wind - Car)
            float relativeAngle = windBearing - carBearing;

            // Normalize to 0-360
            if (relativeAngle < 0) relativeAngle += 360;
            if (relativeAngle > 360) relativeAngle -= 360;

            windDirectionView.setRotation(relativeAngle);
        }
    }

    private void updateNetworkUI(VehicleRepository.NetworkStatus status) {
        // Set Signal Icon
        int iconRes = R.drawable.signal_lte_0; // Default empty

        if (status.isWifi) {
            iconRes = (status.signalStrength > 0) ? R.drawable.signal_wifi_1 : R.drawable.signal_wifi_0;
        } else {
            switch (status.signalStrength) {
                case 1: iconRes = R.drawable.signal_lte_1; break;
                case 2: iconRes = R.drawable.signal_lte_2; break;
                case 3: iconRes = R.drawable.signal_lte_3; break;
                case 4: iconRes = R.drawable.signal_lte_4; break;
                case 5: iconRes = R.drawable.signal_lte_5; break;
            }
        }
        lteStatusView.setImageDrawable(AppCompatResources.getDrawable(this, iconRes));

        // Set Text (LTE, 3G, etc.)
        String typeText = "";
        if (!status.isWifi) {
            switch (status.networkType) {
                case 19: typeText = "LTE"; break;
                case 1: case 2: typeText = "2G"; break;
                case 3: case 8: case 9: typeText = "3G"; break;
                default: typeText = status.networkType > 0 ? "D" + status.networkType : "";
            }
        }
        lteNetworkType.setText(typeText);
    }

    private void initializeFragments() {
        fragmentRight = new MapFragment();
        fragmentLeft = new TelemetryFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentRightContainer, fragmentRight)
                .replace(R.id.fragmentLeftContainer, fragmentLeft)
                .commit();
    }

    private void setupControlPanel() {
        menuMain.setOnClickListener(v -> toggleMenu());
        menuMain.setOnLongClickListener(v -> {
            showEngineDialog();
            return true;
        });

        menuMusic.setOnClickListener(v -> launchSpotify());

        menuVolUp.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_RAISE));
        menuVolDown.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_LOWER));

        // Placeholder for future Climate implementation
        View.OnClickListener notImplemented = v -> Log.d(TAG, "Climate feature pending");
        menuSeatLeft.setOnClickListener(notImplemented);
        menuTempLeftUp.setOnClickListener(notImplemented);
        menuTempLeftDown.setOnClickListener(notImplemented);
    }

    private void toggleMenu() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (currentMenuShowing == 1) {
            // Close Menu
            transaction.setCustomAnimations(R.anim.stay, R.anim.slide_out_bottom);
            Fragment menu = getSupportFragmentManager().findFragmentById(R.id.menuContainer);
            if (menu != null) transaction.remove(menu);
            currentMenuShowing = 0;
        } else {
            // Open Menu
            transaction.setCustomAnimations(R.anim.slide_in_bottom, R.anim.stay);
            transaction.replace(R.id.menuContainer, new MenuFragment());
            currentMenuShowing = 1;
        }
        transaction.commit();
    }

    private void launchSpotify() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                // Fallback Intent
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Spotify launch failed", e);
        }
    }

    private void adjustVolume(int direction) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }

    private void startHardwareService() {
        Intent intent = new Intent(this, MainForegroundService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    // --- Lifecycle & System Events ---

    @Override
    protected void onResume() {
        super.onResume();
        // Clock Ticker
        clockView.setText(clockFormat.format(new Date()));
        clockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
                    clockView.setText(clockFormat.format(new Date()));
                }
            }
        };
        registerReceiver(clockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (clockReceiver != null) {
            unregisterReceiver(clockReceiver);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // XML handles layout width now
    }

    // --- Permissions & Dialogs ---

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        if (!Settings.System.canWrite(this)) {
            showWritePermissionDialog();
        }
    }

    private void showWritePermissionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_permission_write_settings);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.background_dialog, null));
        }
        dialog.findViewById(R.id.b_dialog_write_permission_continue).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, WRITE_SETTINGS_REQUEST_CODE);
            dialog.dismiss();
        });
        dialog.findViewById(R.id.b_dialog_write_permission_cancel).setOnClickListener(v -> dialog.cancel());
        dialog.show();
    }

    public void showEngineDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_engine_menu);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.background_dialog, null));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.findViewById(R.id.b_dialog_engine_close).setOnClickListener(v -> dialog.cancel());
        dialog.show();
    }

    // Public method required for XML onClick="updateTemp"
    public void updateTemp(View view) {
        // Triggers a manual weather refresh via Repo if needed
    }
}
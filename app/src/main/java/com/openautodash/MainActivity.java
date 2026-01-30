package com.openautodash;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.services.MainForegroundService;
import com.openautodash.ui.MapFragment;
import com.openautodash.ui.MapsOverlayFragment;
import com.openautodash.ui.MenuFragment;
import com.openautodash.ui.TelemetryFragment;
import com.openautodash.ui.TopBarFragment;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Permission constants
    private static final int LOCATION_REQUEST_CODE = 350;
    private static final int WRITE_SETTINGS_REQUEST_CODE = 351;

    private VehicleRepository repository;
    private int currentMenuShowing = 0;

    // --- Bottom Bar UI Components (Static) ---
    private ImageView menuMain, menuMusic, menuVolUp, menuVolDown;
    private ImageView menuSeatLeft, menuSeatRight;
    private ImageView menuTempLeftUp, menuTempLeftDown, menuTempRightUp, menuTempRightDown;
    private ImageView menuFan, menuDefrost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Initializing App StartUp");

        // 1. Initialize Repository
        repository = VehicleRepository.getInstance(this);

        // 2. Setup Window (Full Screen / Immersive)
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_main);

        // 3. Start Background Service
        startHardwareService();

        // 4. Setup Observers (Global Night Mode)
        setupRepositoryObservers();

        // 5. Setup Fragments (Map, Telemetry, TopBar)
        initializeFragments();

        // 6. Setup Bottom Bar (Static logic)
        setupBottomBar();

        // 7. Permissions
        checkPermissions();
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Map (Right) - Created once, never destroyed
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentRightContainer) == null) {
            transaction.replace(R.id.fragmentRightContainer, new MapFragment());
        }

        // Telemetry (Left)
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentLeftContainer) == null) {
            transaction.replace(R.id.fragmentLeftContainer, new TelemetryFragment());
        }

        // Top Bar (Status)
        if (getSupportFragmentManager().findFragmentById(R.id.topBarContainer) == null) {
            transaction.replace(R.id.topBarContainer, new TopBarFragment());
        }

        // MAP OVERLAY (Search & Nav UI)
        if (getSupportFragmentManager().findFragmentById(R.id.mapOverlayContainer) == null) {
            transaction.replace(R.id.mapOverlayContainer, new MapsOverlayFragment());
        }

        transaction.commitNow();
    }

    private void setupBottomBar() {
        // Find Views
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

        // Listeners
        menuMain.setOnClickListener(v -> toggleMenu());
        menuMain.setOnLongClickListener(v -> {
            showEngineDialog();
            return true;
        });

        menuMusic.setOnClickListener(v -> launchSpotify());
        menuVolUp.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_RAISE));
        menuVolDown.setOnClickListener(v -> adjustVolume(AudioManager.ADJUST_LOWER));

        // Placeholders
        View.OnClickListener notImplemented = v -> Log.d(TAG, "Climate feature pending");
        menuSeatLeft.setOnClickListener(notImplemented);
        menuSeatRight.setOnClickListener(notImplemented);
        menuTempLeftUp.setOnClickListener(notImplemented);
        menuTempLeftDown.setOnClickListener(notImplemented);
        menuTempRightUp.setOnClickListener(notImplemented);
        menuTempRightDown.setOnClickListener(notImplemented);
        menuFan.setOnClickListener(notImplemented);
        menuDefrost.setOnClickListener(notImplemented);
    }

    // --- THEME & CONFIGURATION HANDLING ---

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Theme Changed: Reloading UI Fragments");

        getTheme().applyStyle(R.style.Theme_OpenAutoDash, true);

        // Update Root Background
        int backgroundColor = ContextCompat.getColor(this, R.color.colorBackgroundDefault);
        View root = findViewById(R.id.root_layout_container);
        if (root != null) root.setBackgroundColor(backgroundColor);

        // --- NUCLEAR OPTION: Kill Zombies ---
        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();

        // Remove old instances explicitly
        Fragment oldTele = getSupportFragmentManager().findFragmentById(R.id.fragmentLeftContainer);
        if (oldTele != null) tr.remove(oldTele);

        Fragment oldTop = getSupportFragmentManager().findFragmentById(R.id.topBarContainer);
        if (oldTop != null) tr.remove(oldTop);

        Fragment oldOverlay = getSupportFragmentManager().findFragmentById(R.id.mapOverlayContainer);
        if (oldOverlay != null) tr.remove(oldOverlay);

        tr.commitNow(); // Ensure they are dead before adding new ones

        // --- Re-Add Fresh Instances (New Theme) ---
        FragmentTransaction addTr = getSupportFragmentManager().beginTransaction();
        addTr.replace(R.id.fragmentLeftContainer, new TelemetryFragment());
        addTr.replace(R.id.topBarContainer, new TopBarFragment());
        addTr.replace(R.id.mapOverlayContainer, new MapsOverlayFragment());
        addTr.commitNow();
    }

    private void setupRepositoryObservers() {
        repository.getIsNightMode().observe(this, isNight -> {
            int targetMode = isNight ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });
    }

    // --- ACTIONS ---

    private void toggleMenu() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (currentMenuShowing == 1) {
            transaction.setCustomAnimations(R.anim.stay, R.anim.slide_out_bottom);
            Fragment menu = getSupportFragmentManager().findFragmentById(R.id.menuContainer);
            if (menu != null) transaction.remove(menu);
            currentMenuShowing = 0;
        } else {
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

    // --- SYSTEM ---

    private void startHardwareService() {
        Intent intent = new Intent(this, MainForegroundService.class);
        ContextCompat.startForegroundService(this, intent);
    }

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

    // Required for legacy XML onClick binding if still present
    public void updateTemp(View view) {}
}
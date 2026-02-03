package com.openautodash.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import com.openautodash.R;
import com.openautodash.object.Weather; // Ensure this import is correct
import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.utilities.ModemInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TopBarFragment extends Fragment {
    private static final String TAG = "TopBarFragment";

    private VehicleRepository repository;
    private TextView clockView, tempView, lteNetworkType, brightnessDebugView;
    private ImageView windDirectionView, bluetoothStatusIcon, lteStatusView, liveTrackingIcon;

    private BroadcastReceiver clockReceiver;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("h:mm a", Locale.US);

    private final Handler modemHandler = new Handler();
    private ModemInfo modemInfo;
    private final Runnable modemRunnable = new Runnable() {
        @Override
        public void run() {
            // Check if we are connected to the car's WiFi modem
            WifiManager wm = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();

            if (info.getSSID().contains("My Fusion")) {
                modemInfo.updateInfo();
            } else {
                // Standard WiFi logic
                boolean connected = isInternetConnected(requireContext());
                repository.updateNetworkStatus(connected ? 1 : 0, "", true);
            }
            modemHandler.postDelayed(this, 5000);
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        modemInfo = new ModemInfo(requireContext());
        modemHandler.post(modemRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        modemHandler.removeCallbacks(modemRunnable);
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_top_bar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = VehicleRepository.getInstance(requireContext());

        initializeViews(view);
        setupRepositoryObservers();
        startClock();
    }

    private void initializeViews(View view) {
        clockView = view.findViewById(R.id.tv_m_clock);
        tempView = view.findViewById(R.id.tv_main_temp);
        windDirectionView = view.findViewById(R.id.iv_m_wind_dir);
        bluetoothStatusIcon = view.findViewById(R.id.iv_m_bluetooth_status);
        lteStatusView = view.findViewById(R.id.iv_main_lte_signal);
        lteNetworkType = view.findViewById(R.id.tv_main_signal_network_type);
        brightnessDebugView = view.findViewById(R.id.brightesscrap);
        liveTrackingIcon = view.findViewById(R.id.iv_top_bar_live_tracking);

        lteStatusView.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void setupRepositoryObservers() {
        // 1. Weather Observer
        repository.getWeather().observe(getViewLifecycleOwner(), weather -> {
            if (weather != null) {
                Log.d(TAG, "Weather Update Received: " + weather.getTemp());
                tempView.setText(String.format(Locale.US, "%d°C", weather.getTemp())); // %.0f removes decimals
                updateWindDirection(); // Recalculate arrow
            } else {
                Log.w(TAG, "Weather data is NULL");
            }
        });

        // 2. Location Observer (CRITICAL FOR WIND ARROW)
        repository.getLocation().observe(getViewLifecycleOwner(), location -> {
            // We don't log here to avoid spamming Logcat every second
            if (location != null) {
                updateWindDirection(); // Spin the arrow when car turns
            }
        });

        // 3. Brightness
        repository.getScreenBrightness().observe(getViewLifecycleOwner(), brightness -> {
            if (getActivity() != null) {
                WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
                lp.screenBrightness = brightness;
                getActivity().getWindow().setAttributes(lp);}
        });

        repository.getSensorLux().observe(getViewLifecycleOwner(), lightSensor -> {
            brightnessDebugView.setText(String.valueOf(lightSensor + "br"));
        });

        // 4. Bluetooth
        repository.getBluetoothState().observe(getViewLifecycleOwner(), isConnected -> {
            if (getActivity() == null) return;
            if (isConnected != null && isConnected) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_bluetooth_nearby));
                bluetoothStatusIcon.clearColorFilter();
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_bluetooth));
            }
        });

        // 5. Network
        repository.getNetworkStatus().observe(getViewLifecycleOwner(), status -> {
            int iconRes = R.drawable.signal_lte_0;
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
            lteStatusView.setImageDrawable(AppCompatResources.getDrawable(requireContext(), iconRes));
            lteNetworkType.setText(status.isWifi ? "" : "LTE");
        });

        repository.getIsLiveTrackingEnabled().observe(getViewLifecycleOwner(), isEnabled -> {
            liveTrackingIcon.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
        });
    }

    private void updateWindDirection() {
        // Need both pieces of data to calculate the arrow angle
        Weather weather = repository.getWeather().getValue();
        Location location = repository.getLocation().getValue();

        if (weather != null && location != null) {
            float windBearing = (float) weather.getWindDeg();
            float carBearing = location.getBearing();

            // Formula: Wind Direction - Car Heading = Arrow Rotation
            float relativeAngle = windBearing - carBearing;

            // Normalize to 0-360 for clean animation
            if (relativeAngle < 0) relativeAngle += 360;
            if (relativeAngle > 360) relativeAngle -= 360;

            windDirectionView.setRotation(relativeAngle);
        }
    }

    private void startClock() {
        clockView.setText(clockFormat.format(new Date()));
        clockReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                clockView.setText(clockFormat.format(new Date()));
            }
        };
        requireContext().registerReceiver(clockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (clockReceiver != null) requireContext().unregisterReceiver(clockReceiver);
    }


    public static boolean isInternetConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetwork() != null && cm.getNetworkCapabilities(cm.getActiveNetwork()) != null;
    }


}
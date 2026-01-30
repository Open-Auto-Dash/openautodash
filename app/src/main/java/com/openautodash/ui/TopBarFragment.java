package com.openautodash.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
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
import com.openautodash.repositorys.VehicleRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TopBarFragment extends Fragment {

    private VehicleRepository repository;
    private TextView clockView, tempView, lteNetworkType, brightnessDebugView;
    private ImageView windDirectionView, bluetoothStatusIcon, lteStatusView;

    private BroadcastReceiver clockReceiver;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("h:mm a", Locale.US);

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflater has the correct Theme context
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

        lteStatusView.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void setupRepositoryObservers() {
        // Brightness
        repository.getScreenBrightness().observe(getViewLifecycleOwner(), brightness -> {
            if (getActivity() != null) {
                WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
                lp.screenBrightness = brightness;
                getActivity().getWindow().setAttributes(lp);
                brightnessDebugView.setText((int)(brightness * 255) + "br");
            }
        });

        // Bluetooth
        repository.getBluetoothState().observe(getViewLifecycleOwner(), isConnected -> {
            if (getActivity() == null) return;
            if (isConnected) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_bluetooth_nearby));
                bluetoothStatusIcon.clearColorFilter(); // Show Blue
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                bluetoothStatusIcon.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_bluetooth));

            }
        });

        // Weather & Wind
        repository.getWeather().observe(getViewLifecycleOwner(), weather -> {
            if (weather != null) {
                tempView.setText(String.format(Locale.US, "%d°C", weather.getTemp()));
                Location loc = repository.getLocation().getValue();
                float carBearing = (loc != null) ? loc.getBearing() : 0f;
                float relativeAngle = (float) weather.getWindDeg() - carBearing;
                windDirectionView.setRotation(relativeAngle);
            }
        });

        repository.getLocation().observe(getViewLifecycleOwner(), loc -> {}); // Just to trigger wind update

        // Network
        repository.getNetworkStatus().observe(getViewLifecycleOwner(), status -> {
            updateNetworkUI(status);
        });
    }

    private void updateNetworkUI(VehicleRepository.NetworkStatus status) {
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
        lteNetworkType.setText(status.isWifi ? "" : "LTE"); // Simplified
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
}
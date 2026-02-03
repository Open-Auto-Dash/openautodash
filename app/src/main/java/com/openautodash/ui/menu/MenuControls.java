package com.openautodash.ui.menu;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.openautodash.R;
import com.openautodash.repositorys.VehicleRepository;

public class MenuControls extends Fragment {

    private Button restartAppButton;
    private Button liveTrackingButton;
    private VehicleRepository vehicleRepository;

    public MenuControls() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vehicleRepository = VehicleRepository.getInstance(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_controls, container, false);

        restartAppButton = view.findViewById(R.id.b_restart_app_menu_controls);
        liveTrackingButton = view.findViewById(R.id.b_live_tracking);

        restartAppButton.setOnClickListener(v -> {
            requireActivity().finish();
            System.exit(0);
        });

        liveTrackingButton.setOnClickListener(v -> {
            boolean isTracking = !Boolean.TRUE.equals(vehicleRepository.getIsLiveTrackingEnabled().getValue());
            vehicleRepository.setLiveTrackingEnabled(isTracking);
        });

        vehicleRepository.getIsLiveTrackingEnabled().observe(getViewLifecycleOwner(), isEnabled -> {
            liveTrackingButton.setText(isEnabled ? "Disable Tracking" : "Enable Tracking");
            liveTrackingButton.setSelected(isEnabled);
        });

        return view;
    }
}
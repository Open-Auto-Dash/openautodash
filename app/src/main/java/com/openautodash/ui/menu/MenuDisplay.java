package com.openautodash.ui.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.slider.Slider;
import com.openautodash.R;
import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.utilities.LocalSettings;

public class MenuDisplay extends Fragment {
    private static final String TAG = "MenuDisplay";

    private LocalSettings localSettings;
    private VehicleRepository repository;

    private Slider nightModeThresholdSlider;
    private Slider displayPointSlider;
    private Slider displayBrightnessSlider;

    // State for the brightness curve editor
    private int currentCurveIndex = 0;
    private int[] brightnessCurve;

    public MenuDisplay() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localSettings = new LocalSettings(requireContext());
        repository = VehicleRepository.getInstance(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_display, container, false);

        nightModeThresholdSlider = view.findViewById(R.id.slider_menu_controls_night_mode);
        displayPointSlider = view.findViewById(R.id.slider_menu_controls_display_point);
        displayBrightnessSlider = view.findViewById(R.id.slider_menu_controls_display_brightness);

        // 1. Load Initial Values
        brightnessCurve = localSettings.getBrightnessSetting();

        // Safety check to ensure we have a valid array
        if (brightnessCurve == null || brightnessCurve.length < 6) {
            brightnessCurve = new int[]{10, 40, 80, 120, 200, 255};
        }

        nightModeThresholdSlider.setValue(localSettings.getNightModeSetPoint());

        // Initialize brightness slider based on the first point (Index 0)
        currentCurveIndex = 0;
        displayPointSlider.setValue(0);
        displayBrightnessSlider.setValue(brightnessCurve[0]);

        // 2. Setup Listeners

        // Night Mode Threshold Listener
        nightModeThresholdSlider.addOnChangeListener((slider, value, fromUser) -> {
            int threshold = (int) value;
            localSettings.setNightModeSetPoint(threshold);
            // Notify Repository if you add a method to update this live,
            // otherwise it picks it up on next app restart or you can expose a setter in Repo.
        });

        // Display Point Selector (0 to 5)
        displayPointSlider.addOnChangeListener((slider, value, fromUser) -> {
            currentCurveIndex = (int) value;

            // Snap to integer
            slider.setValue(currentCurveIndex);

            // Update the brightness slider to match the value at this index
            if (currentCurveIndex < brightnessCurve.length) {
                displayBrightnessSlider.setValue(brightnessCurve[currentCurveIndex]);
            }
        });

        // Brightness Value Adjuster
        displayBrightnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            int brightnessValue = (int) value;

            // Update the local array
            if (currentCurveIndex < brightnessCurve.length) {
                brightnessCurve[currentCurveIndex] = brightnessValue;

                // Save to Persistence
                localSettings.setBrightnessSetting(brightnessCurve);

                // Update Live Repository (This applies changes immediately to the running Service)
                // Note: You might need to add `updateBrightnessCurve(int[])` to VehicleRepository
                // if you haven't yet, or simply rely on the Service reading LocalSettings.
                // ideally: repository.reloadSettings();
            }
        });

        return view;
    }
}
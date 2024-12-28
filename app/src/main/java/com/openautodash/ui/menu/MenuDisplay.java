package com.openautodash.ui.menu;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.slider.Slider;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.utilities.LocalSettings;

public class MenuDisplay extends Fragment {
    private static final String TAG = "MenuControls";

    private LocalSettings localSettings;

    private Slider nightModeThreshHold;
    private Slider displayPoint;
    private Slider displayBrightness;

    private int displayPointNow;
    private int[] displayBrightnessArray;
    public MenuDisplay() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localSettings = new LocalSettings(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_menu_display, container, false);
        nightModeThreshHold = view.findViewById(R.id.slider_menu_controls_night_mode);
        displayPoint = view.findViewById(R.id.slider_menu_controls_display_point);
        displayBrightness = view.findViewById(R.id.slider_menu_controls_display_brightness);

        nightModeThreshHold.setValue(localSettings.getNightModeSetPoint());

        displayBrightnessArray = localSettings.getBrightnessSetting();
        displayBrightness.setValue(displayBrightnessArray[0]);
        nightModeThreshHold.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                localSettings.setNightModeSetPoint((int)value);
            }
        });

        displayPoint.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                displayPointNow = (int)value;
                displayBrightness.setValue(displayBrightnessArray[(int)value]);
            }
        });

        displayBrightness.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                displayBrightnessArray[displayPointNow] = (int)value;
                localSettings.setBrightnessSetting(displayBrightnessArray);
                ((MainActivity) requireActivity()).setBrightnessSettings(displayBrightnessArray);
            }
        });

        // Inflate the layout for this fragment
        return view;
    }
}
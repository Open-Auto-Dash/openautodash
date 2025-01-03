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

public class MenuControls extends Fragment {


    public MenuControls() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_menu_controls, container, false);
        return view;
    }
}
package com.openautodash.ui.menu;

import static androidx.browser.customtabs.CustomTabsClient.getPackageName;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.utilities.LocalSettings;

import java.util.Objects;

public class MenuControls extends Fragment {

    private Button restartAppButton;

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

        restartAppButton = view.findViewById(R.id.b_restart_app_menu_controls);

        restartAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requireActivity().finish();
                System.exit(0);
            }
        });

        return view;
    }
}
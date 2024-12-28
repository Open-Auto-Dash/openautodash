package com.openautodash.ui.menu;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.openautodash.BuildConfig;
import com.openautodash.R;
public class MenuSoftware extends Fragment {

    TextView tvSoftwareVersion;

    public MenuSoftware() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_menu_software, container, false);

        tvSoftwareVersion = view.findViewById(R.id.tv_menu_software_version);
        tvSoftwareVersion.setText(BuildConfig.VERSION_NAME);
        // Inflate the layout for this fragment
        return view;
    }
}
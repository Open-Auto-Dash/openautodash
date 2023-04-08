package com.openautodash.ui;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.openautodash.LiveDataViewModel;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.interfaces.OverpassAPICallback;
import com.openautodash.utilities.OverpassAPI;

public class TelemetryFragment extends Fragment implements OverpassAPICallback {
    private static final String TAG = "TelemetryFragment";

    //ViewModel
    private LiveDataViewModel liveDataViewModel;

    //Views
    private TextView speed;
    private TextView alt;

    private TextView nice;

    private TextView maxSpeedView;
    private TextView speedLimitView;

    private ImageView albumArt;
    private TextView songTitle;
    private TextView songArtist;
    private ProgressBar songProgress;
    private ImageView playPause;



    //Variables

    private OverpassAPICallback callback;

    private int locationUpdatesCount;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        liveDataViewModel = ((MainActivity) requireActivity()).getViewModel();
        callback = this;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_telemetry, container, false);
        speed = view.findViewById(R.id.tv_m_speed);
        speedLimitView = view.findViewById(R.id.tv_tele_speed_limit);
        maxSpeedView = view.findViewById(R.id.tv_max_speed);
        alt = view.findViewById(R.id.tv_m_gear);
        nice = view.findViewById(R.id.tv_m_nice);

        albumArt = view.findViewById(R.id.iv_home_album_art);
        songTitle = view.findViewById(R.id.tv_home_song_title);
        songArtist = view.findViewById(R.id.tv_home_song_artist);

        songProgress = view.findViewById(R.id.pb_home_song_progress);
        playPause = view.findViewById(R.id.iv_home_play);

        //Get location updates
        liveDataViewModel.getLocationData().observe(getViewLifecycleOwner(), new Observer<Location>() {
            @Override
            public void onChanged(Location location) {
                locationUpdatesCount++;
                speed.setText(String.valueOf((int)(location.getSpeed() * 3.6)));
                alt.setText(String.valueOf((int)location.getAltitude()));

                if(locationUpdatesCount > 5 && location.getSpeed() > 5){
                    OverpassAPI overpassAPI = new OverpassAPI(getContext(), location, callback);
                    overpassAPI.getSpeedLimit();
                    locationUpdatesCount = 0;
                }
            }
        });
        return view;
    }

    @Override
    public void onComplete(String result) {

    }

    @Override
    public void speedLimitUpdated(String speedLimit) {
        speedLimitView.setText(speedLimit);
        // Do something with the max speed data
        int maxSpeedInt = Integer.parseInt(speedLimit);
        if(maxSpeedInt < 60){
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 10));
        }
        else if(maxSpeedInt >  90){
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 25));
        }
        else {
            maxSpeedView.setText(String.valueOf(maxSpeedInt + 20));
        }
    }


}

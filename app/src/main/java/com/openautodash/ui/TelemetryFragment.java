package com.openautodash.ui;

import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TelemetryFragment extends Fragment {
    private static final String TAG = "TelemetryFragment";

    //ViewModel
    private LiveDataViewModel liveDataViewModel;

    //Views
    private TextView speed;
    private TextView alt;

    private TextView nice;

    private View speedLimitView;
    private TextView maxSpeedView;
    private TextView speedLimit;

    private ImageView albumArt;
    private TextView songTitle;
    private TextView songArtist;
    private ProgressBar songProgress;
    private ImageView playPause;



    //Variables

    private int locationUpdatesCount;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        liveDataViewModel = ((MainActivity) getActivity()).getViewModel();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_telemetry, container, false);
        speed = view.findViewById(R.id.tv_m_speed);
        speedLimit = view.findViewById(R.id.tv_tele_speed_limit);
        maxSpeedView = view.findViewById(R.id.tv_max_speed);
        alt = view.findViewById(R.id.tv_m_gear);
        nice = view.findViewById(R.id.tv_m_nice);

        albumArt = view.findViewById(R.id.iv_home_album_art);
        songTitle = view.findViewById(R.id.tv_home_song_title);
        songArtist = view.findViewById(R.id.tv_home_song_artist);

        songProgress = view.findViewById(R.id.pb_home_song_progress);
        playPause = view.findViewById(R.id.iv_home_play);

        speedLimitView = view.findViewById(R.id.v_f_home_speedlimit_view);

        //Get location updates
        liveDataViewModel.getLocationData().observe(getViewLifecycleOwner(), new Observer<Location>() {
            @Override
            public void onChanged(Location location) {
                locationUpdatesCount++;
                speed.setText(String.valueOf((int)(location.getSpeed() * 3.6)));
                alt.setText(String.valueOf(location.getAltitude()));

                if(locationUpdatesCount > 5 && location.getSpeed() > 5){
                    QueryOverpassTask task = new QueryOverpassTask(location.getLatitude(), location.getLongitude());
                    task.execute();
                    locationUpdatesCount = 0;
                }
            }
        });

        return view;
    }

    private class QueryOverpassTask extends AsyncTask<Void, Void, String> {
        private double latitude;
        private double longitude;

        public QueryOverpassTask(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                OkHttpClient client = new OkHttpClient();

                String queryUrl = "https://overpass-api.de/api/interpreter?data=[out:json][timeout:25];way(around:20," +
                        latitude + "," + longitude + ")[maxspeed];out;";

                Request request = new Request.Builder()
                        .url(queryUrl)
                        .build();

                Response response = client.newCall(request).execute();
                return response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            if (response != null) {
                try {
                    JSONObject responseJson = new JSONObject(response);
                    JSONArray elements = responseJson.getJSONArray("elements");

                    for (int i = 0; i < elements.length(); i++) {
                        JSONObject element = elements.getJSONObject(i);

                        if (element.has("tags")) {
                            JSONObject tags = element.getJSONObject("tags");
                            if (tags.has("maxspeed")) {
                                String maxSpeed = tags.getString("maxspeed");
                                Log.d(TAG, "onPostExecute: Max speed: " + maxSpeed);
                                speedLimit.setText(maxSpeed);
                                // Do something with the max speed data
                                int maxSpeedInt = Integer.parseInt(maxSpeed);
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
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                // Handle any network or API errors
            }
        }
    }
}

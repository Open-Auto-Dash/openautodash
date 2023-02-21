package com.openautodash.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.openautodash.LiveDataViewModel;
import com.openautodash.MainActivity;
import com.openautodash.R;

public class MapFragment extends Fragment {
    private static final String TAG = "MapFragment";


    //ViewModel
    private LiveDataViewModel liveDataViewModel;

    //Map stuff
    private GoogleMap map;
    private Marker marker;

    private Location location;

    private ImageView resumeAnimate;



    private boolean isAnimating;
    private boolean mapMoving;
    private int lastAnimation;
    public MapFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        liveDataViewModel = ((MainActivity) getActivity()).getViewModel();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        // Initialize view
        View view=inflater.inflate(R.layout.fragment_map, container, false);

        // Initialize map fragment
        SupportMapFragment supportMapFragment=(SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.google_map);

        resumeAnimate = view.findViewById(R.id.iv_b_start_animate);

        resumeAnimate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isAnimating = true;
                resumeAnimate.setVisibility(View.INVISIBLE);
            }
        });

        // Async map
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                // When map is loaded

                map = googleMap;

                int nightModeFlags = getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                switch (nightModeFlags) {
                    case Configuration.UI_MODE_NIGHT_YES:
                        Log.d(TAG, "onMapReady: Night Mode ON");
                        try {
                            // Customise the styling of the base map using a JSON object defined
                            // in a raw resource file.
                            boolean success = googleMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(
                                            getContext(), R.raw.style_json));

                            if (!success) {
                                Log.e(TAG, "Style parsing failed.");
                            }
                        } catch (Resources.NotFoundException e) {
                            Log.e(TAG, "Can't find style. Error: ", e);
                        }        // Instantiates a new CircleOptions object and defines the center and radius
                        break;

                    case Configuration.UI_MODE_NIGHT_NO:
                        Log.d(TAG, "onMapReady: Night Mode OFf");
                        break;

                    case Configuration.UI_MODE_NIGHT_UNDEFINED:
                        Log.d(TAG, "onMapReady: I have no idea if night mode is on or not. 🤷‍️");
                        break;
                }

                googleMap.getUiSettings().setCompassEnabled(false);

                googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
                    @Override
                    public void onCameraMoveStarted(int i) {
                        if(i == REASON_GESTURE){
                            Log.d(TAG, "onCameraMoveStarted: Gesture");
                            isAnimating = false;
                            resumeAnimate.setVisibility(View.VISIBLE);
                        }
                    }
                });

                //Move map to Home
                LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
                @SuppressLint("MissingPermission") Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                //43.561826, -80.668757
                LatLng location = new LatLng(43.596067, -80.717016);
                if(lastKnownLocation != null){
                    location = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                }
                CameraPosition newCamPos = new CameraPosition(location,
                        18,
                        70,
                        0);
                map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 1000, null);

                marker = map.addMarker(new MarkerOptions()
                        .position(location)
                        .title("Current Location")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                        .flat(true));


                Log.d(TAG, "onMapReady");
                liveDataViewModel.getLocationData().observe(getViewLifecycleOwner(), new Observer<Location>() {
                    @Override
                    public void onChanged(Location location) {
                        if(map != null){
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            if (marker == null) {
                                Log.d(TAG, "onReceive: Marker is null");
                                marker = map.addMarker(new MarkerOptions()
                                        .position(latLng)
                                        .title("Current Location")
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker))
                                        .flat(true));
                            } else {
                                Log.d(TAG, "onReceive: Set marker position");
                            }
                            assert marker != null;
                            marker.setPosition(latLng);


                            int speedInt = (int) (location.getSpeed() * 3.6);
                            int zoom = 15;
                            int tilt = 55;
                            if (speedInt < 102) {
                                zoom = 16;
                                tilt = 60;
                            }
                            if (speedInt < 79) {
                                zoom = 17;
                                tilt = 70;
                            }
                            if (speedInt < 59) {
                                zoom = 18;
                            }
                            if (speedInt < 30) {
                                zoom = 19;
                            }
                            marker.setRotation(location.getBearing());
//                    speed.setText(Speed);
                            CameraPosition newCamPos = new CameraPosition(latLng,
                                    zoom,
                                    tilt,
                                    location.getBearing());

                            if (isAnimating) {
                                if (speedInt > 4 && !mapMoving) {
                                    mapMoving = true;
                                    map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 900, new GoogleMap.CancelableCallback() {
                                        @Override
                                        public void onFinish() {
                                            mapMoving = false;
                                            // Code to execute when the animateCamera task has finished
                                        }

                                        @Override
                                        public void onCancel() {
                                            mapMoving = false;
                                            // Code to execute when the user has canceled the animateCamera task
                                        }
                                    });
                                    lastAnimation = 0;

                                }
                            } else {
                                lastAnimation++;
                                if (lastAnimation > 45) {
                                    isAnimating = true;
                                }
                            }
                            Log.d(TAG, "onReceive: Lat/Lng: " + location.getLatitude() + " | " + location.getLongitude() + " Bearing: " + location.getBearing() + " Speed: " + location.getSpeed() * 3.6);
                        }
                    }
                });
            }
        });
        // Return view
        return view;

    }

    @Override
    public void onResume() {
        super.onResume();
        isAnimating = true;
    }


}

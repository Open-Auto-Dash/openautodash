package com.openautodash.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.openautodash.LiveDataViewModel;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.utilities.LocalSettings;

public class MapFragment extends Fragment implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnPoiClickListener {
    private static final String TAG = "MapFragment";


    //ViewModel
    private LiveDataViewModel liveDataViewModel;

    //Map stuff
    private GoogleMap map;
    private Marker marker;

    private ImageView resumeAnimate;
    private ImageView mapTypeView;
    private ImageView mapTrafficView;

    private boolean isAnimating;
    private boolean mapMoving;
    private int lastAnimation;

    private LocalSettings settings;

    public MapFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        liveDataViewModel = ((MainActivity) requireActivity()).getViewModel();
        settings = new LocalSettings(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        // Initialize view
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // Initialize map fragment
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.google_map);
        supportMapFragment.getMapAsync(this);


        resumeAnimate = view.findViewById(R.id.iv_b_start_animate);
        mapTypeView = view.findViewById(R.id.iv_map_type);
        mapTrafficView = view.findViewById(R.id.iv_map_traffic);

        resumeAnimate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isAnimating = true;
                resumeAnimate.setVisibility(View.INVISIBLE);
                //Move map to Home
                LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
                @SuppressLint("MissingPermission") Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                updateMap(lastKnownLocation, true);
            }
        });

        mapTypeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (map.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                    map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    mapTypeView.setBackground(getContext().getResources().getDrawable(R.drawable.background_image_view_sellected));
                } else {
                    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    mapTypeView.setBackground(null);
                }
            }
        });

        mapTrafficView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!map.isTrafficEnabled()) {
                    map.setTrafficEnabled(true);
                    mapTrafficView.setBackground(getContext().getResources().getDrawable(R.drawable.background_image_view_sellected));
                } else {
                    map.setTrafficEnabled(false);
                    mapTrafficView.setBackground(null);
                }
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

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        // When map is loaded
        map = googleMap;

        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnMapClickListener(this);
        map.setOnPoiClickListener(this);
        map.setPadding(0, 400, 0, 0);

        int nightModeFlags = getContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                Log.d(TAG, "onMapReady: Night Mode ON");
                try {
                    // Customise the styling of the base map using a JSON object defined
                    // in a raw resource file.
                    boolean success = googleMap.setMapStyle(
                            MapStyleOptions.loadRawResourceStyle(
                                    getContext(), R.raw.map_night_style));

                    if (!success) {
                        Log.e(TAG, "Style parsing failed.");
                    }
                } catch (Resources.NotFoundException e) {
                    Log.e(TAG, "Can't find style. Error: ", e);
                }        // Instantiates a new CircleOptions object and defines the center and radius
                break;

            case Configuration.UI_MODE_NIGHT_NO:
                Log.d(TAG, "onMapReady: Night Mode OFf");
                boolean success = googleMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(
                                getContext(), R.raw.map_day_white_style));

                if (!success) {
                    Log.e(TAG, "Style parsing failed.");
                }
                break;

            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                Log.d(TAG, "onMapReady: I have no idea if night mode is on or not. 🤷‍️");
                break;
        }


        googleMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int i) {
                if (i == REASON_GESTURE) {
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
        if (lastKnownLocation != null) {
            location = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        }
        CameraPosition newCamPos = new CameraPosition(location,
                18,
                70,
                0);
        map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 1000, null);


        Bitmap resizeBitmap = bitmapSizeByScale(BitmapFactory.decodeResource(requireContext().getResources(), R.drawable.marker_red), 0.6f);

        marker = map.addMarker(new MarkerOptions()
                .position(location)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.fromBitmap(resizeBitmap))
                .flat(true));


        Log.d(TAG, "onMapReady");
        liveDataViewModel.getLocationData().observe(getViewLifecycleOwner(), new Observer<Location>() {
            @Override
            public void onChanged(Location location) {
                if (map != null) {
                    updateMap(location, false);
                    Log.d(TAG, "onReceive: Lat/Lng: " + location.getLatitude() + " | " + location.getLongitude() + " Bearing: " + location.getBearing() + " Speed: " + location.getSpeed() * 3.6);
                }
            }
        });
    }

    private void updateMap(Location location, boolean driftOff) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        assert marker != null;
        setMarker(marker, latLng);
        marker.setRotation(location.getBearing());

        if (isAnimating) {
            if (location.getSpeed() > 4 && !mapMoving || driftOff) {
                mapMoving = true;
                CameraPosition newCamPos = new CameraPosition(latLng,
                        getMapZoomTilt(location)[0],
                        getMapZoomTilt(location)[1],
                        location.getBearing());
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
    }

    // Animates a marker to a new location
    public void setMarker(final Marker marker, final LatLng toPosition) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = map.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);

        final long duration = 900;

        final Interpolator interpolator = new LinearInterpolator();
        if (startLatLng != null) {
            Log.e(TAG, "setMarker: Error startLatLng is null");

            handler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = SystemClock.uptimeMillis() - start;
                    float t = interpolator.getInterpolation((float) elapsed / duration);
                    double lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude;
                    double lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude;
                    LatLng latLng = new LatLng(lat, lng);
                    marker.setPosition(latLng);

                    if (t < 1.0) {
                        // Post again 32ms later (30 frames per second)
                        handler.postDelayed(this, 32);
                    }
                }
            });
        }
        else{
            marker.setPosition(toPosition);
        }
    }

    private int[] getMapZoomTilt(Location location) {
        int speedInt = (int) (location.getSpeed() * settings.getSpeedUnits());
        int[] zoomTilt = {14, 55};

        if (speedInt < 102) {
            zoomTilt[0] = 15;
            zoomTilt[1] = 60;
        }
        if (speedInt < 79) {
            zoomTilt[0] = 17;
            zoomTilt[1] = 70;
        }
        if (speedInt < 59) {
            zoomTilt[0] = 18;
        }
        if (speedInt < 30) {
            zoomTilt[0] = 19;
        }
        return zoomTilt;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // Define a radius in meters for the search
        int radius = 20;

    }


    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Log.d(TAG, "onMarkerClick: " + marker.getTitle());
        return false;
    }

    @Override
    public void onPoiClick(@NonNull PointOfInterest pointOfInterest) {
        Log.d(TAG, "onPoiClick: POI: " + pointOfInterest.name);
    }

    public Bitmap bitmapSizeByScale(Bitmap bitmapIn, float scall_zero_to_one_f) {

        Bitmap bitmapOut = Bitmap.createScaledBitmap(bitmapIn,
                Math.round(bitmapIn.getWidth() * scall_zero_to_one_f),
                Math.round(bitmapIn.getHeight() * scall_zero_to_one_f), false);

        return bitmapOut;
    }
}

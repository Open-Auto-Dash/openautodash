package com.openautodash.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PointOfInterest;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.model.Place;
import com.google.maps.android.SphericalUtil;
import com.google.maps.model.DirectionsResult;
import com.openautodash.LiveDataViewModel;
import com.openautodash.MainActivity;
import com.openautodash.R;
import com.openautodash.adapters.SearchSuggestionsAdapter;
import com.openautodash.object.PlaceSearchResult;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.LocationSearchManager;
import com.openautodash.utilities.RouteManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private RouteManager routeManager;
    private Location currentLocation;

    private ImageView resumeAnimate;
    private ImageView mapTypeView;
    private ImageView mapTrafficView;
    private ImageView flightDirectorView;

    private boolean isAnimating;
    private boolean mapMoving;
    private int lastAnimation;
    private boolean isFlightDirectorEnabled = false;
    private Polyline completeStopArc;
    private Polyline slowSpeedArc;
    private Double lastSpeed = null;
    private Long lastSpeedTimestamp = null;
    private Double currentDeceleration = null; // in m/s²
    private static final double MIN_DECELERATION = 0.1; // m/s², minimum deceleration to show arc

    private EditText searchEditText;
    private ImageView clearSearchButton;

    private RecyclerView suggestionsRecyclerView;
    private CardView suggestionsCardView;
    private SearchSuggestionsAdapter suggestionsAdapter;
    private LocationSearchManager locationSearchManager;

    private LocalSettings settings;

    public MapFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        liveDataViewModel = ((MainActivity) requireActivity()).getViewModel();
        settings = new LocalSettings(requireContext());

        locationSearchManager = new LocationSearchManager(requireContext());
        suggestionsAdapter = new SearchSuggestionsAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (routeManager != null) {
            routeManager.cleanup();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // Initialize map fragment
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.google_map);
        supportMapFragment.getMapAsync(this);

        resumeAnimate = view.findViewById(R.id.iv_b_start_animate);
        mapTypeView = view.findViewById(R.id.iv_map_type);
        mapTrafficView = view.findViewById(R.id.iv_map_traffic);
        flightDirectorView = view.findViewById(R.id.iv_flight_director);

        searchEditText = view.findViewById(R.id.et_search);
        suggestionsRecyclerView = view.findViewById(R.id.rv_suggestions);
        suggestionsCardView = view.findViewById(R.id.cv_suggestions);
        clearSearchButton = view.findViewById(R.id.iv_clear_search);

        setupSearchViews();

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

        flightDirectorView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFlightDirectorEnabled = !isFlightDirectorEnabled;
                if (isFlightDirectorEnabled) {
                    flightDirectorView.setBackground(getContext().getResources()
                            .getDrawable(R.drawable.background_image_view_sellected));
                } else {
                    flightDirectorView.setBackground(null);
                    // Clear existing arcs if any
                    if (completeStopArc != null) completeStopArc.remove();
                    if (slowSpeedArc != null) slowSpeedArc.remove();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isAnimating = true;
    }


    private void setupSearchViews() {
        // Setup RecyclerView
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestionsRecyclerView.setAdapter(suggestionsAdapter);

        clearSearchButton.setOnClickListener(v -> clearSearchAndRoute());

        // Handle suggestion clicks
        suggestionsAdapter.setOnSuggestionClickListener(suggestion -> {
            locationSearchManager.getPlaceDetails(suggestion.placeId(), new LocationSearchManager.LocationSearchCallback() {
                @Override
                public void onSearchResults(List<PlaceSearchResult> results) {
                    // Not needed for place details
                }

                @Override
                public void onPlaceSelected(Place place) {
                    if (place.getLocation() != null && currentLocation != null) {
                        // Draw route to selected place
                        LatLng origin = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                        routeManager.requestRoute(origin, place.getLocation(), new RouteManager.RouteCallback() {
                            @Override
                            public void onRouteFound(DirectionsResult result) {
                                // Zoom map to show the entire route
                                LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                                bounds.include(origin);
                                bounds.include(place.getLocation());
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));

                                // Clear search and hide suggestions
                                searchEditText.setText(place.getDisplayName());
                                searchEditText.clearFocus();
                                suggestionsCardView.setVisibility(View.GONE);
                            }

                            @Override
                            public void onRouteError(String error) {
                                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });


        // Setup search input handling
        searchEditText.addTextChangedListener(new TextWatcher() {
            private Handler handler = new Handler();
            private Runnable runnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(runnable);
                runnable = () -> {
                    String query = s.toString().trim();
                    if (query.length() >= 2) {
                        performSearch(query);
                        suggestionsCardView.setVisibility(View.VISIBLE);
                    } else {
                        suggestionsCardView.setVisibility(View.GONE);
                    }
                };
                handler.postDelayed(runnable, 300);
            }
        });

        // Handle focus changes
        searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchEditText.getText().length() >= 2) {
                suggestionsCardView.setVisibility(View.VISIBLE);
            } else if (!hasFocus) {
                // Add a slight delay to allow for item clicks
                new Handler().postDelayed(() ->
                        suggestionsCardView.setVisibility(View.GONE), 200);
            }
        });

        // Handle keyboard search action
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchEditText.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void performSearch(String query) {
        locationSearchManager.searchPlaces(query, new LocationSearchManager.LocationSearchCallback() {
            @Override
            public void onSearchResults(List<PlaceSearchResult> results) {
                suggestionsAdapter.updateSuggestions(results);
            }

            @Override
            public void onPlaceSelected(Place place) {
                // Not needed for search
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Add this method to handle back press in the fragment
    public boolean onBackPressed() {
        if (suggestionsCardView.getVisibility() == View.VISIBLE) {
            suggestionsCardView.setVisibility(View.GONE);
            searchEditText.clearFocus();
            return true;
        }
        return false;
    }

    private void clearSearchAndRoute() {
        // Clear search text
        searchEditText.setText("");
        searchEditText.clearFocus();

        // Hide suggestions
        suggestionsCardView.setVisibility(View.GONE);

        // Clear route if exists
        if (routeManager != null) {
            routeManager.clearRoutes();
        }

        // Hide clear button
        clearSearchButton.setVisibility(View.GONE);

        // Return to following current location if not already
        if (currentLocation != null) {
            isAnimating = true;
            updateMap(currentLocation, true);
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        // When map is loaded
        map = googleMap;

        routeManager = new RouteManager(requireContext().getString(R.string.google_maps_key), map);

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
                }
                break;

            case Configuration.UI_MODE_NIGHT_NO:
                Log.d(TAG, "onMapReady: Night Mode OFF");
                boolean success = googleMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(
                                getContext(), R.raw.map_day_style));

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
                currentLocation = location;

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
                    }

                    @Override
                    public void onCancel() {
                        mapMoving = false;
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

        // Update flight director arcs
        updateDecelerationArcs(location);
    }

    private void updateDecelerationArcs(Location location) {
        if (!isFlightDirectorEnabled) {
            clearArcs();
            return;
        }

        double currentSpeed = location.getSpeed(); // in m/s
        long currentTime = SystemClock.elapsedRealtimeNanos();

        // Add debug logging
        Log.d(TAG, "Current Speed: " + (currentSpeed * 3.6) + " km/h"); // Convert to km/h for readable logs

        // Calculate deceleration rate
        if (lastSpeed != null && lastSpeedTimestamp != null) {
            double deltaV = lastSpeed - currentSpeed;
            double deltaT = (currentTime - lastSpeedTimestamp) / 1e9; // Convert nanos to seconds
            currentDeceleration = deltaV / deltaT; // m/s²

            // Add debug logging
            Log.d(TAG, "Delta V: " + deltaV + " m/s");
            Log.d(TAG, "Delta T: " + deltaT + " s");
            Log.d(TAG, "Calculated Deceleration: " + currentDeceleration + " m/s²");
        }

        // Update speed tracking
        lastSpeed = currentSpeed;
        lastSpeedTimestamp = currentTime;

        // For testing, let's assume a fixed deceleration when speed is above zero
        if (currentSpeed > 0) {
            // Assume moderate deceleration of 2 m/s² for testing
            double testDeceleration = 2.0;

            // Calculate distances
            double stoppingDistance = (currentSpeed * currentSpeed) / (2 * testDeceleration);
            double slowSpeed = 5.56; // 20 km/h in m/s
            double slowSpeedDistance = (currentSpeed * currentSpeed - slowSpeed * slowSpeed) / (2 * testDeceleration);

            Log.d(TAG, "Stopping Distance: " + stoppingDistance + " meters");
            Log.d(TAG, "Slow Speed Distance: " + slowSpeedDistance + " meters");

            // Generate and update arcs
            List<LatLng> completeStopPoints = generateArcPoints(location, stoppingDistance);
            List<LatLng> slowSpeedPoints = generateArcPoints(location, slowSpeedDistance);

            updateArcs(completeStopPoints, slowSpeedPoints);
        } else {
            clearArcs();
        }
    }

    private void clearArcs() {
        if (completeStopArc != null) completeStopArc.remove();
        if (slowSpeedArc != null) slowSpeedArc.remove();
        completeStopArc = null;
        slowSpeedArc = null;
    }

    private void updateArcs(List<LatLng> completeStopPoints, List<LatLng> slowSpeedPoints) {
        if (completeStopArc != null) completeStopArc.remove();
        if (slowSpeedArc != null) slowSpeedArc.remove();

        completeStopArc = map.addPolyline(new PolylineOptions()
                .addAll(completeStopPoints)
                .color(Color.RED)
                .width(5f));

        slowSpeedArc = map.addPolyline(new PolylineOptions()
                .addAll(slowSpeedPoints)
                .color(Color.YELLOW)
                .width(5f)
                .pattern(Arrays.asList(new Dot(), new Gap(20))));
    }

    private List<LatLng> generateArcPoints(Location location, double distance) {
        List<LatLng> points = new ArrayList<>();
        double bearing = location.getBearing();
        LatLng start = new LatLng(location.getLatitude(), location.getLongitude());

        // Generate arc points using great circle calculations
        for (int i = -30; i <= 30; i += 5) {
            double arcBearing = bearing + i;
            LatLng point = SphericalUtil.computeOffset(start, distance, arcBearing);
            points.add(point);
        }

        return points;
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
        } else {
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
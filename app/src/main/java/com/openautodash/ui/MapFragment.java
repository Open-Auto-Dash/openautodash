package com.openautodash.ui;

import static com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
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
import com.openautodash.R;
import com.openautodash.adapters.SearchSuggestionsAdapter;
import com.openautodash.object.PlaceSearchResult;
import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.LocationSearchManager;
import com.openautodash.utilities.RouteManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnPoiClickListener {
    private static final String TAG = "MapFragment";

    // Data Repository
    private VehicleRepository vehicleRepository;

    // Map components
    private GoogleMap map;
    private Marker marker;
    private RouteManager routeManager;
    private Location currentLocation;

    // UI Controls
    private ImageView resumeAnimate;
    private ImageView mapTypeView;
    private ImageView mapTrafficView;
    private ImageView flightDirectorView;

    // State flags
    private boolean isAnimating;
    private boolean mapMoving;
    private int lastAnimation;
    private boolean isFlightDirectorEnabled = false;

    // Flight Director Visuals
    private Polyline completeStopArc;
    private Polyline slowSpeedArc;
    private Double lastSpeed = null;
    private Long lastSpeedTimestamp = null;
    private Double currentDeceleration = null; // in m/s²
    private static final double MIN_DECELERATION = 0.1;

    // Search UI
    private EditText searchEditText;
    private ImageView clearSearchButton;
    private RecyclerView suggestionsRecyclerView;
    private CardView suggestionsCardView;
    private SearchSuggestionsAdapter suggestionsAdapter;
    private LocationSearchManager locationSearchManager;

    private LocalSettings settings;

    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize repository singleton
        vehicleRepository = VehicleRepository.getInstance(requireContext());
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
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // Initialize map fragment
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.google_map);
        if (supportMapFragment != null) {
            supportMapFragment.getMapAsync(this);
        }

        // Bind Views
        resumeAnimate = view.findViewById(R.id.iv_b_start_animate);
        mapTypeView = view.findViewById(R.id.iv_map_type);
        mapTrafficView = view.findViewById(R.id.iv_map_traffic);
        flightDirectorView = view.findViewById(R.id.iv_flight_director);

        searchEditText = view.findViewById(R.id.et_search);
        suggestionsRecyclerView = view.findViewById(R.id.rv_suggestions);
        suggestionsCardView = view.findViewById(R.id.cv_suggestions);
        clearSearchButton = view.findViewById(R.id.iv_clear_search);

        setupSearchViews();

        resumeAnimate.setOnClickListener(view1 -> {
            isAnimating = true;
            resumeAnimate.setVisibility(View.INVISIBLE);

            // Re-center on the last known location from the repository
            if (currentLocation != null) {
                updateMap(currentLocation, true);
            }
        });

        mapTypeView.setOnClickListener(v -> {
            if (map.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                mapTypeView.setBackground(requireContext().getResources().getDrawable(R.drawable.background_image_view_sellected, null));
            } else {
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                mapTypeView.setBackground(null);
            }
        });

        mapTrafficView.setOnClickListener(v -> {
            if (!map.isTrafficEnabled()) {
                map.setTrafficEnabled(true);
                mapTrafficView.setBackground(requireContext().getResources().getDrawable(R.drawable.background_image_view_sellected, null));
            } else {
                map.setTrafficEnabled(false);
                mapTrafficView.setBackground(null);
            }
        });

        flightDirectorView.setOnClickListener(v -> {
            isFlightDirectorEnabled = !isFlightDirectorEnabled;
            if (isFlightDirectorEnabled) {
                flightDirectorView.setBackground(requireContext().getResources()
                        .getDrawable(R.drawable.background_image_view_sellected, null));
            } else {
                flightDirectorView.setBackground(null);
                clearArcs();
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
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestionsRecyclerView.setAdapter(suggestionsAdapter);

        clearSearchButton.setOnClickListener(v -> clearSearchAndRoute());

        suggestionsAdapter.setOnSuggestionClickListener(suggestion -> {
            locationSearchManager.getPlaceDetails(suggestion.placeId(), new LocationSearchManager.LocationSearchCallback() {
                @Override
                public void onSearchResults(List<PlaceSearchResult> results) {
                    // Not needed
                }

                @Override
                public void onPlaceSelected(Place place) {
                    if (place.getLocation() != null && currentLocation != null) {
                        LatLng origin = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                        // Disable auto-follow when routing starts
                        isAnimating = false;
                        resumeAnimate.setVisibility(View.VISIBLE);

                        routeManager.requestRoute(origin, place.getLocation(), new RouteManager.RouteCallback() {
                            @Override
                            public void onRouteFound(DirectionsResult result) {
                                LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                                bounds.include(origin);
                                bounds.include(place.getLocation());
                                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));

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

        searchEditText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler();
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

        searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchEditText.getText().length() >= 2) {
                suggestionsCardView.setVisibility(View.VISIBLE);
            } else if (!hasFocus) {
                new Handler().postDelayed(() ->
                        suggestionsCardView.setVisibility(View.GONE), 200);
            }
        });

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
            public void onPlaceSelected(Place place) {}

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public boolean onBackPressed() {
        if (suggestionsCardView.getVisibility() == View.VISIBLE) {
            suggestionsCardView.setVisibility(View.GONE);
            searchEditText.clearFocus();
            return true;
        }
        return false;
    }

    private void clearSearchAndRoute() {
        searchEditText.setText("");
        searchEditText.clearFocus();
        suggestionsCardView.setVisibility(View.GONE);

        if (routeManager != null) {
            routeManager.clearRoutes();
        }

        clearSearchButton.setVisibility(View.GONE);

        if (currentLocation != null) {
            isAnimating = true;
            updateMap(currentLocation, true);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        routeManager = new RouteManager(requireContext().getString(R.string.google_maps_key), map);

        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnMapClickListener(this);
        map.setOnPoiClickListener(this);
        map.setPadding(0, 400, 0, 0);

        // Handle Map Style based on Repository Night Mode or System config
        int nightModeFlags = requireContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        applyMapStyle(nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        googleMap.setOnCameraMoveStartedListener(i -> {
            if (i == REASON_GESTURE) {
                Log.d(TAG, "onCameraMoveStarted: Gesture");
                isAnimating = false;
                resumeAnimate.setVisibility(View.VISIBLE);
            }
        });

        // Initial setup with default location until Repository updates
        LatLng defaultLoc = new LatLng(43.596067, -80.717016);
        CameraPosition initialCam = new CameraPosition(defaultLoc, 17, 60, 0);
        map.moveCamera(CameraUpdateFactory.newCameraPosition(initialCam));

        // Create the vehicle marker
        Bitmap resizeBitmap = bitmapSizeByScale(BitmapFactory.decodeResource(requireContext().getResources(), R.drawable.marker_red), 0.6f);
        marker = map.addMarker(new MarkerOptions()
                .position(defaultLoc)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.fromBitmap(resizeBitmap))
                .flat(true));

        // OBSERVE THE REPOSITORY
        vehicleRepository.getLocation().observe(getViewLifecycleOwner(), location -> {
            currentLocation = location;
            if (map != null && location != null) {
                updateMap(location, false);
                Log.v(TAG, "Repo Location Update: " + location.getSpeed());
            }
        });
    }

    private void applyMapStyle(boolean isNight) {
        try {
            int styleRes = isNight ? R.raw.map_night_style : R.raw.map_day_style;
            boolean success = map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), styleRes));
            if (!success) Log.e(TAG, "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }
    }

    private void updateMap(Location location, boolean forceCenter) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (marker != null) {
            setMarker(marker, latLng);
            marker.setRotation(location.getBearing());
        }

        if (isAnimating) {
            // Only move map if speed > 4m/s OR if we forced a recenter (button press)
            if ((location.getSpeed() > 4 && !mapMoving) || forceCenter) {
                mapMoving = true;

                int[] zoomTilt = getMapZoomTilt(location);

                CameraPosition newCamPos = new CameraPosition(latLng,
                        zoomTilt[0],
                        zoomTilt[1],
                        location.getBearing());

                map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 900, new GoogleMap.CancelableCallback() {
                    @Override
                    public void onFinish() { mapMoving = false; }
                    @Override
                    public void onCancel() { mapMoving = false; }
                });
                lastAnimation = 0;
            }
        } else {
            // Auto-resume logic if we haven't touched screen in a while
            lastAnimation++;
            if (lastAnimation > 45) {
                isAnimating = true;
                if(resumeAnimate != null) resumeAnimate.setVisibility(View.INVISIBLE);
            }
        }

        updateDecelerationArcs(location);
    }

    private void updateDecelerationArcs(Location location) {
        if (!isFlightDirectorEnabled) {
            clearArcs();
            return;
        }

        double currentSpeed = location.getSpeed(); // m/s
        long currentTime = SystemClock.elapsedRealtimeNanos();

        // Calculate deceleration
        if (lastSpeed != null && lastSpeedTimestamp != null) {
            double deltaV = lastSpeed - currentSpeed;
            double deltaT = (currentTime - lastSpeedTimestamp) / 1e9;
            currentDeceleration = deltaV / deltaT;
        }

        lastSpeed = currentSpeed;
        lastSpeedTimestamp = currentTime;

        if (currentSpeed > 0) {
            // Assuming standard braking physics for visualization
            double testDeceleration = 2.0;
            double stoppingDistance = (currentSpeed * currentSpeed) / (2 * testDeceleration);
            double slowSpeed = 5.56; // ~20 km/h
            double slowSpeedDistance = (currentSpeed * currentSpeed - slowSpeed * slowSpeed) / (2 * testDeceleration);

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

        for (int i = -30; i <= 30; i += 5) {
            double arcBearing = bearing + i;
            LatLng point = SphericalUtil.computeOffset(start, distance, arcBearing);
            points.add(point);
        }
        return points;
    }

    public void setMarker(final Marker marker, final LatLng toPosition) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = map.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);
        final long duration = 900;

        final Interpolator interpolator = new LinearInterpolator();
        if (startLatLng == null) {
            marker.setPosition(toPosition);
            return;
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));

                if (t < 1.0) {
                    handler.postDelayed(this, 32);
                }
            }
        });
    }

    private int[] getMapZoomTilt(Location location) {
        int speedInt = (int) (location.getSpeed() * settings.getSpeedUnits());
        int[] zoomTilt = {14, 55}; // Default

        if (speedInt < 102) { zoomTilt[0] = 15; zoomTilt[1] = 60; }
        if (speedInt < 79) { zoomTilt[0] = 17; zoomTilt[1] = 70; }
        if (speedInt < 59) { zoomTilt[0] = 18; }
        if (speedInt < 30) { zoomTilt[0] = 19; }
        return zoomTilt;
    }

    @Override
    public void onMapClick(@NonNull LatLng latLng) {
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        return false;
    }

    @Override
    public void onPoiClick(@NonNull PointOfInterest pointOfInterest) {
    }

    public Bitmap bitmapSizeByScale(Bitmap bitmapIn, float scale) {
        return Bitmap.createScaledBitmap(bitmapIn,
                Math.round(bitmapIn.getWidth() * scale),
                Math.round(bitmapIn.getHeight() * scale), false);
    }
}
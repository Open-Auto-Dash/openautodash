package com.openautodash.ui;

import static com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE;

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
import android.text.Html;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import com.google.maps.model.DirectionsStep;
import com.openautodash.R;
import com.openautodash.adapters.SearchSuggestionsAdapter;
import com.openautodash.object.PlaceSearchResult;
import com.openautodash.repositorys.VehicleRepository;
import com.openautodash.utilities.LocalSettings;
import com.openautodash.utilities.LocationSearchManager;
import com.openautodash.utilities.RouteManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MapFragment extends Fragment implements OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        GoogleMap.OnPoiClickListener {
    private static final String TAG = "MapFragment";

    // Dependencies
    private VehicleRepository vehicleRepository;
    private LocalSettings settings;
    private RouteManager routeManager;
    private LocationSearchManager locationSearchManager;
    private SearchSuggestionsAdapter suggestionsAdapter;

    // Map Stuff
    private GoogleMap map;
    private Marker marker;
    private Location currentLocation;

    // UI Controls
    private ImageView resumeAnimate, mapTypeView, mapTrafficView, flightDirectorView;
    private EditText searchEditText;
    private ImageView clearSearchButton;
    private RecyclerView suggestionsRecyclerView;
    private CardView suggestionsCardView;

    // Nav Dashboard UI
    private LinearLayout navDashboard;
    private ProgressBar routeProgressBar;
    private TextView tvEta, tvDist, tvTime;
    private TextView tvTurn1, tvTurn2, tvTurn3;

    // State
    private boolean isAnimating = true;
    private boolean mapMoving = false;
    private boolean isFlightDirectorEnabled = false;
    private boolean isNavigating = false;
    private boolean ignoreTextChange = false;

    private int lastAnimation = 0;
    private LatLng currentDest = null;
    private double totalRouteDistance = 0;

    // Flight Director Visuals
    private Polyline completeStopArc, slowSpeedArc;
    private Double lastSpeed = null;
    private Long lastSpeedTimestamp = null;
    private Double currentDeceleration = null; // MISSING VARIABLE ADDED HERE

    public MapFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vehicleRepository = VehicleRepository.getInstance(requireContext());
        settings = new LocalSettings(requireContext());
        locationSearchManager = new LocationSearchManager(requireContext());
        suggestionsAdapter = new SearchSuggestionsAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (routeManager != null) routeManager.cleanup();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.google_map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        initializeViews(view);
        setupSearchViews();
        setupMapControls();

        return view;
    }

    private void initializeViews(View view) {
        resumeAnimate = view.findViewById(R.id.iv_b_start_animate);
        mapTypeView = view.findViewById(R.id.iv_map_type);
        mapTrafficView = view.findViewById(R.id.iv_map_traffic);
        flightDirectorView = view.findViewById(R.id.iv_flight_director);

        searchEditText = view.findViewById(R.id.et_search);
        clearSearchButton = view.findViewById(R.id.iv_clear_search);
        suggestionsRecyclerView = view.findViewById(R.id.rv_suggestions);
        suggestionsCardView = view.findViewById(R.id.cv_suggestions);

        // Nav Dashboard
        navDashboard = view.findViewById(R.id.ll_nav_dashboard);
        routeProgressBar = view.findViewById(R.id.pb_route_progress);
        tvEta = view.findViewById(R.id.tv_nav_eta);
        tvDist = view.findViewById(R.id.tv_nav_dist);
        tvTime = view.findViewById(R.id.tv_nav_time);
        tvTurn1 = view.findViewById(R.id.tv_turn_1);
        tvTurn2 = view.findViewById(R.id.tv_turn_2);
        tvTurn3 = view.findViewById(R.id.tv_turn_3);
    }

    private void setupMapControls() {
        resumeAnimate.setOnClickListener(v -> {
            isAnimating = true;
            resumeAnimate.setVisibility(View.INVISIBLE);
            if (currentLocation != null) updateMap(currentLocation, true);
        });

        mapTypeView.setOnClickListener(v -> {
            if (map.getMapType() == GoogleMap.MAP_TYPE_NORMAL) {
                map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                mapTypeView.setBackground(requireContext().getResources().getDrawable(R.drawable.background_image_view_sellected));
            } else {
                map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                mapTypeView.setBackground(null);
            }
        });

        mapTrafficView.setOnClickListener(v -> {
            boolean active = !map.isTrafficEnabled();
            map.setTrafficEnabled(active);
            mapTrafficView.setBackground(active ? requireContext().getResources().getDrawable(R.drawable.background_image_view_sellected) : null);
        });

        flightDirectorView.setOnClickListener(v -> {
            isFlightDirectorEnabled = !isFlightDirectorEnabled;
            if (isFlightDirectorEnabled) {
                flightDirectorView.setBackground(requireContext().getResources().getDrawable(R.drawable.background_image_view_sellected));
            } else {
                flightDirectorView.setBackground(null);
                clearArcs();
            }
        });
    }

    private void setupSearchViews() {
        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        suggestionsRecyclerView.setAdapter(suggestionsAdapter);

        clearSearchButton.setOnClickListener(v -> clearSearchAndRoute());

        // Place Selection Handler
        suggestionsAdapter.setOnSuggestionClickListener(suggestion -> {
            locationSearchManager.getPlaceDetails(suggestion.placeId(), new LocationSearchManager.LocationSearchCallback() {
                @Override public void onSearchResults(List<PlaceSearchResult> results) {}

                @Override
                public void onPlaceSelected(Place place) {
                    if (place.getLocation() != null && currentLocation != null) {
                        LatLng origin = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                        currentDest = place.getLocation();

                        isAnimating = false;
                        resumeAnimate.setVisibility(View.VISIBLE);
                        suggestionsCardView.setVisibility(View.GONE);

                        ignoreTextChange = true;
                        searchEditText.setText(place.getDisplayName());
                        ignoreTextChange = false;

                        searchEditText.clearFocus();
                        navDashboard.setVisibility(View.VISIBLE);
                        tvTurn1.setText("Calculating route...");
                        isNavigating = true;

                        routeManager.requestRoute(origin, place.getLocation(), new RouteManager.RouteCallback() {
                            @Override
                            public void onRouteFound(DirectionsResult result) {
                                handleRouteFound(result, origin, place.getLocation());
                            }
                            @Override
                            public void onRouteError(String error) {
                                Toast.makeText(requireContext(), "Route Error", Toast.LENGTH_SHORT).show();
                                navDashboard.setVisibility(View.GONE);
                                isNavigating = false;
                            }
                        });
                    }
                }
                @Override public void onError(String message) {}
            });
        });

        // Search Text Watcher
        searchEditText.addTextChangedListener(new TextWatcher() {
            private final Handler handler = new Handler();
            private Runnable runnable;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearSearchButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreTextChange) return;

                handler.removeCallbacks(runnable);
                runnable = () -> {
                    String query = s.toString().trim();
                    if (query.length() >= 2) {
                        performSearch(query);
                        suggestionsCardView.setVisibility(View.VISIBLE);
                        navDashboard.setVisibility(View.GONE);
                    } else {
                        suggestionsCardView.setVisibility(View.GONE);
                        if (isNavigating) navDashboard.setVisibility(View.VISIBLE);
                    }
                };
                handler.postDelayed(runnable, 300);
            }
        });

        searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchEditText.getText().length() >= 2) {
                suggestionsCardView.setVisibility(View.VISIBLE);
            } else if (!hasFocus) {
                new Handler().postDelayed(() -> suggestionsCardView.setVisibility(View.GONE), 200);
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
            @Override public void onPlaceSelected(Place place) {}
            @Override public void onError(String message) {}
        });
    }

    private void clearSearchAndRoute() {
        ignoreTextChange = true;
        searchEditText.setText("");
        ignoreTextChange = false;

        searchEditText.clearFocus();
        suggestionsCardView.setVisibility(View.GONE);
        navDashboard.setVisibility(View.GONE);
        clearSearchButton.setVisibility(View.GONE);

        isNavigating = false;
        currentDest = null;

        if (routeManager != null) routeManager.clearRoutes();

        if (currentLocation != null) {
            isAnimating = true;
            updateMap(currentLocation, true);
        }
    }

    private void handleRouteFound(DirectionsResult result, LatLng origin, LatLng dest) {
        if (result.routes.length == 0) return;

        long distMeters = result.routes[0].legs[0].distance.inMeters;
        long durSeconds = result.routes[0].legs[0].duration.inSeconds;
        totalRouteDistance = distMeters;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        bounds.include(origin);
        bounds.include(dest);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));

        updateNavStats(distMeters, durSeconds);
        populateTurns(result.routes[0].legs[0].steps);
    }

    private void updateNavStats(long meters, long seconds) {
        tvDist.setText(meters > 1000 ? String.format(Locale.US, "%.1f km", meters / 1000.0) : meters + " m");

        if (seconds > 3600) {
            tvTime.setText((seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m");
        } else {
            tvTime.setText((seconds / 60) + " min");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, (int) seconds);
        int hour = calendar.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        tvEta.setText(String.format(Locale.US, "%d:%02d", hour, calendar.get(Calendar.MINUTE)));

        if (totalRouteDistance > 0) {
            int progress = (int) (100 - ((meters / totalRouteDistance) * 100));
            routeProgressBar.setProgress(Math.max(0, Math.min(100, progress)));
        }
    }

    private void populateTurns(DirectionsStep[] steps) {
        if (steps.length > 0) {
            tvTurn1.setText(formatTurnText(steps[0]));
            tvTurn1.setVisibility(View.VISIBLE);
        }
        if (steps.length > 1) {
            tvTurn2.setText(formatTurnText(steps[1]));
            tvTurn2.setVisibility(View.VISIBLE);
        } else {
            tvTurn2.setVisibility(View.GONE);
        }
        if (steps.length > 2) {
            tvTurn3.setText(formatTurnText(steps[2]));
            tvTurn3.setVisibility(View.VISIBLE);
        } else {
            tvTurn3.setVisibility(View.GONE);
        }
    }

    private String formatTurnText(DirectionsStep step) {
        String instruction = Html.fromHtml(step.htmlInstructions, Html.FROM_HTML_MODE_COMPACT).toString();
        return instruction + " (" + step.distance.humanReadable + ")";
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        routeManager = new RouteManager(requireContext().getString(R.string.google_maps_key), map);

        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);
        map.setOnPoiClickListener(this);
        map.setPadding(0, 400, 0, 0);

        int nightModeFlags = requireContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        applyMapStyle(nightModeFlags == Configuration.UI_MODE_NIGHT_YES);

        googleMap.setOnCameraMoveStartedListener(i -> {
            if (i == REASON_GESTURE) {
                isAnimating = false;
                resumeAnimate.setVisibility(View.VISIBLE);
            }
        });

        LatLng defaultLoc = new LatLng(43.596067, -80.717016);
        Bitmap markerIcon = bitmapSizeByScale(BitmapFactory.decodeResource(getResources(), R.drawable.marker_red), 0.6f);

        marker = map.addMarker(new MarkerOptions()
                .position(defaultLoc)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.fromBitmap(markerIcon))
                .flat(true));

        vehicleRepository.getLocation().observe(getViewLifecycleOwner(), location -> {
            currentLocation = location;
            if (map != null && location != null) {
                updateMap(location, false);

                if (isNavigating && currentDest != null) {
                    double dist = SphericalUtil.computeDistanceBetween(
                            new LatLng(location.getLatitude(), location.getLongitude()),
                            currentDest);
                    double speed = location.getSpeed() < 1 ? 13.8 : location.getSpeed();
                    long time = (long) (dist / speed);
                    updateNavStats((long)dist, time);
                }
            }
        });
    }

    private void applyMapStyle(boolean isNight) {
        try {
            int styleRes = isNight ? R.raw.map_night_style : R.raw.map_day_style;
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), styleRes));
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Style parsing failed.");
        }
    }

    private void updateMap(Location location, boolean forceCenter) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        if (marker != null) {
            setMarker(marker, latLng);
            marker.setRotation(location.getBearing());
        }

        if (isAnimating) {
            if ((location.getSpeed() > 4 && !mapMoving) || forceCenter) {
                mapMoving = true;
                int[] zoomTilt = getMapZoomTilt(location);
                CameraPosition newCamPos = new CameraPosition(latLng, zoomTilt[0], zoomTilt[1], location.getBearing());
                map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 900, new GoogleMap.CancelableCallback() {
                    @Override public void onFinish() { mapMoving = false; }
                    @Override public void onCancel() { mapMoving = false; }
                });
                lastAnimation = 0;
            }
        } else {
            lastAnimation++;
            if (lastAnimation > 45) {
                isAnimating = true;
                if(resumeAnimate != null) resumeAnimate.setVisibility(View.INVISIBLE);
            }
        }
        updateDecelerationArcs(location);
    }

    private void updateDecelerationArcs(Location location) {
        if (!isFlightDirectorEnabled) { clearArcs(); return; }

        double currentSpeed = location.getSpeed();
        long currentTime = SystemClock.elapsedRealtimeNanos();

        if (lastSpeed != null && lastSpeedTimestamp != null) {
            double deltaV = lastSpeed - currentSpeed;
            double deltaT = (currentTime - lastSpeedTimestamp) / 1e9;
            currentDeceleration = deltaV / deltaT;
        }
        lastSpeed = currentSpeed;
        lastSpeedTimestamp = currentTime;

        if (currentSpeed > 0) {
            double testDeceleration = 2.0;
            double stoppingDistance = (currentSpeed * currentSpeed) / (2 * testDeceleration);
            double slowSpeed = 5.56;
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

        completeStopArc = map.addPolyline(new PolylineOptions().addAll(completeStopPoints).color(Color.RED).width(5f));
        slowSpeedArc = map.addPolyline(new PolylineOptions().addAll(slowSpeedPoints).color(Color.YELLOW).width(5f).pattern(Arrays.asList(new Dot(), new Gap(20))));
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
        if (startLatLng == null) { marker.setPosition(toPosition); return; }

        handler.post(new Runnable() {
            @Override public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));
                if (t < 1.0) handler.postDelayed(this, 32);
            }
        });
    }

    private int[] getMapZoomTilt(Location location) {
        int speedInt = (int) (location.getSpeed() * settings.getSpeedUnits());
        int[] zoomTilt = {14, 55};
        if (speedInt < 102) { zoomTilt[0] = 15; zoomTilt[1] = 60; }
        if (speedInt < 79) { zoomTilt[0] = 17; zoomTilt[1] = 70; }
        if (speedInt < 59) { zoomTilt[0] = 18; }
        if (speedInt < 30) { zoomTilt[0] = 19; }
        return zoomTilt;
    }

    public Bitmap bitmapSizeByScale(Bitmap bitmapIn, float scale) {
        return Bitmap.createScaledBitmap(bitmapIn,
                Math.round(bitmapIn.getWidth() * scale),
                Math.round(bitmapIn.getHeight() * scale), false);
    }

    @Override public void onMapClick(@NonNull LatLng latLng) {}
    @Override public boolean onMarkerClick(@NonNull Marker marker) { return false; }
    @Override public void onPoiClick(@NonNull PointOfInterest pointOfInterest) {}
}
package com.openautodash.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.SupportNavigationFragment;
import com.google.android.libraries.navigation.TimeAndDistance;
import com.google.android.libraries.navigation.Waypoint;
import com.openautodash.MapViewModel;
import com.openautodash.R;
import com.openautodash.repositorys.VehicleRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MapFragment extends Fragment {
    private static final String TAG = "MapFragment";

    private Navigator mNavigator;
    private SupportNavigationFragment mNavFragment;
    private VehicleRepository vehicleRepository;
    private MapViewModel viewModel; // The Bridge

    // State
    private boolean isSdkInitialized = false;
    private Marker customMarker;
    private boolean isNavigating = false;
    private boolean mapMoving = false; // Used for "Free Drive" camera control
    private Navigator.RemainingTimeOrDistanceChangedListener navListener;

    public MapFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vehicleRepository = VehicleRepository.getInstance(requireContext());
        // Link to the Shared ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // This layout is just a container for the Google Nav Fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupNavigationFragment();
        initializeNavigationSdk();
        setupViewModelObservers();
    }

    private void setupViewModelObservers() {
        // 1. Listen for Start Navigation Command
        viewModel.getStartNavigationCommand().observe(getViewLifecycleOwner(), waypoint -> {
            if (waypoint != null) {
                executeStartNavigation(waypoint);
            }
        });

        // 2. Listen for Stop Navigation Command
        viewModel.getStopNavigationCommand().observe(getViewLifecycleOwner(), stop -> {
            if (stop != null && stop) {
                executeStopNavigation();
            }
        });

        // --- NEW CONTROLS ---

        // 3. Traffic Toggle
        viewModel.getIsTrafficEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (mNavFragment != null) {
                mNavFragment.getMapAsync(map -> {
                    try {
                        map.setTrafficEnabled(enabled);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Traffic permission error", e);
                    }
                });
            }
        });

        // 4. Satellite Toggle
        viewModel.getIsSatelliteEnabled().observe(getViewLifecycleOwner(), enabled -> {
            if (mNavFragment != null) {
                mNavFragment.getMapAsync(map -> {
                    if (enabled) {
                        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    } else {
                        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                });
            }
        });

        // 5. Audio Guidance
        viewModel.getAudioGuidanceState().observe(getViewLifecycleOwner(), state -> {
            if (mNavigator != null && state != null) {
                mNavigator.setAudioGuidance(state);
                Log.d(TAG, "Audio Guidance Set To: " + state);
            }
        });
    }

    // --- NAVIGATION LOGIC ---

    @SuppressLint("MissingPermission")
    private void executeStartNavigation(Waypoint destination) {
        if (mNavigator == null) return;
        isNavigating = true;

        mNavigator.setDestination(destination, new RoutingOptions().travelMode(RoutingOptions.TravelMode.DRIVING))
                .setOnResultListener(code -> {
                    if (code == Navigator.RouteStatus.OK) {
                        // Apply current audio setting immediately upon start
                        Integer audioState = viewModel.getAudioGuidanceState().getValue();
                        if (audioState != null) {
                            mNavigator.setAudioGuidance(audioState);
                        } else {
                            mNavigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE);
                        }

                        mNavigator.startGuidance();
                        setupNavListeners();

                        if (mNavFragment != null) {
                            mNavFragment.getMapAsync(googleMap -> {
                                // NAV MODE: Push center to the LEFT (325dp padding on Right)
                                float scale = getResources().getDisplayMetrics().density;
                                int paddingRight = (int) (325 * scale + 0.5f);

                                googleMap.setPadding(0, 0, paddingRight, 0);
                                googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED);

                                // Push Google's UI out of the way
                                moveTripViewPagerDown();
                            });
                        }
                    }
                });
    }

    private void executeStopNavigation() {
        if (mNavigator != null) {
            mNavigator.stopGuidance();
            mNavigator.clearDestinations(); // Clear the blue line

            if (navListener != null) {
                mNavigator.removeRemainingTimeOrDistanceChangedListener(navListener);
                navListener = null;
            }
        }
        isNavigating = false;

        // FREE DRIVE MODE: Restore Padding (Push center DOWN)
        if (mNavFragment != null) {
            mNavFragment.getMapAsync(googleMap -> {
                // 400px bottom padding so car sits low
                googleMap.setPadding(0, 400, 0, 0);
            });
        }
    }

    private void setupNavListeners() {
        if (mNavigator == null) return;
        navListener = () -> {
            List<TimeAndDistance> list = mNavigator.getTimeAndDistanceList();
            if (list != null && !list.isEmpty()) {
                TimeAndDistance td = list.get(0);

                // --- Format Data ---
                long secondsRemaining = td.getSeconds();
                long metersRemaining = td.getMeters();
                String timeStr, distStr, etaStr;

                // Time
                int minutes = (int) (secondsRemaining / 60);
                int hours = minutes / 60;
                minutes = minutes % 60;
                timeStr = (hours > 0) ? String.format("%dh %02dm", hours, minutes) : minutes + " min";

                // Distance
                distStr = (metersRemaining >= 1000)
                        ? String.format("%.1f km", metersRemaining / 1000.0)
                        : metersRemaining + " m";

                // ETA
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.SECOND, (int) secondsRemaining);
                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
                etaStr = sdf.format(calendar.getTime());

                // --- PUSH TO VIEW MODEL (Overlay will update UI) ---
                viewModel.updateNavStats(timeStr, distStr, etaStr);
            }
        };
        mNavigator.addRemainingTimeOrDistanceChangedListener(10, 50, navListener);
    }

    // --- MAP & CAMERA UPDATES ---

    private void startUpdatingCustomMarker() {
        vehicleRepository.getLocation().observe(getViewLifecycleOwner(), location -> {
            if (location == null) return;

            // 1. Always update Marker
            if (customMarker != null) {
                LatLng newPos = new LatLng(location.getLatitude(), location.getLongitude());
                setMarker(customMarker, newPos);
                customMarker.setRotation(location.getBearing());
            }

            // 2. Control Camera (Only in Free Drive Mode)
            if (!isNavigating && mNavFragment != null) {
                mNavFragment.getMapAsync(map -> {
                    updateFreeDriveCamera(map, location);
                });
            }
        });
    }

    private void updateFreeDriveCamera(GoogleMap map, android.location.Location location) {
        if (location.getSpeed() > 1 && !mapMoving) {
            mapMoving = true;

            int[] zoomTilt = getMapZoomTilt(location);

            CameraPosition newCamPos = new CameraPosition(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    zoomTilt[0],
                    zoomTilt[1],
                    location.getBearing());

            map.animateCamera(CameraUpdateFactory.newCameraPosition(newCamPos), 950, new GoogleMap.CancelableCallback() {
                @Override public void onFinish() { mapMoving = false; }
                @Override public void onCancel() { mapMoving = false; }
            });
        }
    }

    private int[] getMapZoomTilt(android.location.Location location) {
        int speedKph = (int) (location.getSpeed() * 3.6f);
        int[] zoomTilt = {14, 55};

        if (speedKph < 102) { zoomTilt[0] = 15; zoomTilt[1] = 60; }
        if (speedKph < 79)  { zoomTilt[0] = 17; zoomTilt[1] = 70; }
        if (speedKph < 59)  { zoomTilt[0] = 18; zoomTilt[1] = 70; }
        if (speedKph < 30)  { zoomTilt[0] = 19; zoomTilt[1] = 0;  }
        return zoomTilt;
    }

    public void setMarker(final Marker marker, final LatLng toPosition) {
        if (mNavFragment == null) return;
        mNavFragment.getMapAsync(map -> {
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
                        handler.postDelayed(this, 16);
                    }
                }
            });
        });
    }

    // --- UI HACKS & SETUP ---

    private void moveTripViewPagerDown() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            View rootView = mNavFragment.getView();
            if (rootView == null) return;

            int resId = getResources().getIdentifier("trip_view_pager", "id", requireContext().getPackageName());
            if (resId == 0) {
                resId = getResources().getIdentifier("trip_view_pager", "id", "com.google.android.libraries.navigation");
            }

            if (resId != 0) {
                final View tripPager = rootView.findViewById(resId);
                if (tripPager != null) {
                    float scale = getResources().getDisplayMetrics().density;
                    final float translationY = 90 * scale; // Adjust if needed

                    View current = tripPager;
                    while (current.getParent() instanceof ViewGroup) {
                        ViewGroup parent = (ViewGroup) current.getParent();
                        parent.setClipChildren(false);
                        parent.setClipToPadding(false);
                        if (parent == rootView) break;
                        current = parent;
                    }

                    tripPager.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        if (tripPager.getTranslationY() != translationY) {
                            tripPager.setTranslationY(translationY);
                        }
                    });

                    tripPager.setTranslationY(translationY);

                } else {
                    findAndNuclearRecursively((ViewGroup) rootView, resId);
                }
            }
        }, 1000);
    }

    private void findAndNuclearRecursively(ViewGroup parent, int targetId) {
        float scale = getResources().getDisplayMetrics().density;
        final float translationY = 90 * scale;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getId() == targetId) {
                View current = child;
                while (current.getParent() instanceof ViewGroup) {
                    ViewGroup p = (ViewGroup) current.getParent();
                    p.setClipChildren(false);
                    p.setClipToPadding(false);
                    if (p == mNavFragment.getView()) break;
                    current = p;
                }
                child.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (child.getTranslationY() != translationY) {
                        child.setTranslationY(translationY);
                    }
                });
                child.setTranslationY(translationY);
                return;
            }
            if (child instanceof ViewGroup) {
                findAndNuclearRecursively((ViewGroup) child, targetId);
            }
        }
    }

    private void handleNightMode() {
        if (mNavFragment == null) return;
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            mNavFragment.setForceNightMode(2);
        } else {
            mNavFragment.setForceNightMode(1);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            mNavFragment.setForceNightMode(2);
        } else {
            mNavFragment.setForceNightMode(1);
        }
    }

    private void setupNavigationFragment() {
        FragmentManager fm = getChildFragmentManager();
        mNavFragment = (SupportNavigationFragment) fm.findFragmentByTag("NAV_FRAG");

        if (mNavFragment == null) {
            mNavFragment = SupportNavigationFragment.newInstance();
            fm.beginTransaction()
                    .replace(R.id.nav_host_container, mNavFragment, "NAV_FRAG")
                    .commitNow();
        }

        handleNightMode();

        mNavFragment.setSpeedLimitIconEnabled(false);
        mNavFragment.setEtaCardEnabled(false);
        mNavFragment.setTripProgressBarEnabled(true);
        mNavFragment.setReportIncidentButtonEnabled(false);
        mNavFragment.setTrafficPromptsEnabled(true);

        mNavFragment.getMapAsync(googleMap -> {
            try {
                googleMap.getUiSettings().setCompassEnabled(false);
                googleMap.setMyLocationEnabled(false);
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);

                // 1. Set Free Drive Padding (Bottom Offset: 400px)
                googleMap.setPadding(0, 400, 0, 0);

                // 2. Add Long Click Listener for "Dropped Pin" Navigation
                googleMap.setOnMapLongClickListener(latLng -> {
                    // FIX: Use default Builder (inherits App Theme automatically)
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Navigate Here?")
                            .setMessage("Do you want to start navigation to this point?")
                            .setPositiveButton("Go", (dialog, which) -> {
                                // 3. Build Waypoint from LatLng
                                Waypoint destination = Waypoint.builder()
                                        .setLatLng(latLng.latitude, latLng.longitude)
                                        .setTitle("Dropped Pin")
                                        .build();

                                // Send Command to ViewModel (Overlay updates, Map starts)
                                viewModel.requestStartNavigation(destination);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });

            } catch (SecurityException e) {
                Log.e(TAG, "Permission error", e);
            }

            if (customMarker == null && getContext() != null) {
                LatLng startPos = new LatLng(0, 0);
                Bitmap original = BitmapFactory.decodeResource(getResources(), R.drawable.marker_red);
                Bitmap scaled = Bitmap.createScaledBitmap(original,
                        (int)(original.getWidth() * 0.6f),
                        (int)(original.getHeight() * 0.6f), false);

                customMarker = googleMap.addMarker(new MarkerOptions()
                        .position(startPos)
                        .icon(BitmapDescriptorFactory.fromBitmap(scaled))
                        .anchor(0.5f, 0.5f)
                        .flat(true));
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void initializeNavigationSdk() {
        if (isSdkInitialized) return;

        if (NavigationApi.areTermsAccepted(requireActivity().getApplication())) {
            requestNavigator();
        } else {
            NavigationApi.showTermsAndConditionsDialog(
                    requireActivity(), "OpenAutoDash", "Navigation Terms",
                    new NavigationApi.OnTermsResponseListener() {
                        @Override
                        public void onTermsResponse(boolean accepted) {
                            if (accepted) requestNavigator();
                        }
                    });
        }
    }

    @SuppressLint("MissingPermission")
    private void requestNavigator() {
        NavigationApi.getNavigator(requireActivity(), new NavigationApi.NavigatorListener() {
            @Override
            public void onNavigatorReady(Navigator navigator) {
                mNavigator = navigator;
                isSdkInitialized = true;
                startUpdatingCustomMarker();
            }
            @Override public void onError(int errorCode) {}
        });
    }
}
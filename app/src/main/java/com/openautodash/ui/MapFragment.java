package com.openautodash.ui;

import android.content.pm.PackageManager;
import android.content.res.Configuration; // Import Configuration
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.navigation.NavigationApi;
import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.RoutingOptions;
import com.google.android.libraries.navigation.SupportNavigationFragment;
import com.google.android.libraries.navigation.Waypoint;
import com.openautodash.R;
import com.openautodash.repositorys.VehicleRepository;

public class MapFragment extends Fragment {

    private static final String TAG = "MapFragment";
    private static final String DESTINATION_PLACE_ID = "ChIJbWMMXjTjK4gRIwDdujgamhM";
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private Navigator mNavigator;
    private SupportNavigationFragment mNavFragment;
    private VehicleRepository vehicleRepository;

    private boolean isSdkInitialized = false;
    private Marker customMarker;

    public MapFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vehicleRepository = VehicleRepository.getInstance(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupNavigationFragment();
        initializeNavigationSdk();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            Log.d(TAG, "Config Changed: DARK. Forcing Map Night.");
            mNavFragment.setForceNightMode(2); // FORCE_NIGHT_ON
        } else {
            Log.d(TAG, "Config Changed: LIGHT. Forcing Map Day.");
            mNavFragment.setForceNightMode(1); // FORCE_NIGHT_OFF
        }
    }

    private void handleNightMode() {
        if (mNavFragment == null) return;

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            Log.d(TAG, "System is Dark. Forcing Night Mode.");
            mNavFragment.setForceNightMode(2);
        } else {
            Log.d(TAG, "System is Light. Forcing Day Mode.");
            mNavFragment.setForceNightMode(1);
        }
    }
    // -------------------------------------------------

    private void setupNavigationFragment() {
        FragmentManager fm = getChildFragmentManager();
        mNavFragment = (SupportNavigationFragment) fm.findFragmentByTag("NAV_FRAG");

        if (mNavFragment == null) {
            mNavFragment = SupportNavigationFragment.newInstance();
            fm.beginTransaction()
                    .replace(R.id.nav_host_container, mNavFragment, "NAV_FRAG")
                    .commitNow();
        }

        // Apply Night Mode settings immediately on setup
        handleNightMode();

        mNavFragment.setSpeedLimitIconEnabled(false);
        mNavFragment.setEtaCardEnabled(false);
        mNavFragment.setTripProgressBarEnabled(true);
        mNavFragment.setReportIncidentButtonEnabled(true);
        mNavFragment.setTrafficPromptsEnabled(true);

        mNavFragment.getMapAsync(googleMap -> {
            try {
                googleMap.getUiSettings().setCompassEnabled(false);
                googleMap.setMyLocationEnabled(false);
                googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED);
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

    private void initializeNavigationSdk() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }

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

    private void requestNavigator() {
        NavigationApi.getNavigator(requireActivity(), new NavigationApi.NavigatorListener() {
            @Override
            public void onNavigatorReady(Navigator navigator) {
                mNavigator = navigator;
                isSdkInitialized = true;

                startUpdatingCustomMarker();
                // Speed Limit listener removed as requested

                navigate();
            }
            @Override public void onError(int errorCode) {}
        });
    }

    private void navigate() {
        if (mNavigator == null) return;

        Waypoint destination;
        try {
            destination = Waypoint.builder().setPlaceIdString(DESTINATION_PLACE_ID).build();
        } catch (Waypoint.UnsupportedPlaceIdException e) { return; }

        mNavigator.setDestination(destination, new RoutingOptions().travelMode(RoutingOptions.TravelMode.DRIVING))
                .setOnResultListener(code -> {
                    if (code == Navigator.RouteStatus.OK) {
                        mNavigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_ONLY);
                        mNavigator.startGuidance();

                        if (mNavFragment != null) {
                            mNavFragment.getMapAsync(googleMap -> {
                                googleMap.setPadding(0, 0, 0, 0);
                                googleMap.followMyLocation(GoogleMap.CameraPerspective.TILTED);
                                moveTripViewPagerDown(); // Your UI Hack
                            });
                        }
                    }
                });
    }

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
                    final float translationY = 40 * scale;

                    // 1. DISABLE CLIPPING (Hierarchy Fix)
                    View current = tripPager;
                    while (current.getParent() instanceof ViewGroup) {
                        ViewGroup parent = (ViewGroup) current.getParent();
                        parent.setClipChildren(false);
                        parent.setClipToPadding(false);
                        if (parent == rootView) break;
                        current = parent;
                    }

                    // 2. STICKY TRANSLATION
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
        final float translationY = 40 * scale;

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

    private void startUpdatingCustomMarker() {
        vehicleRepository.getLocation().observe(getViewLifecycleOwner(), location -> {
            if (location != null && customMarker != null) {
                LatLng newPos = new LatLng(location.getLatitude(), location.getLongitude());
                setMarker(customMarker, newPos);
                customMarker.setRotation(location.getBearing());
            }
        });
    }

    public void setMarker(final Marker marker, final LatLng toPosition) {
        if (mNavFragment == null) return;

        mNavFragment.getMapAsync(map -> {
            final Handler handler = new Handler();
            final long start = SystemClock.uptimeMillis();
            Projection proj = map.getProjection();
            Point startPoint = proj.toScreenLocation(marker.getPosition());
            final LatLng startLatLng = proj.fromScreenLocation(startPoint);
            final long duration = 990;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupNavigationFragment();
            initializeNavigationSdk();
        }
    }
}
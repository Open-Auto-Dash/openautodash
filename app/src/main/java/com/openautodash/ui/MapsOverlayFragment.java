package com.openautodash.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.Waypoint;
import com.google.android.libraries.places.api.model.Place;
import com.openautodash.MapViewModel;
import com.openautodash.R;
import com.openautodash.adapters.SearchAdapter;
import com.openautodash.object.PlaceSearchResult;
import com.openautodash.utilities.LocationSearchManager;

import java.util.List;

public class MapsOverlayFragment extends Fragment implements LocationSearchManager.LocationSearchCallback {
    private static final String TAG = "MapsOverlayFragment";

    private MapViewModel viewModel;
    private LocationSearchManager searchManager;
    private SearchAdapter searchAdapter;

    // --- UI: Search & Nav Header ---
    private EditText searchBar;
    private RecyclerView searchResultsRv;
    private ConstraintLayout navInfoHeader;
    private TextView tvEta, tvDistance, tvTime, btnExitNav;

    // --- UI: Map Controls ---
    private ImageView btnTraffic, btnSat;

    // --- UI: Volume Control ---
    private CardView cardVolumeControl;

    private CardView cardTripInfo;
    private TextView tvTripDist;
    private ToggleButton btnTripBusiness;
    private ImageView btnTripStop;
    private ImageView btnVolMain, btnVolAlert, btnVolMute;
    private boolean isVolumeExpanded = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MapViewModel.class);
        searchManager = new LocationSearchManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps_overlay, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupListeners();
        setupObservers();
    }

    private void initViews(View view) {
        // Search & Header
        searchBar = view.findViewById(R.id.et_search_bar);
        searchResultsRv = view.findViewById(R.id.rv_search_results);
        navInfoHeader = view.findViewById(R.id.cl_nav_info_header);
        tvEta = view.findViewById(R.id.tv_nav_eta);
        tvDistance = view.findViewById(R.id.tv_nav_distance);
        tvTime = view.findViewById(R.id.tv_nav_time);
        btnExitNav = view.findViewById(R.id.btn_exit_nav);

        // Map Controls
        btnTraffic = view.findViewById(R.id.iv_map_traffic);
        btnSat = view.findViewById(R.id.iv_map_type);

        // Volume Controls
        cardVolumeControl = view.findViewById(R.id.card_volume_control);
        btnVolMain = view.findViewById(R.id.btn_vol_main);
        btnVolAlert = view.findViewById(R.id.btn_vol_alert);
        btnVolMute = view.findViewById(R.id.btn_vol_mute);

        // Setup RecyclerView
        searchResultsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        searchAdapter = new SearchAdapter(this::onPlaceSuggestionClicked);
        searchResultsRv.setAdapter(searchAdapter);

        // Trip Controls
        cardTripInfo = view.findViewById(R.id.card_trip_info);
        tvTripDist = view.findViewById(R.id.tv_trip_dist);
        btnTripBusiness = view.findViewById(R.id.btn_trip_business);
        btnTripStop = view.findViewById(R.id.btn_trip_stop);
    }

    private void setupListeners() {
        // --- Search Bar ---
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) {
                    searchManager.searchPlaces(s.toString(), MapsOverlayFragment.this);
                } else {
                    searchResultsRv.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- Buttons ---
        btnExitNav.setOnClickListener(v -> viewModel.requestStopNavigation());

        // Map Toggles
        btnTraffic.setOnClickListener(v -> viewModel.toggleTraffic());
        btnSat.setOnClickListener(v -> viewModel.toggleSatellite());

        // --- VOLUME LOGIC FIX ---

        // 1. Main Button (The Anchor)
        btnVolMain.setOnClickListener(v -> {
            if (isVolumeExpanded) {
                // If Expanded: Clicking this icon means "Select Normal Voice"
                viewModel.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE);
                toggleVolumeExpand();
            } else {
                // If Collapsed: Clicking this opens the menu
                toggleVolumeExpand();
            }
        });

        // 2. Mute Button
        btnVolMute.setOnClickListener(v -> {
            viewModel.setAudioGuidance(Navigator.AudioGuidance.SILENT);
            toggleVolumeExpand(); // Select & Close
        });

        // 3. Alert Button
        btnVolAlert.setOnClickListener(v -> {
            viewModel.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_ONLY);
            toggleVolumeExpand(); // Select & Close
        });

        // Trip Controlls
        btnTripStop.setOnClickListener(v -> viewModel.stopTrip());

        btnTripBusiness.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setTripBusiness(isChecked);
            // Visual feedback
            btnTripBusiness.setAlpha(isChecked ? 1.0f : 0.3f);
        });
    }

    private void toggleVolumeExpand() {
        isVolumeExpanded = !isVolumeExpanded;

        if (isVolumeExpanded) {
            // EXPAND: Show hidden options to the left
            // We set the Main icon to "Voice" temporarily so the list looks like [Mute][Alert][Voice]
            btnVolMain.setImageResource(R.drawable.ic_volume);
            btnVolMute.setVisibility(View.VISIBLE);
            btnVolAlert.setVisibility(View.VISIBLE);
        } else {
            // COLLAPSE: Hide options
            btnVolMute.setVisibility(View.GONE);
            btnVolAlert.setVisibility(View.GONE);

            // Restore the main icon to match the ACTUAL current state
            updateMainVolumeIcon(viewModel.getAudioGuidanceState().getValue());
        }
    }

    private void setupObservers() {
        // 1. Navigation State (Show/Hide Header vs Search)
        viewModel.getIsNavigating().observe(getViewLifecycleOwner(), isNavigating -> {
            if (isNavigating) {
                searchBar.setVisibility(View.GONE);
                searchResultsRv.setVisibility(View.GONE);
                navInfoHeader.setVisibility(View.VISIBLE);
            } else {
                searchBar.setVisibility(View.VISIBLE);
                searchBar.setText("");
                navInfoHeader.setVisibility(View.GONE);
            }
        });

        // 2. Navigation Stats
        viewModel.getNavStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                tvTime.setText(stats.time);
                tvDistance.setText(stats.distance);
                tvEta.setText(stats.eta);
            }
        });


        // 3. Traffic Toggle UI
        viewModel.getIsTrafficEnabled().observe(getViewLifecycleOwner(), enabled -> {
            // Dim if disabled, Bright if enabled
            btnTraffic.setBackground(enabled ? ResourcesCompat.getDrawable(getResources(), R.drawable.background_image_view_sellected, null) : null);
        });

        // 4. Satellite Toggle UI
        viewModel.getIsSatelliteEnabled().observe(getViewLifecycleOwner(), enabled -> {
            btnSat.setBackground(enabled ? ResourcesCompat.getDrawable(getResources(), R.drawable.background_image_view_sellected, null) : null);
        });

        // 5. Volume Icon UI
        viewModel.getAudioGuidanceState().observe(getViewLifecycleOwner(), state -> {
            // Only update the main icon if we are COLLAPSED.
            // If expanded, we want the icons to remain static options.
            if (!isVolumeExpanded) {
                updateMainVolumeIcon(state);
            }
        });


        // Trip Controls
        viewModel.getCurrentTrip().observe(getViewLifecycleOwner(), trip -> {
            if (trip != null && trip.isOpen()) {
                cardTripInfo.setVisibility(View.VISIBLE);

                // Update Distance
                float distKm = trip.getDistanceMeters() / 1000f;
                tvTripDist.setText(String.format("%.1f km", distKm));

                // Update Toggle State (prevent loop)
                if (btnTripBusiness.isChecked() != trip.isBusiness()) {
                    btnTripBusiness.setChecked(trip.isBusiness());
                }
            } else {
                cardTripInfo.setVisibility(View.GONE);
            }
        });
    }

    private void updateMainVolumeIcon(Integer state) {
        if (state == null) return;

        int iconRes;
        if (state == Navigator.AudioGuidance.SILENT) {
            iconRes = R.drawable.ic_volume_mute;
        } else if (state == Navigator.AudioGuidance.VOICE_ALERTS_ONLY) {
            iconRes = R.drawable.ic_volume_alert;
        } else {
            iconRes = R.drawable.ic_volume; // Voice Guidance (Normal)
        }
        btnVolMain.setImageResource(iconRes);
    }

    // --- Search Logic ---
    private void onPlaceSuggestionClicked(PlaceSearchResult result) {
        hideKeyboard();
        searchBar.clearFocus();
        searchResultsRv.setVisibility(View.GONE);
        searchBar.setText(result.primaryText());

        try {
            Waypoint destination = Waypoint.builder().setPlaceIdString(result.placeId()).build();
            viewModel.requestStartNavigation(destination);
        } catch (Waypoint.UnsupportedPlaceIdException e) {
            Log.e(TAG, "Invalid Place ID", e);
        }
    }

    @Override
    public void onSearchResults(List<PlaceSearchResult> results) {
        if (results != null && !results.isEmpty()) {
            searchAdapter.updateData(results);
            searchResultsRv.setVisibility(View.VISIBLE);
        } else {
            searchResultsRv.setVisibility(View.GONE);
        }
    }

    @Override public void onError(String message) {}
    @Override public void onPlaceSelected(Place place) {}

    private void hideKeyboard() {
        if (getActivity() != null && getView() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
    }
}
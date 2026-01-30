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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    // UI
    private EditText searchBar;
    private RecyclerView searchResultsRv;
    private ConstraintLayout navInfoHeader;
    private TextView tvEta, tvDistance, tvTime, btnExitNav;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Grab the ViewModel tied to the Activity (shared)
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
        setupObservers();
    }

    private void initViews(View view) {
        searchBar = view.findViewById(R.id.et_search_bar);
        searchResultsRv = view.findViewById(R.id.rv_search_results);
        navInfoHeader = view.findViewById(R.id.cl_nav_info_header);
        tvEta = view.findViewById(R.id.tv_nav_eta);
        tvDistance = view.findViewById(R.id.tv_nav_distance);
        tvTime = view.findViewById(R.id.tv_nav_time);
        btnExitNav = view.findViewById(R.id.btn_exit_nav);

        // Setup Search
        searchResultsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        searchAdapter = new SearchAdapter(this::onPlaceSuggestionClicked);
        searchResultsRv.setAdapter(searchAdapter);

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

        // Exit Navigation Button
        btnExitNav.setOnClickListener(v -> viewModel.requestStopNavigation());
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
                searchBar.setText(""); // Clear prev search
                navInfoHeader.setVisibility(View.GONE);
            }
        });

        // 2. Navigation Stats Updates (ETA, Time, Dist)
        viewModel.getNavStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                tvTime.setText(stats.time);
                tvDistance.setText(stats.distance);
                tvEta.setText(stats.eta);
            }
        });
    }

    // --- Search Logic ---
    private void onPlaceSuggestionClicked(PlaceSearchResult result) {
        hideKeyboard();
        searchBar.clearFocus();
        searchResultsRv.setVisibility(View.GONE);
        searchBar.setText(result.primaryText());

        try {
            Waypoint destination = Waypoint.builder().setPlaceIdString(result.placeId()).build();
            // Send Command to MapFragment via ViewModel
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
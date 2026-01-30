package com.openautodash.ui.menu;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.openautodash.R;
import com.openautodash.adapters.TripAdapter;
import com.openautodash.database.Trip;

public class MenuTrips extends Fragment implements TripAdapter.OnTripActionListener {

    private TripsViewModel viewModel;
    private TripAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu_trips, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.rv_trips_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TripAdapter(this);
        recyclerView.setAdapter(adapter);

        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            adapter.setTrips(trips);
        });
    }

    @Override
    public void onTripClick(Trip trip) {
        long endTime = trip.isOpen() ? System.currentTimeMillis() : trip.getEndTime();
        long duration = endTime - trip.getStartTime();

        TripDetailsBottomSheet sheet = TripDetailsBottomSheet.newInstance(
                trip.getStartTime(),
                trip.getDistanceMeters(),
                duration,
                trip.isBusiness()
        );
        sheet.show(getParentFragmentManager(), "TripDetails");
    }

    @Override
    public void onDeleteClick(Trip trip) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Trip?")
                .setMessage("This will remove the trip record, but keep the telemetry logs.")
                .setPositiveButton("Delete", (dialog, which) -> viewModel.deleteTrip(trip))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
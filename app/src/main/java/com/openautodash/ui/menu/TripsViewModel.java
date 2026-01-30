package com.openautodash.ui.menu;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.openautodash.database.DatabaseRepository;
import com.openautodash.database.Trip;
import java.util.List;

public class TripsViewModel extends AndroidViewModel {
    private final DatabaseRepository repository;
    private final LiveData<List<Trip>> allTrips;
    private final MutableLiveData<Trip> selectedTrip = new MutableLiveData<>();

    public TripsViewModel(@NonNull Application application) {
        super(application);
        repository = new DatabaseRepository(application);
        allTrips = repository.getAllTrips();
    }

    public LiveData<List<Trip>> getAllTrips() { return allTrips; }

    public LiveData<Trip> getSelectedTrip() { return selectedTrip; }

    public void selectTrip(Trip trip) {
        selectedTrip.setValue(trip);
    }

    public void deleteTrip(Trip trip) {
        repository.deleteTrip(trip);
    }
}
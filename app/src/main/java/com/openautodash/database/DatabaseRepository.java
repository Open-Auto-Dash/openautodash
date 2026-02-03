package com.openautodash.database;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseRepository {

    private TelemetryLogDao telemetryLogDao;
    private TripDao tripDao;
    private LiveData<List<TelemetryLog>> logs;
    private LiveData<Trip> currentOpenTrip;

    // Executor for background DB operations (Replacing AsyncTask)
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DatabaseRepository(Application application){
        TelemetryLogDatabase database = TelemetryLogDatabase.getInstance(application);
        telemetryLogDao = database.telemetryLogDao();
        tripDao = database.tripDao();
        logs = telemetryLogDao.getAllLogs();
        currentOpenTrip = tripDao.getLiveOpenTrip();
    }

    // --- Telemetry Methods ---
    public void insertTelemetryLog(TelemetryLog telemetryLog){
        executor.execute(() -> telemetryLogDao.insert(telemetryLog));
    }

    // --- Trip Methods ---
    public LiveData<Trip> getLiveOpenTrip() { return currentOpenTrip; }

    public void startNewTrip(Trip trip) {
        executor.execute(() -> tripDao.insert(trip));
    }

    public void updateTrip(Trip trip) {
        executor.execute(() -> tripDao.update(trip));
    }

    public void deleteTrip(Trip trip) {
        executor.execute(() -> tripDao.delete(trip));
    }

    public LiveData<List<Trip>> getAllTrips() {
        return tripDao.getAllTrips();
    }

    // Synchronous check (executed on background thread)
    public void checkAndManageTrips(TripManagementCallback callback) {
        executor.execute(() -> {
            Trip openTrip = tripDao.getOpenTrip();
            if (callback != null) {
                callback.onCheckComplete(openTrip);
            }
        });
    }

    public interface TripManagementCallback {
        void onCheckComplete(Trip openTrip);
    }
}
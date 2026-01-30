package com.openautodash.database;

import android.app.Application;
import android.os.AsyncTask;
import androidx.lifecycle.LiveData;
import java.util.List;

public class DatabaseRepository {

    private TelemetryLogDao telemetryLogDao;
    private TripDao tripDao;
    private LiveData<List<TelemetryLog>> logs;
    private LiveData<Trip> currentOpenTrip;

    public DatabaseRepository(Application application){
        TelemetryLogDatabase database = TelemetryLogDatabase.getInstance(application);
        telemetryLogDao = database.telemetryLogDao();
        tripDao = database.tripDao();
        logs = telemetryLogDao.getAllLogs();
        currentOpenTrip = tripDao.getLiveOpenTrip();
    }

    // --- Telemetry Methods (Existing) ---
    public void insertTelemetryLog(TelemetryLog telemetryLog){
        new InsertTelemetryLogAsyncTask(telemetryLogDao).execute(telemetryLog);
    }
    // ... (Keep existing update/delete methods) ...

    // --- Trip Methods ---
    public LiveData<Trip> getLiveOpenTrip() { return currentOpenTrip; }

    public void startNewTrip(Trip trip) {
        new InsertTripAsyncTask(tripDao).execute(trip);
    }

    public void updateTrip(Trip trip) {
        new UpdateTripAsyncTask(tripDao).execute(trip);
    }

    public void deleteTrip(Trip trip) {
        new DeleteTripAsyncTask(tripDao).execute(trip);
    }

    private static class DeleteTripAsyncTask extends AsyncTask<Trip, Void, Void> {
        private TripDao tripDao;

        private DeleteTripAsyncTask(TripDao tripDao) {
            this.tripDao = tripDao;
        }

        @Override
        protected Void doInBackground(Trip... trips) {
            tripDao.delete(trips[0]);
            return null;
        }
    }

    public LiveData<List<Trip>> getAllTrips() {
        return tripDao.getAllTrips();
    }

    // We need a way to get the Trip object synchronously for logic
    // This is a simplified approach; usually you'd use RxJava or Kotlin Coroutines
    public void checkAndManageTrips(TripManagementCallback callback) {
        new CheckTripsAsyncTask(tripDao, callback).execute();
    }

    public interface TripManagementCallback {
        void onCheckComplete(Trip openTrip);
    }

    // --- Async Tasks ---

    private static class InsertTripAsyncTask extends AsyncTask<Trip, Void, Void> {
        private TripDao tripDao;
        private InsertTripAsyncTask(TripDao tripDao) { this.tripDao = tripDao; }
        @Override protected Void doInBackground(Trip... trips) {
            tripDao.insert(trips[0]);
            return null;
        }
    }

    private static class UpdateTripAsyncTask extends AsyncTask<Trip, Void, Void> {
        private TripDao tripDao;
        private UpdateTripAsyncTask(TripDao tripDao) { this.tripDao = tripDao; }
        @Override protected Void doInBackground(Trip... trips) {
            tripDao.update(trips[0]);
            return null;
        }
    }

    private static class CheckTripsAsyncTask extends AsyncTask<Void, Void, Trip> {
        private TripDao tripDao;
        private TripManagementCallback callback;
        private CheckTripsAsyncTask(TripDao tripDao, TripManagementCallback callback) {
            this.tripDao = tripDao;
            this.callback = callback;
        }
        @Override protected Trip doInBackground(Void... voids) {
            return tripDao.getOpenTrip();
        }
        @Override protected void onPostExecute(Trip trip) {
            callback.onCheckComplete(trip);
        }
    }

    // ... (Keep existing Telemetry AsyncTasks) ...
    private static class InsertTelemetryLogAsyncTask extends AsyncTask<TelemetryLog, Void, Void>{
        private TelemetryLogDao telemetryLogDao;
        private InsertTelemetryLogAsyncTask(TelemetryLogDao telemetryLogDao){ this.telemetryLogDao = telemetryLogDao; }
        @Override protected Void doInBackground(TelemetryLog... logs) {
            telemetryLogDao.insert(logs[0]);
            return null;
        }
    }
}
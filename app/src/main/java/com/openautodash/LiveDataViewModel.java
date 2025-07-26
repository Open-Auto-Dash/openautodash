package com.openautodash;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.database.TelemetryLog;
import com.openautodash.database.DatabaseRepository;
import com.openautodash.object.LastKnownCameraPosition;

import java.util.List;

public class LiveDataViewModel extends AndroidViewModel {
    private static final String TAG = "LiveDataViewModel";

    private DatabaseRepository databaseRepository;

    private LiveData<List<TelemetryLog>> telemetryLogs;
    private MutableLiveData<Location> locationData = new MutableLiveData<>();

    private MutableLiveData<LastKnownCameraPosition> lastKnownCameraPosition = new MutableLiveData<>(new LastKnownCameraPosition(17, 60, 0));

    public LiveDataViewModel(Application application) {
        super(application);
        databaseRepository = new DatabaseRepository(application);
        telemetryLogs = databaseRepository.getTelemetryLogs();
    }

    public void insertTelemetryLog(TelemetryLog telemetryLog){
        databaseRepository.insertTelemetryLog(telemetryLog);
    }
    public void updateTelemetryLog(TelemetryLog telemetryLog){

    }
    public void deleteTelemetryLog(TelemetryLog telemetryLog){
        databaseRepository.deleteTelemetryLog(telemetryLog);
    }
    public void deleteAllTelemetryLogs(){
        databaseRepository.deleteAllTelemetryLogs();
    }

    public LiveData<List<TelemetryLog>> getAllTelemetryLogs(){
        return telemetryLogs;
    }

    public LiveData<Location> getLocationData() {
        return locationData;
    }

    public void setLocation(Location location) {
        locationData.setValue(location);
    }

    public LiveData<LastKnownCameraPosition> getLastKnownCameraPosition (){
        return lastKnownCameraPosition;
    }

    public void setLastKnownCameraPosition(LastKnownCameraPosition lastKnownCameraPosition){
        this.lastKnownCameraPosition.setValue(lastKnownCameraPosition);
    }
}

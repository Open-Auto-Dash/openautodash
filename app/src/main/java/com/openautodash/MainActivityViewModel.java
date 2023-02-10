package com.openautodash;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.openautodash.database.TelemetryLog;
import com.openautodash.database.DatabaseRepository;

import java.util.List;

public class MainActivityViewModel extends AndroidViewModel {
    private static final String TAG = "MainActivityViewModel";

    private DatabaseRepository databaseRepository;

    private LiveData<List<TelemetryLog>> telemetryLogs;

    public MainActivityViewModel(@NonNull Application application) {
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
}

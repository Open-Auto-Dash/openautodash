package com.openautodash.database;

import android.app.Application;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import java.util.List;

public class TelemetryLogRepository {

    private TelemetryLogDao telemetryLogDao;
    private LiveData<List<TelemetryLog>> logs;

    public TelemetryLogRepository (Application application){
        TelemetryLogDatabase database = TelemetryLogDatabase.getInstance(application);
        telemetryLogDao = database.telemetryLogDao();
        logs = telemetryLogDao.getAllLogs();
    }

    public void insert(TelemetryLog telemetryLog){
        new InsertTelemetryLogAsyncTask(telemetryLogDao).execute(telemetryLog);
    }
    public void update(TelemetryLog telemetryLog){
        new UpdateTelemetryLogAsyncTask(telemetryLogDao).execute(telemetryLog);
    }
    public void delete(TelemetryLog telemetryLog){
        new DeleteTelemetryLogAsyncTask(telemetryLogDao).execute(telemetryLog);
    }
    public void deleteAll(){
        new DeleteAllTelemetryLogAsyncTask(telemetryLogDao).execute();
    }
    public LiveData<List<TelemetryLog>> getLogs(){
        return logs;
    }

    private static class InsertTelemetryLogAsyncTask extends AsyncTask<TelemetryLog, Void, Void>{
        private TelemetryLogDao telemetryLogDao;

        private InsertTelemetryLogAsyncTask(TelemetryLogDao telemetryLogDao){
            this.telemetryLogDao = telemetryLogDao;
        }

        @Override
        protected Void doInBackground(TelemetryLog... telemetryLogs) {
            telemetryLogDao.insert(telemetryLogs[0]);
            return null;
        }
    }
    private static class UpdateTelemetryLogAsyncTask extends AsyncTask<TelemetryLog, Void, Void>{
        private TelemetryLogDao telemetryLogDao;

        private UpdateTelemetryLogAsyncTask(TelemetryLogDao telemetryLogDao){
            this.telemetryLogDao = telemetryLogDao;
        }

        @Override
        protected Void doInBackground(TelemetryLog... telemetryLogs) {
            telemetryLogDao.update(telemetryLogs[0]);
            return null;
        }
    }

    private static class DeleteTelemetryLogAsyncTask extends AsyncTask<TelemetryLog, Void, Void>{
        private TelemetryLogDao telemetryLogDao;

        private DeleteTelemetryLogAsyncTask(TelemetryLogDao telemetryLogDao){
            this.telemetryLogDao = telemetryLogDao;
        }

        @Override
        protected Void doInBackground(TelemetryLog... telemetryLogs) {
            telemetryLogDao.delete(telemetryLogs[0]);
            return null;
        }
    }



    private static class DeleteAllTelemetryLogAsyncTask extends AsyncTask<Void, Void, Void>{
        private TelemetryLogDao telemetryLogDao;

        private DeleteAllTelemetryLogAsyncTask(TelemetryLogDao telemetryLogDao){
            this.telemetryLogDao = telemetryLogDao;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            telemetryLogDao.deleteAllNotes();
            return null;
        }
    }
}

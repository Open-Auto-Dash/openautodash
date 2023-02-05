package com.openautodash.database;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.loader.content.AsyncTaskLoader;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {TelemetryLog.class}, version = 1)
public abstract class TelemetryLogDatabase  extends RoomDatabase {
    private static TelemetryLogDatabase instance;

    public abstract TelemetryLogDao telemetryLogDao();

    public static synchronized TelemetryLogDatabase getInstance(Context context){
        if(instance == null){
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    TelemetryLogDatabase.class,
                    "telemetry_log_database")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }

    private static RoomDatabase.Callback roomCallback = new RoomDatabase.Callback(){
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
        }
    };

    private static class PopulateDBAsyncClass extends AsyncTask<Void, Void, Void> {
        private TelemetryLogDao telemetryLogDao;

        private PopulateDBAsyncClass(TelemetryLogDatabase database){
            telemetryLogDao = database.telemetryLogDao();
        }
        @Override
        protected Void doInBackground(Void... voids) {
            telemetryLogDao.insert(new TelemetryLog(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,System.currentTimeMillis()));
            return null;
        }
    }
}

package com.openautodash.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TelemetryLogDao {

    @Insert
    void insert(TelemetryLog log);

    @Update
    void update(TelemetryLog log);

    @Delete
    void delete(TelemetryLog log);


    @Query("DELETE  FROM telemetry_log")
    void deleteAllNotes();

    @Query("SELECT * FROM telemetry_log ORDER BY timestamp DESC")
    LiveData<List<TelemetryLog>> getAllLogs();
}

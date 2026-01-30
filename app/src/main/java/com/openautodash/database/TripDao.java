package com.openautodash.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TripDao {
    @Insert
    void insert(Trip trip);

    @Update
    void update(Trip trip);

    @Delete
    void delete(Trip trip);

    @Query("SELECT * FROM trip_table WHERE isOpen = 1 LIMIT 1")
    Trip getOpenTrip(); // Synchronous for background logic

    @Query("SELECT * FROM trip_table WHERE isOpen = 1 LIMIT 1")
    LiveData<Trip> getLiveOpenTrip(); // For UI

    @Query("SELECT * FROM trip_table ORDER BY startTime DESC LIMIT 1")
    Trip getLastTrip();

    // ... inside TripDao interface
    @Query("SELECT * FROM trip_table ORDER BY startTime DESC")
    LiveData<List<Trip>> getAllTrips();
}
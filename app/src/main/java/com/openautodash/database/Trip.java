package com.openautodash.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trip_table")
public class Trip {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private long startTime;
    private long endTime;
    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;
    private float distanceMeters;
    private boolean isBusiness;
    private boolean isOpen; // True if trip is currently active

    public Trip(long startTime, double startLat, double startLng) {
        this.startTime = startTime;
        this.startLat = startLat;
        this.startLng = startLng;
        this.isOpen = true;
        this.distanceMeters = 0;
        this.isBusiness = false; // Default
    }

    // Getters and Setters
    public void setId(int id) { this.id = id; }
    public int getId() { return id; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public double getStartLat() { return startLat; }
    public void setStartLat(double startLat) { this.startLat = startLat; }

    public double getStartLng() { return startLng; }
    public void setStartLng(double startLng) { this.startLng = startLng; }

    public double getEndLat() { return endLat; }
    public void setEndLat(double endLat) { this.endLat = endLat; }

    public double getEndLng() { return endLng; }
    public void setEndLng(double endLng) { this.endLng = endLng; }

    public float getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(float distanceMeters) { this.distanceMeters = distanceMeters; }

    public boolean isBusiness() { return isBusiness; }
    public void setBusiness(boolean business) { isBusiness = business; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }
}
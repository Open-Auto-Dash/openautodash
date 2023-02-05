package com.openautodash.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "telemetry_log")
public class TelemetryLog {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private int tripId;

    private int segment;

    private double lat;

    private double lng;

    private double elevation;

    private float speed;

    private float bearing;

    private float heading;

    private int speedLimit;

    private int roadType;

    private double accelX;

    private double accelY;

    private double accelZ;

    private int vehicleState;

    private int rpm;

    private int batVoltage;

    private int gear;

    private float breakPeddle;

    private float acceleratorPeddle;

    private float steeringAngle;

    private int cruise;

    private int occupants;

    private long timestamp;


    public TelemetryLog(int tripId, int segment, double lat, double lng, double elevation, float speed, float bearing, float heading, int speedLimit, int roadType, double accelX, double accelY, double accelZ, int vehicleState, int rpm, int batVoltage, int gear, float breakPeddle, float acceleratorPeddle, float steeringAngle, int cruise, int occupants, long timestamp) {
        this.tripId = tripId;
        this.segment = segment;
        this.lat = lat;
        this.lng = lng;
        this.elevation = elevation;
        this.speed = speed;
        this.bearing = bearing;
        this.heading = heading;
        this.speedLimit = speedLimit;
        this.roadType = roadType;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.vehicleState = vehicleState;
        this.rpm = rpm;
        this.batVoltage = batVoltage;
        this.gear = gear;
        this.breakPeddle = breakPeddle;
        this.acceleratorPeddle = acceleratorPeddle;
        this.steeringAngle = steeringAngle;
        this.cruise = cruise;
        this.occupants = occupants;
        this.timestamp = timestamp;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public int getTripId() {
        return tripId;
    }

    public int getSegment() {
        return segment;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public double getElevation() {
        return elevation;
    }

    public float getSpeed() {
        return speed;
    }

    public float getBearing() {
        return bearing;
    }

    public float getHeading() {
        return heading;
    }

    public int getSpeedLimit() {
        return speedLimit;
    }

    public int getRoadType() {
        return roadType;
    }

    public double getAccelX() {
        return accelX;
    }

    public double getAccelY() {
        return accelY;
    }

    public double getAccelZ() {
        return accelZ;
    }

    public int getVehicleState() {
        return vehicleState;
    }

    public int getRpm() {
        return rpm;
    }

    public int getBatVoltage() {
        return batVoltage;
    }

    public int getGear() {
        return gear;
    }

    public float getBreakPeddle() {
        return breakPeddle;
    }

    public float getAcceleratorPeddle() {
        return acceleratorPeddle;
    }

    public float getSteeringAngle() {
        return steeringAngle;
    }

    public int getCruise() {
        return cruise;
    }

    public int getOccupants() {
        return occupants;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

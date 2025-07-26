package com.openautodash.object;

public class LastKnownCameraPosition {
    public long zoom;
    public long tilt;
    public float bearing;

    public LastKnownCameraPosition(long zoom, long tilt, float bearing) {
        this.zoom = zoom;
        this.tilt = tilt;
        this.bearing = bearing;
    }
}
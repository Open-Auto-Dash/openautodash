package com.openautodash.object;

public class NavigationRequest {
    public final double lat;
    public final double lng;
    public final String label;
    public final String placeId;

    public NavigationRequest(double lat, double lng, String label, String placeId) {
        this.lat = lat;
        this.lng = lng;
        this.label = label;
        this.placeId = placeId;
    }
}
package com.openautodash.utilities;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.openautodash.LiveDataViewModel;

import java.util.List;

public class LocationListener implements android.location.LocationListener {
    private static final String TAG = "LocationListener";

    private MutableLiveData<Location> locationLiveData;

    public LocationListener(MutableLiveData<Location> locationLiveData) {
        this.locationLiveData = locationLiveData;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        locationLiveData.postValue(location);
    }

    @Override
    public void onLocationChanged(@NonNull List<Location> locations) {
        android.location.LocationListener.super.onLocationChanged(locations);
    }

    @Override
    public void onFlushComplete(int requestCode) {
        android.location.LocationListener.super.onFlushComplete(requestCode);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        android.location.LocationListener.super.onProviderEnabled(provider);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        android.location.LocationListener.super.onProviderDisabled(provider);
        // Make a fuss if location is not turned on.

    }
}

package com.openautodash;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.libraries.navigation.Waypoint;

public class MapViewModel extends ViewModel {

    // 1. STATE: Is the Search Bar visible or hidden?
    private final MutableLiveData<Boolean> isNavigating = new MutableLiveData<>(false);

    // 2. DATA: The text to show in the header (Time, Dist, ETA)
    private final MutableLiveData<NavStats> navStats = new MutableLiveData<>();

    // 3. COMMANDS: Tell the Map to Start/Stop
    private final MutableLiveData<Waypoint> startNavigationCommand = new MutableLiveData<>();
    private final MutableLiveData<Boolean> stopNavigationCommand = new MutableLiveData<>();

    // --- Getters for the Fragments to Observe ---
    public LiveData<Boolean> getIsNavigating() { return isNavigating; }
    public LiveData<NavStats> getNavStats() { return navStats; }
    public LiveData<Waypoint> getStartNavigationCommand() { return startNavigationCommand; }
    public LiveData<Boolean> getStopNavigationCommand() { return stopNavigationCommand; }

    // --- Actions (Called by Overlay Fragment) ---
    public void requestStartNavigation(Waypoint destination) {
        // This IMMEDIATELY triggers the Observer in OverlayFragment to hide the Search Bar
        isNavigating.setValue(true);
        startNavigationCommand.setValue(destination);
    }

    public void requestStopNavigation() {
        // This IMMEDIATELY triggers the Observer in OverlayFragment to show the Search Bar
        isNavigating.setValue(false);
        stopNavigationCommand.setValue(true);
    }

    // --- Updates (Called by Map Fragment) ---
    public void updateNavStats(String time, String distance, String eta) {
        navStats.postValue(new NavStats(time, distance, eta));
    }

    // Simple data holder
    public static class NavStats {
        public final String time, distance, eta;
        public NavStats(String t, String d, String e) { this.time = t; this.distance = d; this.eta = e; }
    }
}
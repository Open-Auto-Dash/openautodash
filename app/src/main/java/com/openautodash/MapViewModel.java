package com.openautodash;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.libraries.navigation.Navigator;
import com.google.android.libraries.navigation.Waypoint;
import com.openautodash.utilities.LocalSettings;

// Changed to AndroidViewModel to get Context
public class MapViewModel extends AndroidViewModel {

    private final LocalSettings localSettings;

    // --- STATE ---
    private final MutableLiveData<Boolean> isNavigating = new MutableLiveData<>(false);
    private final MutableLiveData<NavStats> navStats = new MutableLiveData<>();
    private final MutableLiveData<Waypoint> startNavigationCommand = new MutableLiveData<>();
    private final MutableLiveData<Boolean> stopNavigationCommand = new MutableLiveData<>();

    // --- MAP CONTROLS (Initialized from Settings) ---
    private final MutableLiveData<Boolean> isTrafficEnabled;
    private final MutableLiveData<Boolean> isSatelliteEnabled;
    private final MutableLiveData<Integer> audioGuidanceState;

    public MapViewModel(@NonNull Application application) {
        super(application);
        localSettings = new LocalSettings(application);

        // Load Saved Preferences on Launch
        isTrafficEnabled = new MutableLiveData<>(localSettings.getTrafficEnabled());
        isSatelliteEnabled = new MutableLiveData<>(localSettings.getSatelliteEnabled());
        audioGuidanceState = new MutableLiveData<>(localSettings.getAudioGuidanceState());
    }

    // --- Getters ---
    public LiveData<Boolean> getIsNavigating() { return isNavigating; }
    public LiveData<NavStats> getNavStats() { return navStats; }
    public LiveData<Waypoint> getStartNavigationCommand() { return startNavigationCommand; }
    public LiveData<Boolean> getStopNavigationCommand() { return stopNavigationCommand; }

    public LiveData<Boolean> getIsTrafficEnabled() { return isTrafficEnabled; }
    public LiveData<Boolean> getIsSatelliteEnabled() { return isSatelliteEnabled; }
    public LiveData<Integer> getAudioGuidanceState() { return audioGuidanceState; }

    // --- Navigation Actions ---
    public void requestStartNavigation(Waypoint destination) {
        isNavigating.setValue(true);
        startNavigationCommand.setValue(destination);
    }

    public void requestStopNavigation() {
        isNavigating.setValue(false);
        stopNavigationCommand.setValue(true);
    }

    // --- Map Control Actions (Now Saves to Settings) ---
    public void toggleTraffic() {
        boolean newState = !Boolean.TRUE.equals(isTrafficEnabled.getValue());
        isTrafficEnabled.setValue(newState);
        localSettings.setTrafficEnabled(newState); // SAVE
    }

    public void toggleSatellite() {
        boolean newState = !Boolean.TRUE.equals(isSatelliteEnabled.getValue());
        isSatelliteEnabled.setValue(newState);
        localSettings.setSatelliteEnabled(newState); // SAVE
    }

    public void setAudioGuidance(int state) {
        audioGuidanceState.setValue(state);
        localSettings.setAudioGuidanceState(state); // SAVE
    }

    // --- Updates ---
    public void updateNavStats(String time, String distance, String eta) {
        navStats.postValue(new NavStats(time, distance, eta));
    }

    public static class NavStats {
        public final String time, distance, eta;
        public NavStats(String t, String d, String e) { this.time = t; this.distance = d; this.eta = e; }
    }
}
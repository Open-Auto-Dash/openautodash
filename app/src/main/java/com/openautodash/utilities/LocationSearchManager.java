// LocationSearchManager.java (in .utilities package)
package com.openautodash.utilities;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.openautodash.R;
import com.openautodash.object.PlaceSearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationSearchManager {
    private static final String TAG = "LocationSearchManager";
    private final PlacesClient placesClient;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface LocationSearchCallback {
        void onSearchResults(List<PlaceSearchResult> results);
        void onPlaceSelected(Place place);
        void onError(String message);
    }

    public LocationSearchManager(Context context) {
        if (!Places.isInitialized()) {
            Places.initialize(context, context.getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(context);
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void searchPlaces(String query, LocationSearchCallback callback) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    List<PlaceSearchResult> searchResults = new ArrayList<>();
                    for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                        searchResults.add(new PlaceSearchResult(
                                prediction.getPlaceId(),
                                prediction.getPrimaryText(null).toString(),
                                prediction.getSecondaryText(null).toString()
                        ));
                    }
                    mainHandler.post(() -> callback.onSearchResults(searchResults));
                })
                .addOnFailureListener(exception -> {
                    mainHandler.post(() -> callback.onError("Place search failed: " + exception.getMessage()));
                });
    }

    public void getPlaceDetails(String placeId, LocationSearchCallback callback) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.TYPES
        );

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields).build();

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    mainHandler.post(() -> callback.onPlaceSelected(place));
                })
                .addOnFailureListener(exception -> {
                    mainHandler.post(() -> callback.onError("Place details failed: " + exception.getMessage()));
                });
    }
}

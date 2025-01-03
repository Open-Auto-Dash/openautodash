// RouteManager.java (in .utilities package)
package com.openautodash.utilities;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;

import java.util.ArrayList;
import java.util.List;

public class RouteManager {
    private static final String TAG = "RouteManager";
    private final GeoApiContext geoApiContext;
    private GoogleMap map;
    private List<com.google.android.gms.maps.model.Polyline> activeRoutes = new ArrayList<>();

    public interface RouteCallback {
        void onRouteFound(DirectionsResult result);
        void onRouteError(String error);
    }

    public RouteManager(String apiKey, GoogleMap map) {
        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
        this.map = map;
    }

    public void requestRoute(LatLng origin, LatLng destination, RouteCallback callback) {
        new AsyncTask<Void, Void, DirectionsResult>() {
            @Override
            protected DirectionsResult doInBackground(Void... voids) {
                try {
                    return DirectionsApi.newRequest(geoApiContext)
                            .mode(TravelMode.DRIVING)
                            .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                            .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                            .await();
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching directions: " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(DirectionsResult result) {
                if (result != null && result.routes.length > 0) {
                    drawRoute(result.routes[0]);
                    callback.onRouteFound(result);
                } else {
                    callback.onRouteError("Could not find route");
                }
            }
        }.execute();
    }

    public void drawRoute(DirectionsRoute route) {
        // Clear any existing routes
        clearRoutes();

        List<LatLng> points = decodePolyline(route);

        // Draw the main route line
        PolylineOptions routeOptions = new PolylineOptions()
                .addAll(points)
                .color(Color.BLUE)
                .width(12)
                .geodesic(true);

        // Add a white border to make the route stand out
        PolylineOptions borderOptions = new PolylineOptions()
                .addAll(points)
                .color(Color.WHITE)
                .width(14)
                .geodesic(true);

        // Add the lines to the map and store them
        activeRoutes.add(map.addPolyline(borderOptions));
        activeRoutes.add(map.addPolyline(routeOptions));
    }

    private List<LatLng> decodePolyline(DirectionsRoute route) {
        List<LatLng> decodedPath = new ArrayList<>();

        // Get the encoded points from the overview polyline
        String encodedPoints = route.overviewPolyline.getEncodedPath();

        // Decode the points using the Google Maps Utility Library
        List<com.google.maps.model.LatLng> coords =
                com.google.maps.internal.PolylineEncoding.decode(encodedPoints);

        // Convert to the Android Maps LatLng objects
        for (com.google.maps.model.LatLng coord : coords) {
            decodedPath.add(new LatLng(coord.lat, coord.lng));
        }

        return decodedPath;
    }

    public void clearRoutes() {
        for (com.google.android.gms.maps.model.Polyline route : activeRoutes) {
            route.remove();
        }
        activeRoutes.clear();
    }

    public void cleanup() {
        clearRoutes();
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
    }
}
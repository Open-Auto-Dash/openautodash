package com.openautodash.utilities;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.maps.GeoApiContext;
import com.google.maps.RoadsApi;
import com.google.maps.model.SnappedSpeedLimitResult;
import com.google.maps.model.SpeedLimit;
import com.google.maps.model.LatLng;
import com.openautodash.R;
import com.openautodash.interfaces.SpeedLimitCallback;

public class SpeedLimitAPI {
    private static final String TAG = "SpeedLimitAPI";
    private static final double APPROACHING_SEGMENT_END_THRESHOLD = 100; // meters

    private Context context;
    private GeoApiContext geoApiContext;
    private SpeedLimitCallback callback;

    // Cache for current road segment
    private String currentPlaceId;
    private SpeedLimit currentSpeedLimit;
    private LatLng segmentStart;
    private LatLng segmentEnd;
    private double segmentLengthMeters;

    public SpeedLimitAPI(Context context, SpeedLimitCallback callback) {
        this.context = context;
        this.callback = callback;

        geoApiContext = new GeoApiContext.Builder()
                .apiKey(context.getString(R.string.google_maps_key))
                .build();
    }

    public void checkSpeedLimit(Location location) {
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        // If we have cached data, check if we're still on the same road segment
        if (currentSpeedLimit != null) {
            // Calculate distance to end of segment
            float[] results = new float[1];
            Location.distanceBetween(
                    location.getLatitude(), location.getLongitude(),
                    segmentEnd.lat, segmentEnd.lng,
                    results
            );

            double distanceToSegmentEnd = results[0];

            // If we're not approaching the end of the segment, use cached speed limit
            if (distanceToSegmentEnd > APPROACHING_SEGMENT_END_THRESHOLD) {
                callback.onSpeedLimitUpdated(String.valueOf(currentSpeedLimit.speedLimit));
                return;
            }
        }

        // If we're here, either we have no cached data or we're approaching segment end
        // Time to fetch new speed limit data
        fetchSpeedLimit(currentLatLng);
    }

    private void fetchSpeedLimit(LatLng latLng) {
        try {
            SnappedSpeedLimitResult result = RoadsApi.speedLimits(geoApiContext, latLng).await();

            if (result != null && result.speedLimits.length > 0) {
                // Update cached data
                currentSpeedLimit = result.speedLimits[0];
                currentPlaceId = result.snappedPoints[0].placeId;

                // Get the road segment coordinates
                segmentStart = result.snappedPoints[0].location;
                // If there's more than one point, use it as segment end
                segmentEnd = result.snappedPoints.length > 1 ?
                        result.snappedPoints[1].location :
                        result.snappedPoints[0].location;

                // Calculate segment length
                float[] results = new float[1];
                Location.distanceBetween(
                        segmentStart.lat, segmentStart.lng,
                        segmentEnd.lat, segmentEnd.lng,
                        results
                );
                segmentLengthMeters = results[0];

                // Notify callback with speed limit
                callback.onSpeedLimitUpdated(String.valueOf(currentSpeedLimit.speedLimit));

                Log.d(TAG, String.format("New road segment: ID=%s, Length=%.2fm, Speed=%d",
                        currentPlaceId, segmentLengthMeters, currentSpeedLimit.speedLimit));
            } else {
                callback.onError("Speed limit not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching speed limit: " + e.getMessage());
            callback.onError(e.getMessage());
        }
    }

    public void cleanup() {
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
    }
}
package com.openautodash.utilities;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.openautodash.R;
import com.openautodash.repositorys.VehicleRepository;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class LiveTrackingManager {
    private static final String TAG = "LiveTrackingManager";

    private final Context context;

    public LiveTrackingManager(Context context) {
        this.context = context;
    }

    public void updateSpotify(VehicleRepository.SpotifyTrack spotifyTrack) {
        try {
            // Prepare Parameters
            Map<String, String> arguments = new HashMap<>();
            arguments.put("track_title", spotifyTrack.title);
            arguments.put("track_artist", spotifyTrack.artist);
            arguments.put("track_progress", String.valueOf(spotifyTrack.progress));

            if(spotifyTrack.artUrl != null){
                arguments.put("track_art", spotifyTrack.artUrl);
            }

            // Build POST Body
            StringJoiner sj = new StringJoiner("&");
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                        + URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
            uploadData(out);

        } catch (Exception e) {
            Log.e(TAG, "Construct upload body for spotify upload failed", e);
        }
    }

    public void updateLocation(Location location, VehicleRepository.VehicleTelemetry vehicleTelemetry) {
        try {
            // Prepare Parameters
            Map<String, String> arguments = new HashMap<>();
            arguments.put("speed", String.valueOf(location.getSpeed() * 3.6f));
            arguments.put("lat", String.valueOf(location.getLatitude()));
            arguments.put("lng", String.valueOf(location.getLongitude()));
            arguments.put("intervals", "1"); // Assuming interval is 1

            // Build POST Body
            StringJoiner sj = new StringJoiner("&");
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                        + URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
            uploadData(out);

        } catch (Exception e) {
            Log.e(TAG, "Construct upload body for location update failed", e);
        }
    }


    private void uploadData(byte[] out) {
        new Thread(() -> {
            try {
                URL url = new URL(context.getString(R.string.live_tracking_url));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                // Send
                conn.setFixedLengthStreamingMode(out.length);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.connect();
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }

                // Read Response (Optional, just to ensure request completes)
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Cloud Update: " + responseCode);
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Cloud Upload Failed", e);
            }
        }).start();
    }
}

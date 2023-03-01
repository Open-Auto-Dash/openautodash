package com.openautodash.utilities;

import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.openautodash.interfaces.OverpassAPICallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OverpassAPI extends AsyncTask <Void, Void, String> {
    private static final String TAG = "OverpassAPI";

    private Context context;
    private Location location;
    private OverpassAPICallback callback;

    public OverpassAPI(Context context, Location location, OverpassAPICallback callback) {
        this.context = context;
        this.location = location;
        this.callback = callback;
    }

    public void getSpeedLimit(){
        execute();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            OkHttpClient client = new OkHttpClient();

            String queryUrl = "https://overpass-api.de/api/interpreter?data=[out:json][timeout:25];way(around:20," +
                    location.getLatitude() + "," + location.getLongitude() + ")[maxspeed];out;";

            Request request = new Request.Builder()
                    .url(queryUrl)
                    .build();

            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(String response) {
        if (response != null) {
            try {
                JSONObject responseJson = new JSONObject(response);
                JSONArray elements = responseJson.getJSONArray("elements");

                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);

                    if (element.has("tags")) {
                        JSONObject tags = element.getJSONObject("tags");
                        if (tags.has("maxspeed")) {
                            String maxSpeed = tags.getString("maxspeed");
                            Log.d(TAG, "onPostExecute: Max speed: " + maxSpeed);
                            callback.speedLimitUpdated(maxSpeed);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            // Handle any network or API errors
        }
    }
}

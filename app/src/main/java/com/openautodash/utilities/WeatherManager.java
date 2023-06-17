package com.openautodash.utilities;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.openautodash.R;
import com.openautodash.enums.Units;
import com.openautodash.interfaces.WeatherUpdateCallback;
import com.openautodash.object.Weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Handler;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherManager {
    private static final String TAG = "WeatherManager";

    private final LocalSettings localSettings;
    private WeatherUpdateCallback callback;

    private final Context context;
    private Location location;
    private Weather weather;
    OkHttpClient client = new OkHttpClient();

    public WeatherManager(Context context, Location location, WeatherUpdateCallback callback) {
        this.context = context;
        this.location = location;
        this.callback = callback;
        weather = new Weather();
        localSettings = new LocalSettings(context);
        localSettings.setWeatherUnits(Units.Metric);
    }

    public void getCurrentWeather(Location location) {
        this.location = location;
        this.callback = callback;
        if (shouldRenewData()) {
            updateWeatherData();
        } else {
            callback.onComplete(weather);
        }
    }

    public Weather syncWeather() {
        return weather;
    }

    private boolean shouldRenewData() {
        if (weather.getDate() != null) {
            Log.d(TAG, "shouldRenewData: Date: " + weather.getDate().getYear());
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - weather.getDate().getTime();
            long fifteenMinutesInMillis = 15 * 60 * 1000; // 15 minutes in milliseconds

            if (elapsedTime >= fifteenMinutesInMillis) {
                Log.d(TAG, "shouldRenewData: Over 15 minutes");
                return true; // Data is older than 15 minutes, renew it
            }

            if (location != null && weather.getLocation() != null) {
                float distance = location.distanceTo(weather.getLocation());
                float fifteenKmInMeters = 15000; // 15 kilometers in meters

                if (distance >= fifteenKmInMeters) {
                    Log.d(TAG, "shouldRenewData: over 15 KM");
                    return true; // Distance is greater than or equal to 15 km, renew the data
                }
            }
        } else {
            Log.d(TAG, "shouldRenewData: data is null");
            return true;
        }

        return false; // Data is up-to-date, no need to renew
    }

    public void updateWeatherData() {
        Thread myThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Units units = localSettings.getWeatherUnits();
                String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + location.getLatitude() + "&lon=" + location.getLongitude() + "&units=" + units.toString() + "&appid=" + context.getString(R.string.open_weather_map_key);
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                try {
                    Response response = client.newCall(request).execute();
                    String weatherData = response.body().string();
                    // Parse the JSON data here
                    try {
                        JSONObject jsonObject = new JSONObject(weatherData);

                        // Parse the individual fields from the JSON object
                        weather.setDate(new Date(System.currentTimeMillis()));
                        weather.setLocation(location);

                        JSONArray weatherArray = jsonObject.getJSONArray("weather");
                        JSONObject weatherObj = weatherArray.getJSONObject(0);
                        weather.setDescription(weatherObj.getString("description"));

                        JSONObject main = jsonObject.getJSONObject("main");
                        weather.setTemp(main.getInt("temp"));
                        weather.setFeelsLike(main.getInt("feels_like"));
                        weather.setTempMin(main.getInt("temp_min"));
                        weather.setTempMax(main.getInt("temp_max"));
                        weather.setPressure(main.getInt("pressure"));
                        weather.setHumidity(main.getInt("humidity"));

                        JSONObject wind = jsonObject.getJSONObject("wind");
                        weather.setWindSpeed(wind.getInt("speed"));
                        weather.setWindDeg(wind.getInt("deg"));
                        weather.setWindGust(wind.getInt("gust"));

                        float relativeAngle = (float) weather.getWindDeg() - weather.getLocation().getBearing();
                        if (relativeAngle < 0) {
                            relativeAngle += 360;
                        } else if (relativeAngle > 360) {
                            relativeAngle -= 360;
                        }
                        weather.setWindDegRel((int) relativeAngle);

                        Log.d(TAG, "updateWeatherData: Temp is" + weather.getTemp());
                        Log.d(TAG, "updateWeatherData: Location is" + weather.getLocation().getLatitude() + ":" + weather.getLocation().getLatitude());
                        // Parse other fields as needed

                        callback.onComplete(weather);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "updateWeatherData: Error", e);
                    }

                } catch (
                        IOException e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Can't get weather", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Start the thread
        myThread.start();
    }

    private Location getDefaultLocation() {
        // Create and return a default Location object
        Location defaultLocation = new Location("Default");
        defaultLocation.setLatitude(43);
        defaultLocation.setLongitude(-80);
        return defaultLocation;
    }
}

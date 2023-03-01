package com.openautodash.utilities;

import android.content.Context;
import android.location.Location;

import com.openautodash.R;
import com.openautodash.enums.Units;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Weather {
    private Context context;
    OkHttpClient client = new OkHttpClient();

    public Weather(Context context) {
        this.context = context;
    }

    public String getCurrentTemp(Location location, Units unites){
        String jsonData = getCurrentConditions(location, unites);
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONObject main = jsonObject.getJSONObject("main");
            double temp = main.getDouble("temp");
            String stringUnits = "°";
            switch (unites){
                case Standard:
                    stringUnits = "°K";
                    break;
                case Metric:
                    stringUnits = "°C";
                    break;
                case Imperial:
                    stringUnits = "°F";
                    break;
            }
            return (int)temp + stringUnits;
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getCurrentConditions(Location location, Units units){
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + location.getLatitude() + "&lon=" + location.getLongitude() + "&units=" + units.toString() + "&appid=" + context.getString(R.string.open_weather_map_key);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
            // Parse the JSON data here
        } catch (
                IOException e) {
            e.printStackTrace();
            return "404";
        }
    }
}

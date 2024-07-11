package com.openautodash.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.openautodash.R;
import com.openautodash.enums.Units;
import com.openautodash.object.PhoneKey;

import java.util.Arrays;
import java.util.List;

public class LocalSettings {
    private static final String TAG = "LocalSettings";

    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;


    public LocalSettings(Context context) {
        preferences = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    public void setVehicleId(String vehicleId){
        editor.putString("vehicle_id", vehicleId);
    }
    public String getVehicleId(){
        return preferences.getString("vehicle_id", "100000001");
    }

    public void setPhoneKey(PhoneKey phoneKey){
        List<PhoneKey> phoneKeys = getPhoneKeys();
        phoneKeys.add(phoneKey);
        Gson gson = new Gson();
        editor.putString("phone_keys", gson.toJson(phoneKeys, PhoneKey.class));
    }

    public List<PhoneKey> getPhoneKeys(){
        Gson gson = new Gson();
        String keysString = preferences.getString("phone_keys", null);
        if(keysString != null){
            return Arrays.asList(gson.fromJson(keysString, PhoneKey.class));
        }
        return null;
    }

    public void isNight(boolean night){
        editor.putBoolean("isNight", night).commit();
    }

    public boolean getIsNight(){
        return preferences.getBoolean("isNight", false);
    }

    public void setNightModeSetPoint(int value){
        editor.putInt("nightModeSetPoint", value).commit();
    }

    public int getNightModeSetPoint(){
        return preferences.getInt("nightModeSetPoint", 0);
    }

    public void setBrightnessSetting(int[] values){
        String setting = Arrays.toString(values);
        editor.putString("brightnessSetting", setting).commit();
    }

    public int[] getBrightnessSetting(){
        String setting = preferences.getString("brightnessSetting", "[0, 0, 0, 0, 0, 0]");

        setting = setting.substring(1, setting.length() - 1);
        String[] stringArray = setting.split(", ");

        // Step 3: Convert each string in the array back into an integer
        int[] intArray = new int[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            intArray[i] = Integer.parseInt(stringArray[i]);
        }
        return intArray;
    }

    public void setSpeedUnits(Units units){
        editor.putString("speedUnits", String.valueOf(units)).commit();
    }

    public double getSpeedUnits(){
        return Double.parseDouble(preferences.getString("weatherUnits", "3.6"));
    }

    public void setWeatherUnits(Units units){
        editor.putString("speedUnits", String.valueOf(units)).commit();
    }

    public Units getWeatherUnits(){
        return Units.valueOf(preferences.getString("weatherUnits", "Metric"));
    }
}

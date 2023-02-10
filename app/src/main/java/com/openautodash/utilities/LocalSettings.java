package com.openautodash.utilities;

import android.content.Context;
import android.content.SharedPreferences;

import com.openautodash.R;

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
        return preferences.getString("vehicle_id", "0000");
    }
}

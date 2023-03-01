package com.openautodash.utilities;

import android.content.Context;
import android.content.SharedPreferences;

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
        return preferences.getString("vehicle_id", "0000");
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

    public void setSpeedUnits(Units units){
        editor.putString("speedUnits", String.valueOf(units));
    }

    public double getSpeedUnits(){
        return Double.parseDouble(preferences.getString("speedUnits", "3.6"));
    }
}

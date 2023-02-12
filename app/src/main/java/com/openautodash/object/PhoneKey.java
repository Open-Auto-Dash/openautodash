package com.openautodash.object;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.stream.Stream;

public class PhoneKey {
    int id;
    String name;
    String deviceName;
    String deviceSerialNumber;
    String bluetoothName;
    String bluetoothMac;
    String bluetoothUUID;

    public String getBluetoothMac(){
        return bluetoothMac;
    }


    public static JSONObject Serialize(PhoneKey key) {

        Gson gson = new Gson();
        String jsonString = gson.toJson(key);

        try {

            JSONObject json = new JSONObject(jsonString);
            return json;

        } catch (JSONException e) {

            e.printStackTrace();

        }

        return null;
    }
}

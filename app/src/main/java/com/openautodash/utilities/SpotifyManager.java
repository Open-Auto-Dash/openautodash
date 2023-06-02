package com.openautodash.utilities;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spotify.android.appremote.api.SpotifyAppRemote;

public class SpotifyManager {
    private Context context;

    private static final String CLIENT_ID = "13bbd41117ca417980d841742d5b4ad2";
    private static final String REDIRECT_URI = "comopenautodash://callback";

    private static SpotifyAppRemote mSpotifyAppRemote;
    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SpotifyManager(Context context){
        this.context = context;

        SpotifyAppRemote.setDebugMode(true);
    }
}

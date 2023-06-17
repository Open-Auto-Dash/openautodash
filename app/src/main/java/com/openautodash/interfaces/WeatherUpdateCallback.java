package com.openautodash.interfaces;

import com.openautodash.object.Weather;

public interface WeatherUpdateCallback {
    void onComplete(Weather weather);
}

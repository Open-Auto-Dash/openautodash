package com.openautodash.object;

import android.location.Location;

import com.openautodash.enums.Units;

import java.util.Date;

import kotlin.Unit;

public class Weather {
    private Units units;
    private String country;
    private Date date;
    private Location location;
    private String name;
    private String description;
    private int temp;
    private int feelsLike;
    private int tempMin;
    private int tempMax;
    private int dewPoint;
    private int pressure;
    private int humidity;
    private int uvi;
    private int cloudCover;
    private int seaLevel;
    private int groundLevel;
    private int visibility;
    private int windSpeed;
    private int windDeg;
    private int windDegRel;
    private int windGust;
    private int rainProb;
    private int snowProb;
    private int precipAmount;
    private long sunrise;
    private long sunset;
    private int timeZone;
    private String timeZoneName;
    private String[] alerts;

    public Weather(){

    }

    public Units getUnits() {
        return units;
    }

    public void setUnits(Units units) {
        this.units = units;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTemp() {
        return temp;
    }

    public void setTemp(int temp) {
        this.temp = temp;
    }

    public int getFeelsLike() {
        return feelsLike;
    }

    public void setFeelsLike(int feelsLike) {
        this.feelsLike = feelsLike;
    }

    public int getTempMin() {
        return tempMin;
    }

    public void setTempMin(int tempMin) {
        this.tempMin = tempMin;
    }

    public int getTempMax() {
        return tempMax;
    }

    public void setTempMax(int tempMax) {
        this.tempMax = tempMax;
    }

    public int getDewPoint() {
        return dewPoint;
    }

    public void setDewPoint(int dewPoint) {
        this.dewPoint = dewPoint;
    }

    public int getPressure() {
        return pressure;
    }

    public void setPressure(int pressure) {
        this.pressure = pressure;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public int getUvi() {
        return uvi;
    }

    public void setUvi(int uvi) {
        this.uvi = uvi;
    }

    public int getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(int cloudCover) {
        this.cloudCover = cloudCover;
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    public void setSeaLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public int getGroundLevel() {
        return groundLevel;
    }

    public void setGroundLevel(int groundLevel) {
        this.groundLevel = groundLevel;
    }

    public int getVisibility() {
        return visibility;
    }

    public void setVisibility(int visibility) {
        this.visibility = visibility;
    }

    public int getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(int windSpeed) {
        this.windSpeed = windSpeed;
    }

    public int getWindDeg() {
        return windDeg;
    }

    public void setWindDeg(int windDeg) {
        this.windDeg = windDeg;
    }

    public int getWindDegRel() {
        return windDegRel;
    }

    public void setWindDegRel(int windDegRel) {
        this.windDegRel = windDegRel;
    }

    public int getWindGust() {
        return windGust;
    }

    public void setWindGust(int windGust) {
        this.windGust = windGust;
    }

    public int getRainProb() {
        return rainProb;
    }

    public void setRainProb(int rainProb) {
        this.rainProb = rainProb;
    }

    public int getSnowProb() {
        return snowProb;
    }

    public void setSnowProb(int snowProb) {
        this.snowProb = snowProb;
    }

    public int getPrecipAmount() {
        return precipAmount;
    }

    public void setPrecipAmount(int precipAmount) {
        this.precipAmount = precipAmount;
    }

    public long getSunrise() {
        return sunrise;
    }

    public void setSunrise(long sunrise) {
        this.sunrise = sunrise;
    }

    public long getSunset() {
        return sunset;
    }

    public void setSunset(long sunset) {
        this.sunset = sunset;
    }

    public int getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(int timeZone) {
        this.timeZone = timeZone;
    }

    public String getTimeZoneName() {
        return timeZoneName;
    }

    public void setTimeZoneName(String timeZoneName) {
        this.timeZoneName = timeZoneName;
    }

    public String[] getAlerts() {
        return alerts;
    }

    public void setAlerts(String[] alerts) {
        this.alerts = alerts;
    }
}

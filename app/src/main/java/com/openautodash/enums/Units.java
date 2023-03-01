package com.openautodash.enums;

import com.google.gson.annotations.SerializedName;

public enum Units {
    @SerializedName("standard")
    Standard (0),

    @SerializedName("metric")
    Metric (1),

    @SerializedName("imperial")
    Imperial (2),

    @SerializedName("km/h")
    kmh (3.6),

    @SerializedName("mp/h")
    mph (2.23694);




    private final double value;

    double getValue(){
        return value;
    }
    Units(double value) {
        this.value = value;
    }
}

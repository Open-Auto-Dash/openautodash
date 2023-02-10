package com.openautodash.enums;

import com.google.gson.annotations.SerializedName;

public enum VehicleState {
    @SerializedName("0")
    Dead (0),

    @SerializedName("1")
    Sleep(1),

    @SerializedName("2")
    Idle(2),

    @SerializedName("3")
    Powered(3),

    @SerializedName("4")
    Running(4),

    @SerializedName("5")
    Driving(5);


    private final int value;

    int getValue(){
        return value;
    }
    VehicleState(int value) {
        this.value = value;
    }
}

package com.openautodash.enums;

import com.google.gson.annotations.SerializedName;

public enum Importance {

    @SerializedName("0")
    Silent (0),

    @SerializedName("1")
    Muted(1),

    @SerializedName("2")
    Low(2),

    @SerializedName("3")
    Default(3),

    @SerializedName("4")
    High(4),

    @SerializedName("5")
    Max(5),

    @SerializedName("6")
    Catastrophe(6);

    private final int value;

    int getValue(){
        return value;
    }

    Importance(int value){
        this.value = value;
    }
}

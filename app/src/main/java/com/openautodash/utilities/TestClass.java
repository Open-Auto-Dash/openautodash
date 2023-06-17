package com.openautodash.utilities;

import org.json.JSONObject;

// Just a random spot where you can test out Java code

public class TestClass {
    public static void main(String[] args) {
        // WindDirection - location.getBearing()
        float relativeAngle = 180 - 90;
        if (relativeAngle < 0) {
            relativeAngle += 360;
        } else if (relativeAngle > 360) {
            relativeAngle -= 360;
        }
        System.out.println(relativeAngle);
    }
}

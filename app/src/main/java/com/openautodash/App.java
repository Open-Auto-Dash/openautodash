package com.openautodash;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class App extends Application {
    private static final String TAG = "App";

    public static final String ForegroundService = "foreground_service";
    public static final String Navigation = "navigation";
    public static final String Security = "security";
    public static final String VehicleInformation = "vehicle_information";
    public static final String Media = "media";
    public static final String OpenPilot = "open_pilot";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannels();
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);

        NotificationChannel foregroundService = new NotificationChannel(
                ForegroundService,
                "Foreground Service",
                NotificationManager.IMPORTANCE_NONE
        );
        foregroundService.setDescription("Required notification to keep things running when device asleep");

        NotificationChannel navigation = new NotificationChannel(
                Navigation,
                "Navigation",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        navigation.setDescription("Turn by turn navigation notifications");

        NotificationChannel security = new NotificationChannel(
                Security,
                "Security",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        security.setDescription("Security notifications");

        NotificationChannel vehicleInformation = new NotificationChannel(
                VehicleInformation,
                "Vehicle Information",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        vehicleInformation.setDescription("Engine, battery, gas and other vehicle telemetry");

        NotificationChannel media = new NotificationChannel(
                Media,
                "Media",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        media.setDescription("Media, music, and other sounds");

        NotificationChannel openPilot = new NotificationChannel(
                OpenPilot,
                "OpenPilot",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        openPilot.setDescription("OpenPilot integration notifications");

        //Create notification channels.
        manager.createNotificationChannel(foregroundService);
        manager.createNotificationChannel(navigation);
        manager.createNotificationChannel(security);
        manager.createNotificationChannel(vehicleInformation);
        manager.createNotificationChannel(media);
        manager.createNotificationChannel(openPilot);
    }
}

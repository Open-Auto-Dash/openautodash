package com.openautodash;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.datatransport.runtime.firebase.transport.LogEventDropped;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class PushNotificationService extends FirebaseMessagingService {
    private static final String TAG = "PushNotificationService";
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Log.d(TAG, "onMessageReceived: " + message.getNotification().getTitle());
        super.onMessageReceived(message);
    }
}

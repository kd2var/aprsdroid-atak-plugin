package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AprsMessageManager {

    private static final String TAG = "APRSMSG";

    private final Context context;

    public AprsMessageManager(Context context) {
        this.context = context;
    }

    public boolean isAprsMessageIntent(Intent intent) {
        if (intent == null || intent.getAction() == null)
            return false;

        return "org.aprsdroid.app.MESSAGE".equals(intent.getAction())
                || "org.aprsdroid.app.MESSAGETX".equals(intent.getAction());
    }

    public void handleIncomingIntent(Intent intent) {
        String action = intent.getAction();

        String source = intent.getStringExtra("source");
        String dest = intent.getStringExtra("dest");
        String body = intent.getStringExtra("body");

        Log.d(TAG, "APRS MESSAGE action=" + action
                + " source=[" + source + "]"
                + " dest=[" + dest + "]"
                + " body=[" + body + "]");
    }

    public void sendAprsMessage(String destinationCallsign, String message) {
        if (destinationCallsign == null || destinationCallsign.trim().isEmpty())
            return;

        if (message == null || message.trim().isEmpty())
            return;

        String dest = destinationCallsign.trim().toUpperCase();

        if (dest.length() > 9)
            dest = dest.substring(0, 9);

        String paddedDest = String.format("%-9s", dest);

        String payload = ":" + paddedDest + ":" + message.trim();

        Intent i = new Intent("org.aprsdroid.app.SEND_PACKET");
        i.setPackage("org.aprsdroid.app");
        i.putExtra("data", payload);

        context.startService(i);

        Log.d(TAG, "Sent APRS message payload=[" + payload + "]");
    }
}

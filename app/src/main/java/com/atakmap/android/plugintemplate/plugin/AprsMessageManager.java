package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AprsMessageManager {

    private AprsGeoChatBridge geoChatBridge;

    private static final String TAG = "APRSMSG";

    private final Context context;

    private String localCallsign;
    private String lastMessageKey;
    private long lastMessageTime;

    public boolean hasLocalCallsign() {
        return localCallsign != null && !localCallsign.trim().isEmpty();
    }

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

        if (!isMessageForUs(source, dest, action)) {
            Log.d(TAG, "Ignoring APRS message not for this station");
            return;
        }

        String messageKey = action + "|" + source + "|" + dest + "|" + body;
        long now = System.currentTimeMillis();

        if (messageKey.equals(lastMessageKey) && now - lastMessageTime < 120000) {
            Log.d(TAG, "Ignoring duplicate APRS message");
            return;
        }

        lastMessageKey = messageKey;
        lastMessageTime = now;

        Log.d(TAG, "APRS MESSAGE action=" + action
                + " source=[" + source + "]"
                + " dest=[" + dest + "]"
                + " body=[" + body + "]");

        if (geoChatBridge != null) {
            geoChatBridge.receiveAprsMessage(source, dest, body);
        }
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

    public void setLocalCallsign(String callsign) {
        if (callsign == null || callsign.trim().isEmpty())
            return;

        localCallsign = callsign.trim().toUpperCase();

        Log.d(TAG, "Local APRS callsign set to [" + localCallsign + "]");
    }

    public boolean isMessageForUs(String source, String dest, String action) {
        if (localCallsign == null || dest == null || source == null)
            return false;

        String local = localCallsign.toUpperCase();
        String src = source.toUpperCase();
        String dst = dest.toUpperCase();

        if ("org.aprsdroid.app.MESSAGE".equals(action)) {
            return dst.equals(local) || stripSsid(dst).equals(stripSsid(local));
        }

        if ("org.aprsdroid.app.MESSAGETX".equals(action)) {
            return src.equals(local) || stripSsid(src).equals(stripSsid(local));
        }

        return false;
    }

    private String stripSsid(String call) {
        int dash = call.indexOf('-');
        return dash >= 0 ? call.substring(0, dash) : call;
    }

    public void setGeoChatBridge(AprsGeoChatBridge bridge) {
        this.geoChatBridge = bridge;
    }
}

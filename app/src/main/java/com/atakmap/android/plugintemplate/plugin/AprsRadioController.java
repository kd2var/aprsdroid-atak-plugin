package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AprsRadioController {

    private final Context context;

    public AprsRadioController(Context context) {
        this.context = context;
    }

    public void startAprsdroid() {
        try {
            Intent i = new Intent("org.aprsdroid.app.SERVICE");
            i.setPackage("org.aprsdroid.app");
            context.startService(i);
        } catch (Exception e) {
            Log.e("APRSMRADIO", "Failed to start APRSdroid", e);
        }
    }

    public void stopAprsdroid() {
        try {
            Intent i = new Intent("org.aprsdroid.app.SERVICE_STOP");
            i.setPackage("org.aprsdroid.app");
            context.startService(i);
        } catch (Exception e) {
            Log.e("APRSMRADIO", "Failed to stop APRSdroid", e);
        }
    }

    public void sendBeacon() {
        try {
            Intent i = new Intent("org.aprsdroid.app.ONCE");
            i.setPackage("org.aprsdroid.app");
            context.startService(i);
        } catch (Exception e) {
            Log.e("APRSMRADIO", "Failed to send APRSdroid beacon", e);
        }
    }
}
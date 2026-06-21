package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.content.Intent;

public class AprsRadioController {

    private final Context context;

    public AprsRadioController(Context context) {
        this.context = context;
    }

    public void startAprsdroid() {
        Intent i = new Intent("org.aprsdroid.app.SERVICE");
        i.setPackage("org.aprsdroid.app");
        context.startService(i);
    }

    public void stopAprsdroid() {
        Intent i = new Intent("org.aprsdroid.app.SERVICE_STOP");
        i.setPackage("org.aprsdroid.app");
        context.startService(i);
    }

    public void sendBeacon() {
        Intent i = new Intent("org.aprsdroid.app.ONCE");
        i.setPackage("org.aprsdroid.app");
        context.startService(i);
    }
}
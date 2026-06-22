package com.atakmap.android.plugintemplate.plugin;

import android.content.SharedPreferences;

import com.atakmap.android.contact.Connector;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.maps.MapView;

public class AprsContact extends IndividualContact {

    private static final String UID_PREFIX = "aprs.";

    public AprsContact(String callsign) {
        super(callsign, UID_PREFIX + callsign);

        setDispatch(false);
        addConnector(new AprsChatConnector(callsign));
        addConnector(AprsChatConnector.PLUGIN_CONNECTOR);
        setDispatch(true);
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext().getPackageName()
                + "/"
                + com.atakmap.app.R.drawable.ic_menu_send;
    }

    @Override
    public Connector getDefaultConnector(SharedPreferences prefs) {
        return getConnector(AprsChatConnector.PLUGIN_CONNECTOR.getConnectionType());
    }
}

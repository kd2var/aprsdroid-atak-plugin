package com.atakmap.android.plugintemplate.plugin;

import com.atakmap.android.contact.Connector;
import com.atakmap.android.contact.PluginConnector;
import com.atakmap.android.maps.MapView;

public class AprsChatConnector extends Connector {

    public static final String CONNECTOR_TYPE = "connector.aprschat";

    public static final String ACTION_SEND_CHAT_APRS =
            "com.atakmap.android.aprsimport.SEND_CHAT_APRS";

    public final static Connector PLUGIN_CONNECTOR =
            new AprsChatConnector(ACTION_SEND_CHAT_APRS) {
                @Override
                public String getConnectionType() {
                    return PluginConnector.CONNECTOR_TYPE;
                }
            };

    private final String callsign;

    public AprsChatConnector(final String callsign) {
        this.callsign = callsign;
    }

    @Override
    public String getConnectionString() {
        return callsign;
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "APRS";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext().getPackageName()
                + "/"
                + com.atakmap.app.R.drawable.ic_menu_send;
    }

    @Override
    public int getPriority() {
        return 2;
    }
}

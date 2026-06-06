package com.atakmap.android.plugintemplate.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.assets.Icon;
import java.util.HashMap;
import java.util.Iterator;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin {

    private static final String TAG = "APRSIMPORT";

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane templatePane;

    private BroadcastReceiver aprsReceiver;
    private final HashMap<String, Long> lastHeard = new HashMap<>();
    private MapGroup aprsGroup;
    private final HashMap<String, Marker> aprsMarkers = new HashMap<>();

    public PluginTemplate(IServiceController serviceController) {
        this.serviceController = serviceController;

        final PluginContextProvider ctxProvider =
                serviceController.getService(PluginContextProvider.class);

        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        uiService = serviceController.getService(IHostUIService.class);

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Plugin starting");

        MapView mapView = MapView.getMapView();
        if (mapView != null) {
            aprsGroup = mapView.getRootGroup().addGroup("APRS");
            Log.d(TAG, "APRS map group created");
        } else {
            Log.e(TAG, "MapView is null");
        }

        if (uiService != null) {
            uiService.addToolbarItem(toolbarItem);
        }

        aprsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Broadcast received: " + intent.getAction());

                Location loc = intent.getParcelableExtra("location");
                String callsign = intent.getStringExtra("callsign");
                String packet = intent.getStringExtra("status");
                String comment = packet != null ? packet : "";

                if (loc != null && callsign != null) {
                    lastHeard.put(callsign, System.currentTimeMillis());

                    // Remove stale stations
                    long now = System.currentTimeMillis();
                    Iterator<HashMap.Entry<String, Long>> it = lastHeard.entrySet().iterator();
                    while (it.hasNext()) {
                        HashMap.Entry<String, Long> entry = it.next();
                        if (now - entry.getValue() > (30 * 60 * 1000)) {
                            String oldCall = entry.getKey();
                            Marker oldMarker = aprsMarkers.get(oldCall);
                            if (oldMarker != null && aprsGroup != null) {
                                aprsGroup.removeItem(oldMarker);
                                aprsMarkers.remove(oldCall);
                                Log.d(TAG, "Removed stale station " + oldCall);
                            }
                            it.remove();
                        }
                    }

                    double lat = loc.getLatitude();
                    double lon = loc.getLongitude();
                    Log.d(TAG, "APRS " + callsign + " " + lat + ", " + lon);

                    Marker marker = aprsMarkers.get(callsign);

                    if (marker == null) {
                        // Create marker with white dot
                        marker = new Marker(new GeoPoint(lat, lon), callsign);

                        Icon icon = new Icon.Builder()
                                .setImageUri(0, "file:///android_asset/marker_generic.png")
                                .build();
                        marker.setIcon(icon);

                        // Callsign label
                        marker.setTitle(callsign);

                        // Comment for ATAK details panel
                        marker.setSummary(comment);

                        marker.setShowLabel(true);
                        marker.setAlwaysShowText(true);

                        if (aprsGroup != null) {
                            aprsGroup.addItem(marker);
                        }

                        aprsMarkers.put(callsign, marker);
                        Log.d(TAG, "Created marker " + callsign);

                    } else {
                        // Update existing marker
                        marker.setPoint(new GeoPoint(lat, lon));
                        marker.setTitle(callsign);
                        marker.setSummary(comment);
                        Log.d(TAG, "Updated marker " + callsign);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("org.aprsdroid.app.POSITION");
        filter.addAction("org.aprsdroid.app.UPDATE");
        filter.addAction("org.aprsdroid.app.MESSAGE");

        pluginContext.registerReceiver(aprsReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "APRS receiver registered");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "Plugin stopping");

        if (uiService != null) {
            uiService.removeToolbarItem(toolbarItem);
        }

        if (aprsReceiver != null) {
            try {
                pluginContext.unregisterReceiver(aprsReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void showPane() {
        if (templatePane == null) {
            templatePane = new PaneBuilder(
                    PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null))
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        if (!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
    }
}
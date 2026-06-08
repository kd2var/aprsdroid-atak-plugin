package com.atakmap.android.plugintemplate.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;
import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.assets.Icon;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import java.util.HashMap;
import java.util.Iterator;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.preference.AtakPreferences;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;
import android.os.Bundle;
import com.atakmap.android.maps.MapDataRef;
public class PluginTemplate implements IPlugin {

    private static final String TAG = "APRSIMPORT";

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane templatePane;
    private View paneView;
    private TextView aprsTextView;

    private BroadcastReceiver aprsReceiver;
    private final HashMap<String, Long> lastHeard = new HashMap<>();
    private MapGroup aprsGroup;
    private final HashMap<String, Marker> aprsMarkers = new HashMap<>();
    private final HashMap<String, String> stationComments = new HashMap<>();

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

        // Start stale station cleanup timer
        cleanupHandler.postDelayed(
                cleanupRunnable,
                60 * 1000);

        AtakPreferences prefs =
                new AtakPreferences(
                        MapView.getMapView().getContext());

        Log.e(TAG,
                "PREF CONTEXT="
                        + MapView.getMapView().getContext()
                        .getPackageName());

        Log.e(TAG,
                "ONSTART PREF FILE="
                        + prefs.getAll().toString());

        int savedHours =
                prefs.get(
                        PREF_STALE_HOURS,
                        1);

        Log.e(TAG,
                "ONSTART ATAK PREF="
                        + savedHours);

        staleMillis =
                savedHours
                        * 60L
                        * 60L
                        * 1000L;

        Log.e(TAG,
                "ONSTART LOADED="
                        + savedHours);


        Log.d(TAG, "Started APRS cleanup timer");

        aprsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Broadcast received: " + intent.getAction());

                Bundle extras = intent.getExtras();

                if (extras != null) {
                    for (String key : extras.keySet()) {
                        Log.d(TAG, "EXTRA: " + key + "=" + extras.get(key));
                    }
                }

                Location loc = intent.getParcelableExtra("location");

                String callsign = intent.getStringExtra("callsign");

                if (callsign == null) {
                    callsign = intent.getStringExtra("source");
                }

                String packet = intent.getStringExtra("status");

                if (packet == null) {
                    packet = intent.getStringExtra("packet");
                }

                String aprsSymbol = getAprsSymbol(packet);


                Log.d(TAG, "RAW SYMBOL=[" + aprsSymbol + "]");

                String normalized = normalizeAprsSymbol(aprsSymbol);

                Log.d(TAG, "NORMALIZED=[" + normalized + "]");

                Log.d(TAG,
                        "SYMBOL=[" + aprsSymbol + "] "
                                + getSymbolName(aprsSymbol)
                                + " INDEX=" + getAprsIndex(aprsSymbol));

                Log.d(TAG, "CALLSIGN=[" + callsign + "]");
                Log.d(TAG, "PACKET=[" + packet + "]");
                String comment = "";

                if (packet != null) {

                    int pos = packet.indexOf(":=");

                    if (pos < 0) {
                        pos = packet.indexOf(":!");
                    }

                    if (pos >= 0) {

                        String body = packet.substring(pos + 2);

                        int slashA = body.indexOf("/A=");

                        if (slashA >= 0) {

                            int spaceAfterAltitude = body.indexOf(' ', slashA);

                            if (spaceAfterAltitude > 0 &&
                                    spaceAfterAltitude < body.length() - 1) {

                                comment = body.substring(spaceAfterAltitude + 1).trim();
                            }

                        } else {

                            // no altitude field
                            if (body.length() > 19) {
                                comment = body.substring(19).trim();
                            }
                        }
                    }
                }
                Log.d(TAG, "CALLSIGN=[" + callsign + "]");
                Log.d(TAG, "COMMENT=[" + comment + "]");
                Log.d(TAG, "COMMENT=[" + comment + "]");

                Log.d(TAG, "aprsTextView is null = " + (aprsTextView == null));

                if (callsign != null) {

                    if (!comment.isEmpty()) {
                        stationComments.put(callsign, comment);
                    }

                    if (aprsTextView != null) {

                        StringBuilder sb = new StringBuilder();

                        for (String call : stationComments.keySet()) {

                            sb.append(call)
                                    .append("\n")
                                    .append(stationComments.get(call))
                                    .append("\n\n");
                        }

                        aprsTextView.setText(sb.toString());

                        Log.d(TAG, "Updated APRS pane: " + callsign);
                    }
                }

                if (loc != null && callsign != null) {
                    lastHeard.put(callsign, System.currentTimeMillis());



                    double lat = loc.getLatitude();
                    double lon = loc.getLongitude();
                    Log.d(TAG, "APRS " + callsign + " " + lat + ", " + lon);

                    Marker marker = aprsMarkers.get(callsign);

                    if (marker == null) {

                        marker = new Marker(new GeoPoint(lat, lon), callsign);


                        Log.d(TAG, "ICON PATH = " + getAprsIcon(normalized));

                        int drawableId = getAprsDrawable(normalized);

                        Log.d(TAG,
                                "DRAWABLE="
                                        + drawableId
                                        + " SYMBOL="
                                        + aprsSymbol);

                        String uri =
                                "android.resource://"
                                        + pluginContext.getPackageName()
                                        + "/"
                                        + drawableId;

                        Log.d(TAG, "ICON URI=" + uri);

                        Log.d(TAG,
                                "TABLE=" + normalizeAprsSymbol(aprsSymbol).charAt(0)
                                        + " SYMBOL_CHAR=" + normalizeAprsSymbol(aprsSymbol).charAt(1)
                                        + " ASCII=" + (int)normalizeAprsSymbol(aprsSymbol).charAt(1)
                                        + " INDEX=" + getAprsIndex(normalizeAprsSymbol(aprsSymbol)));

                        Icon icon = new Icon.Builder()
                                .setImageUri(0, uri)
                                .setSize(32, 32)
                                .build();

                        marker.setIcon(icon);

                        Log.d(TAG, "ICON OBJECT = " + marker.getIcon());

                        marker.setTitle(callsign);
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

    // Stop stale station cleanup timer
    cleanupHandler.removeCallbacks(cleanupRunnable);

    if (uiService != null) {
        uiService.removeToolbarItem(toolbarItem);
    }

    if (aprsReceiver != null) {
        try {
            pluginContext.unregisterReceiver(aprsReceiver);
        } catch (Exception ignored) {}
    }
}


    private int getAprsDrawable(String aprsSymbol) {

        int index = getAprsIndex(aprsSymbol);

        String resourceName;

        if (aprsSymbol.startsWith("/")) {

            resourceName = String.format(
                    "aprs_t0_symbol_%02d",
                    index);

        } else if (aprsSymbol.startsWith("\\")) {

            resourceName = String.format(
                    "aprs_t1_symbol_%02d",
                    index);

        } else {

            resourceName = String.format(
                    "aprs_t2_symbol_%02d",
                    index);
        }

        int drawableId = pluginContext.getResources()
                .getIdentifier(
                        resourceName,
                        "drawable",
                        pluginContext.getPackageName());

        Log.d(TAG,
                "RESOURCE="
                        + resourceName
                        + " ID="
                        + drawableId);

        return drawableId;
    }
    private int getAprsIndex(String symbol) {

        if (symbol == null || symbol.length() != 2) {
            return -1;
        }

        symbol = normalizeAprsSymbol(symbol);

        return symbol.charAt(1) - 33;
    }


    private String normalizeAprsSymbol(String symbol) {

        if (symbol == null || symbol.length() != 2)
            return symbol;

        char overlay = symbol.charAt(0);
        char base = symbol.charAt(1);

        if ((overlay >= 'A' && overlay <= 'Z')
                || (overlay >= '0' && overlay <= '9')) {

            String converted = "\\" + String.valueOf(base);

            Log.d(TAG,
                    "Converting "
                            + symbol
                            + " to "
                            + converted);

            return converted;
        }

        if (overlay == 'I' && base == '#') {

            Log.d(TAG,
                    "Converting "
                            + symbol
                            + " to /#");

            return "/#";
        }

        return symbol;
    }
    private String getAprsSymbol(String packet) {

        if (packet == null) {
            return null;
        }

        int pos = packet.indexOf(':');

        if (pos < 0) {
            return null;
        }

        String body = packet.substring(pos + 1);

        if (body.length() < 20) {
            return null;
        }

        char table = body.charAt(9);
        char symbol = body.charAt(19);

        return "" + table + symbol;
    }



    private String getSymbolName(String symbol) {

        if (symbol == null) {
            return "Unknown";
        }

        switch (symbol) {

            case "/-":
                return "Home";

            case "/[":
                return "Person";

            case "/>":
                return "Car";

            case "/_":
                return "Weather";

            case "/#":
                return "Digipeater";

            default:
                return "Unknown";
        }
    }

    private String getAprsIcon(String aprsSymbol) {

        if (aprsSymbol == null) {

            Log.e(TAG,
                    "NULL APRS SYMBOL - using default icon");

            return "asset:///aprs/table0/symbol_00.png";
        }

        int index = getAprsIndex(aprsSymbol);

        if (index < 0) {

            Log.e(TAG,
                    "INVALID APRS SYMBOL ["
                            + aprsSymbol
                            + "] - using default icon");

            index = 0;
        }

        if (aprsSymbol.startsWith("/")) {

            return "asset:///aprs/table0/symbol_"
                    + String.format("%02d", index)
                    + ".png";

        } else if (aprsSymbol.startsWith("\\")) {

            return "asset:///aprs/table1/symbol_"
                    + String.format("%02d", index)
                    + ".png";

        } else {

            return "asset:///aprs/table2/symbol_"
                    + String.format("%02d", index)
                    + ".png";
        }
    }

    private static final String PREF_STALE_HOURS = "aprs_stale_hours";

    private long staleMillis = 60 * 60 * 1000;

    private final Handler cleanupHandler =
            new Handler(Looper.getMainLooper());

private final Runnable cleanupRunnable =
        new Runnable() {
            @Override
            public void run() {

                removeStaleStations();
                refreshAprsPane();

                cleanupHandler.postDelayed(
                        this,
                        60 * 1000);
            }
        };


    private void showPane() {
        if (templatePane == null) {

            paneView = PluginLayoutInflater.inflate(
                    pluginContext,
                    R.layout.main_layout,
                    null);

            aprsTextView = paneView.findViewById(R.id.aprsText);

            Button staleMinus =
                    paneView.findViewById(R.id.staleMinus);

            Button stalePlus =
                    paneView.findViewById(R.id.stalePlus);

            TextView staleHoursText =
                    paneView.findViewById(R.id.staleHoursText);

            final int[] choices = {1, 2, 4, 8, 12, 24};

            AtakPreferences prefs =
                    new AtakPreferences(
                            MapView.getMapView().getContext());

            Log.e(TAG,
                    "PREF CONTEXT="
                            + MapView.getMapView().getContext()
                            .getPackageName());

            Log.e(TAG,
                    "SHOWPANE PREF FILE="
                            + prefs.getAll().toString());

            int savedHours =
                    prefs.get(
                            PREF_STALE_HOURS,
                            1);

            Log.e(TAG,
                    "SHOWPANE PREF VALUE="
                            + savedHours);

            // initialize currentIndex from savedHours
            final int[] currentIndex = {0};
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] == savedHours) {
                    currentIndex[0] = i;
                    break;
                }
            }


// now set staleMillis from the saved index
            staleMillis = choices[currentIndex[0]] * 60L * 60L * 1000L;

            staleHoursText.setText(
                    "Stale Time: "
                            + savedHours
                            + " hour");

            staleMinus.setOnClickListener(v -> {

                if (currentIndex[0] > 0) {

                    currentIndex[0]--;

                    staleMillis =
                            choices[currentIndex[0]]
                                    * 60L
                                    * 60L
                                    * 1000L;

                    staleHoursText.setText(
                            "Stale Time: "
                                    + choices[currentIndex[0]]
                                    + " hour");

                    prefs.set(
                            PREF_STALE_HOURS,
                            choices[currentIndex[0]]);

                    int verify =
                            prefs.get(
                                    PREF_STALE_HOURS,
                                    -1);

                    Log.e(TAG,
                            "VERIFY="
                                    + verify);
                    Log.e(TAG,
                            "SAVE TEST "
                                    + choices[currentIndex[0]]);
                }
            });

            stalePlus.setOnClickListener(v -> {

                if (currentIndex[0] < choices.length - 1) {

                    currentIndex[0]++;

                    staleMillis =
                            choices[currentIndex[0]]
                                    * 60L
                                    * 60L
                                    * 1000L;

                    staleHoursText.setText(
                            "Stale Time: "
                                    + choices[currentIndex[0]]
                                    + " hour");

                    prefs.set(
                            PREF_STALE_HOURS,
                            choices[currentIndex[0]]);

                    int verify =
                            prefs.get(
                                    PREF_STALE_HOURS,
                                    -1);

                    Log.e(TAG,
                            "VERIFY="
                                    + verify);

                    Log.e(TAG,
                            "PREF FILE="
                                    + prefs.getAll().toString());
                    Log.e(TAG,
                            "SAVE TEST "
                                    + choices[currentIndex[0]]);
                }
            });



            templatePane = new PaneBuilder(paneView)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        if (!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }

        refreshAprsPane();
    }

    private void removeStaleStations() {


    long now =
            System.currentTimeMillis();

    boolean changed = false;

    Iterator<HashMap.Entry<String, Long>> it =
            lastHeard.entrySet().iterator();

    while (it.hasNext()) {

        HashMap.Entry<String, Long> entry =
                it.next();

        if (now - entry.getValue() > staleMillis) {

            String oldCall =
                    entry.getKey();

            Marker oldMarker =
                    aprsMarkers.get(oldCall);

            if (oldMarker != null &&
                    aprsGroup != null) {

                aprsGroup.removeItem(oldMarker);
            }

            aprsMarkers.remove(oldCall);
            stationComments.remove(oldCall);

            Log.d(TAG,
                    "Removed stale station "
                            + oldCall);

            it.remove();

            changed = true;
        }
    }

    if (changed) {
        refreshAprsPane();
    }
}

    private void refreshAprsPane() {

        if (aprsTextView == null)
            return;

        StringBuilder sb = new StringBuilder();

        long now = System.currentTimeMillis();

        for (String call : stationComments.keySet()) {

            Long heard = lastHeard.get(call);

            String age = "?";

            if (heard != null) {

                long minutes =
                        (now - heard) / 60000;

                if (minutes < 60) {

                    age = minutes + "m";

                } else {

                    age = (minutes / 60) + "h";
                }
            }

            sb.append(call)
                    .append("   ")
                    .append(age)
                    .append(" ago")
                    .append("\n")
                    .append(stationComments.get(call))
                    .append("\n\n");
        }

        aprsTextView.setText(sb.toString());

        Log.d(TAG, "APRS pane refreshed");
    }
}
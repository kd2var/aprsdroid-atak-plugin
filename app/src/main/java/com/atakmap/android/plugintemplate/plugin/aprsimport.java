package com.atakmap.android.plugintemplate.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import android.os.Bundle;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;                    // IMPORTANT
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconSet;
import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.android.cot.CotMapComponent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

import com.atakmap.android.plugintemplate.plugin.AprsPacketParser;

/**
 * APRS Import Plugin for ATAK - Cleaned & Fixed Version
 */
public class aprsimport implements IPlugin {

    private static final String TAG = "APRSIMPORT";

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane templatePane;
    private View paneView;
    private LinearLayout aprsList;
    private TextView aprsStatus;
    private AprsRadioController radioController;
    private AprsMessageManager messageManager;
    private AprsGeoChatBridge geoChatBridge;
    private AprsConnectorHandler aprsConnectorHandler;
    private View radioPage;
    private View stationsPage;
    private boolean sortByDistance = false;
    private boolean callsignPromptShown = false;

    private BroadcastReceiver aprsReceiver;

    private BroadcastReceiver aprsChatReplyReceiver;

    // Station data
    private final HashMap<String, Long> lastHeard = new HashMap<>();
    private final HashMap<String, String> stationComments = new HashMap<>();
    private final HashMap<String, String> stationAltitude = new HashMap<>();
    private final HashMap<String, String> stationTemperature = new HashMap<>();
    private final HashMap<String, String> stationWind = new HashMap<>();
    private final HashMap<String, String> stationBarometer = new HashMap<>();
    private final HashMap<String, String> stationHumidity = new HashMap<>();
    private final HashMap<String, String> stationRain1Hour = new HashMap<>();
    private final HashMap<String, String> stationRain24Hour = new HashMap<>();
    private final HashMap<String, String> stationRainMidnight = new HashMap<>();
    private final HashMap<String, String> stationCourse = new HashMap<>();
    private final HashMap<String, String> stationSpeed = new HashMap<>();

    private final CotDispatcher cotDispatcher = com.atakmap.android.cot.CotMapComponent.getInternalDispatcher();

    private static final String APRS_ICONSET_UID = "9c9f2b11-0d60-4f6c-a001-aprsiconset-v3";
    private static final String APRS_ICONSET_NAME = "APRS";

    private static final String PREF_STALE_HOURS = "aprs_stale_hours";
    private long staleMillis = 60 * 60 * 1000L;

    private final Handler cleanupHandler = new Handler(Looper.getMainLooper());

    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            removeStaleStations();
            refreshAprsPane();
            cleanupHandler.postDelayed(this, 60 * 1000);
        }
    };

    public aprsimport(IServiceController serviceController) {
        this.serviceController = serviceController;

        final PluginContextProvider ctxProvider = serviceController.getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
            registerAprsIconSet();

            radioController = new AprsRadioController(pluginContext);
            messageManager = new AprsMessageManager(pluginContext);
            geoChatBridge = new AprsGeoChatBridge(pluginContext);
            messageManager.setGeoChatBridge(geoChatBridge);
        }

        uiService = serviceController.getService(IHostUIService.class);


        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.plugin_icon),
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

        if (uiService != null) {
            uiService.addToolbarItem(toolbarItem);
        }

        if (aprsConnectorHandler == null) {
            aprsConnectorHandler = new AprsConnectorHandler();

            CotMapComponent.getInstance()
                    .getContactConnectorMgr()
                    .addContactHandler(aprsConnectorHandler);

            Log.d(TAG, "APRS connector handler registered");
        }

        cleanupHandler.postDelayed(cleanupRunnable, 60 * 1000);

        AtakPreferences prefs = new AtakPreferences(MapView.getMapView().getContext());
        int savedHours = prefs.get(PREF_STALE_HOURS, 1);
        staleMillis = savedHours * 60L * 60L * 1000L;

        aprsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processAprsBroadcast(intent);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("org.aprsdroid.app.POSITION");
        filter.addAction("org.aprsdroid.app.UPDATE");
        filter.addAction("org.aprsdroid.app.MESSAGE");
        filter.addAction("org.aprsdroid.app.MESSAGETX");
        filter.addAction("org.aprsdroid.app.HUD");
        filter.addAction("org.aprsdroid.app.SERVICE_STARTED");
        filter.addAction("org.aprsdroid.app.SERVICE_STOPPED");
        filter.addAction(AprsChatConnector.ACTION_SEND_CHAT_APRS);

        aprsChatReplyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                Bundle chatMessage = intent.getBundleExtra(
                        ChatManagerMapComponent.PLUGIN_SEND_MESSAGE_EXTRA);

                Log.d(TAG, "APRS GeoChat reply action="
                        + intent.getAction()
                        + " bundle="
                        + chatMessage);

                if (chatMessage != null) {

                    String destination =
                            chatMessage.getString("conversationName");

                    String message =
                            chatMessage.getString("message");

                    Log.d(TAG, "APRS GeoChat reply destination=["
                            + destination + "] message=["
                            + message + "]");

                    if (messageManager != null
                            && destination != null
                            && message != null
                            && !destination.trim().isEmpty()
                            && !message.trim().isEmpty()) {

                        messageManager.sendAprsMessage(
                                destination.trim(),
                                message.trim());
                    }
                }
            }
        };

        AtakBroadcast.DocumentedIntentFilter chatFilter =
                new AtakBroadcast.DocumentedIntentFilter();

        chatFilter.addAction(
                AprsChatConnector.ACTION_SEND_CHAT_APRS,
                "Sends APRS Chat message");

        AtakBroadcast.getInstance().registerReceiver(aprsChatReplyReceiver, chatFilter);

        pluginContext.registerReceiver(aprsReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "APRS receiver registered");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "Plugin stopping");
        cleanupHandler.removeCallbacks(cleanupRunnable);

        if (uiService != null) uiService.removeToolbarItem(toolbarItem);

        if (aprsReceiver != null) {
            try {
                pluginContext.unregisterReceiver(aprsReceiver);
            } catch (Exception ignored) {}
        }
    }

    private void processAprsBroadcast(Intent intent) {

        if (messageManager != null
                && messageManager.isAprsMessageIntent(intent)) {

            if (!messageManager.hasLocalCallsign()) {

                if (!callsignPromptShown) {
                    callsignPromptShown = true;
                    promptForLocalCallsign();
                }

                Log.d(TAG, "APRS message received before local callsign was known");
                return;
            }

            messageManager.handleIncomingIntent(intent);
            return;
        }

        String action = intent.getAction();

        if ("org.aprsdroid.app.SERVICE_STARTED".equals(action)) {

            if (aprsStatus != null) {

                aprsStatus.setText("🟢 APRSdroid Running");
                aprsStatus.setTextColor(Color.GREEN);

            }

            String localCallsign = intent.getStringExtra("callsign");

            if (messageManager != null) {
                messageManager.setLocalCallsign(localCallsign);
            }

            return;
        }

        if ("org.aprsdroid.app.SERVICE_STOPPED".equals(action)) {

            if (aprsStatus != null) {

                aprsStatus.setText("🔴 APRSdroid Stopped");
                aprsStatus.setTextColor(Color.RED);

            }

            return;
        }

        Location loc = intent.getParcelableExtra("location");

        if ("org.aprsdroid.app.HUD".equals(intent.getAction())) {
            int latRaw = intent.getIntExtra("org.aprsdroid.app.LOCATION_LAT", Integer.MIN_VALUE);
            int lonRaw = intent.getIntExtra("org.aprsdroid.app.LOCATION_LON", Integer.MIN_VALUE);
            if (latRaw != Integer.MIN_VALUE && lonRaw != Integer.MIN_VALUE) {
                loc = new Location("HUD");
                loc.setLatitude(latRaw / 1000000.0);
                loc.setLongitude(lonRaw / 1000000.0);
            }
        }

        String callsign = getCallsign(intent);
        if (callsign == null) return;

        String packet = intent.getStringExtra("status");
        if (packet == null) packet = intent.getStringExtra("packet");

        String rawComment =
                AprsPacketParser.extractComment(intent, packet);

        String symbolHint = null;

        if ("org.aprsdroid.app.HUD".equals(intent.getAction())) {
            symbolHint = intent.getStringExtra("org.aprsdroid.app.SYMBOL");
        }

        AprsPacketParser.ParsedData parsed =
                AprsPacketParser.parse(packet, rawComment, symbolHint);

        String aprsSymbol = parsed.symbol;

        if (aprsSymbol == null || aprsSymbol.length() < 2) aprsSymbol = "/>";

        String cleanedComment =
                cleanAprsComment(callsign, parsed.comment);

        if (parsed.course != null)
            stationCourse.put(callsign, parsed.course);

        if (parsed.speed != null)
            stationSpeed.put(callsign, parsed.speed);

        if (parsed.windDirection != null) {

            stationWind.put(
                    callsign,
                    parsed.windDirection + "° "
                            + parsed.windSpeed + " mph"
                            + (parsed.windGust != null
                            ? " Gust " + parsed.windGust + " mph"
                            : ""));

        }

        if (parsed.temperature != null)
            stationTemperature.put(callsign, parsed.temperature);

        if (parsed.humidity != null)
            stationHumidity.put(callsign, parsed.humidity);

        if (parsed.barometer != null)
            stationBarometer.put(
                    callsign,
                    String.format("%.1f",
                            Integer.parseInt(parsed.barometer) / 10.0));

        if (parsed.rain1Hour != null)
            stationRain1Hour.put(callsign, parsed.rain1Hour);

        if (parsed.rain24Hour != null)
            stationRain24Hour.put(callsign, parsed.rain24Hour);

        if (parsed.rainSinceMidnight != null)
            stationRainMidnight.put(callsign, parsed.rainSinceMidnight);

        if (parsed.altitude != null)
            stationAltitude.put(callsign, parsed.altitude);

        Log.d(TAG, "PARSED " + callsign
                + " type=" + parsed.type
                + " course=" + parsed.course
                + " speed=" + parsed.speed
                + " windDir=" + parsed.windDirection
                + " windSpeed=" + parsed.windSpeed
                + " comment=[" + parsed.comment + "]");

        stationComments.put(callsign, cleanedComment);
        lastHeard.put(callsign, System.currentTimeMillis());

        if (loc != null) {

            if (geoChatBridge != null) {
                geoChatBridge.getOrCreateAprsContact(callsign);
            }

            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            String iconPath = buildAprsIconPath(aprsSymbol);

            String detailText = buildDetailText(callsign);
            sendAprsCot(callsign, lat, lon, System.currentTimeMillis(), iconPath, detailText);
        }

        refreshAprsPane();
    }

    private String getCallsign(Intent intent) {
        if ("org.aprsdroid.app.HUD".equals(intent.getAction())) {
            return intent.getStringExtra("org.aprsdroid.app.CALLSIGN");
        }
        String callsign = intent.getStringExtra("callsign");
        if (callsign == null) callsign = intent.getStringExtra("source");
        return callsign;
    }

    private String buildDetailText(String callsign) {
        StringBuilder sb = new StringBuilder(stationComments.getOrDefault(callsign, ""));
        addIfNotEmpty(sb, stationAltitude.get(callsign), "\nAltitude: ", " ft");
        addIfNotEmpty(sb, stationTemperature.get(callsign), "\nTemp: ", "°F");
        addIfNotEmpty(sb, stationWind.get(callsign), "\nWind: ", "");
        addIfNotEmpty(sb, stationBarometer.get(callsign), "\nBaro: ", " mb");
        addIfNotEmpty(sb, stationHumidity.get(callsign), "\nHumidity: ", "%");
        addIfNotEmpty(sb, stationCourse.get(callsign), "\nCourse: ", "°");
        addIfNotEmpty(sb, stationSpeed.get(callsign), "\nSpeed: ", " mph");
        addIfNotEmpty(sb, stationRain1Hour.get(callsign), "\nRain 1hr: ", "");
        addIfNotEmpty(sb, stationRain24Hour.get(callsign), "\nRain 24hr: ", "");
        addIfNotEmpty(sb, stationRainMidnight.get(callsign), "\nRain Midnight: ", "");
        return sb.toString().trim();
    }

    private void addIfNotEmpty(StringBuilder sb, String value, String prefix, String suffix) {
        if (value != null && !value.isEmpty()) {
            sb.append(prefix).append(value).append(suffix);
        }
    }

    private void sendAprsCot(String callsign, double lat, double lon, long lastHeardTime, String iconPath, String detailText) {
        try {
            java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            iso.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

            String time = iso.format(new java.util.Date());
            String stale = iso.format(new java.util.Date(lastHeardTime + staleMillis));

            String cotXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<event version=\"2.0\" uid=\"" + getAprsUid(callsign) + "\" type=\"a-f-G-U-C\" "
                    + "time=\"" + time + "\" start=\"" + time + "\" stale=\"" + stale + "\" how=\"m-g\">"
                    + "<point lat=\"" + lat + "\" lon=\"" + lon + "\" hae=\"0\" ce=\"9999\" le=\"9999\"/>"
                    + "<detail>"
                    + "<contact callsign=\"" + callsign + "\"/>"
                    + "<usericon iconsetpath=\"" + iconPath + "\"/>"
                    + "<remarks>" + detailText.replace("\"", "&quot;") + "</remarks>"
                    + "</detail></event>";

            cotDispatcher.dispatch(CotEvent.parse(cotXml));
        } catch (Exception e) {
            Log.e(TAG, "COT SEND FAILED", e);
        }
    }

    private void registerAprsIconSet() {
        try {
            UserIconDatabase db = UserIconDatabase.instance(pluginContext);
            if (db.getIconSet(APRS_ICONSET_UID, false, false) != null) {
                Log.d(TAG, "APRS iconset already installed - skipping");
                return;
            }

            Log.d(TAG, "Installing APRS iconset");
            UserIconSet iconSet = new UserIconSet(APRS_ICONSET_NAME, APRS_ICONSET_UID);
            db.addIconSet(iconSet);

            for (int i = 0; i < 96; i++) {
                installIcon(db, "table0", String.format("symbol_%02d.png", i), "a-f-G-U-C");
            }
            for (int i = 0; i < 96; i++) {
                installIcon(db, "table1", String.format("symbol_%02d.png", i), "a-f-G-U-C");
            }
        } catch (Exception e) {
            Log.e(TAG, "ICONSET INSTALL FAILED", e);
        }
    }

    private void installIcon(UserIconDatabase db, String group, String fileName, String cotType) {
        try {
            java.io.InputStream is = pluginContext.getAssets().open("aprs/" + group + "/" + fileName);
            Bitmap raw = BitmapFactory.decodeStream(is);
            Bitmap bmp = Bitmap.createScaledBitmap(raw, 48, 48, true);

            int iconId = Math.abs((group + "/" + fileName).hashCode());

            String uniqueFileName = group + "_" + fileName;

            UserIcon icon = new UserIcon(
                    iconId,
                    APRS_ICONSET_UID,
                    group,
                    uniqueFileName,
                    cotType,
                    bmp,
                    48);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            db.addIcon(icon, baos.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "Failed to install icon " + fileName, e);
        }
    }

    private String buildAprsIconPath(String aprsSymbol) {
        if (aprsSymbol == null || aprsSymbol.length() != 2)
            aprsSymbol = "/>";

        int index = getAprsIndex(aprsSymbol);
        if (index < 0) index = 0;

        char tableId = aprsSymbol.charAt(0);

        String table;

        if (tableId == '/') {
            table = "table0";
        } else {
            // '\' and overlay characters A-Z / 0-9 use alternate table
            table = "table1";
        }

        String storedName = table + "_symbol_" + String.format("%02d", index) + ".png";

        String path = APRS_ICONSET_UID + "/" + table + "/" + storedName;

        return path;
    }

    private int getAprsIndex(String symbol) {
        if (symbol == null || symbol.length() != 2) return -1;
        return symbol.charAt(1) - 33;
    }

    private String cleanAprsComment(String callsign, String comment) {
        if (comment == null) return "";

        comment = comment.trim();

        if (comment.matches("^[^A-Za-z0-9]{1,4}$"))
            return "";

        return comment;
    }

    private void showPane() {
        if (templatePane == null) {
            paneView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
            Button radioPageButton = paneView.findViewById(R.id.radioPageButton);
            Button stationsPageButton = paneView.findViewById(R.id.stationsPageButton);

            radioPage = paneView.findViewById(R.id.radioPage);
            stationsPage = paneView.findViewById(R.id.stationsPage);

            radioPage.setVisibility(View.VISIBLE);
            stationsPage.setVisibility(View.GONE);

            radioPageButton.setOnClickListener(v -> {
                radioPage.setVisibility(View.VISIBLE);
                stationsPage.setVisibility(View.GONE);
            });

            stationsPageButton.setOnClickListener(v -> {
                radioPage.setVisibility(View.GONE);
                stationsPage.setVisibility(View.VISIBLE);
                refreshAprsPane();
            });

            Button staleMinus = paneView.findViewById(R.id.staleMinus);
            Button stalePlus = paneView.findViewById(R.id.stalePlus);
            TextView staleHoursText = paneView.findViewById(R.id.staleHoursText);

            Button startAprsButton = paneView.findViewById(R.id.startAprsButton);
            Button stopAprsButton = paneView.findViewById(R.id.stopAprsButton);
            Button beaconButton = paneView.findViewById(R.id.beaconButton);
            Button newMessageButton = paneView.findViewById(R.id.newMessageButton);
            Button sortButton = paneView.findViewById(R.id.sortButton);

            startAprsButton.setOnClickListener(v -> {

                radioController.startAprsdroid();

                Log.d(TAG, "Start APRSdroid button sent SERVICE intent");

                aprsStatus.setText("Starting APRSdroid...");
                aprsStatus.setTextColor(Color.YELLOW);

            });

            stopAprsButton.setOnClickListener(v -> {

                radioController.stopAprsdroid();

                aprsStatus.setText("Stopping APRSdroid...");
                aprsStatus.setTextColor(Color.YELLOW);

            });

            beaconButton.setOnClickListener(v -> {

                radioController.sendBeacon();

                aprsStatus.setText("Sending Beacon...");
                aprsStatus.setTextColor(Color.YELLOW);

            });

            newMessageButton.setOnClickListener(v -> {

                final EditText editText = new EditText(pluginContext);

                editText.setHint("Enter Callsign");

                new AlertDialog.Builder(MapView.getMapView().getContext())
                        .setTitle("New APRS Message")
                        .setView(editText)
                        .setPositiveButton("Next", (dialog, which) -> {

                            String callsign = editText.getText().toString().trim().toUpperCase();

                            if (!callsign.isEmpty()) {

                                final EditText messageText = new EditText(pluginContext);
                                messageText.setHint("Enter Message");

                                new AlertDialog.Builder(MapView.getMapView().getContext())
                                        .setTitle("Message to " + callsign)
                                        .setView(messageText)
                                        .setPositiveButton("Send", (msgDialog, msgWhich) -> {

                                            String message = messageText.getText().toString().trim();

                                            if (!message.isEmpty()) {
                                                messageManager.sendAprsMessage(callsign, message);

                                                Toast.makeText(
                                                                MapView.getMapView().getContext(),
                                                                "Sent APRS message to " + callsign,
                                                                Toast.LENGTH_SHORT)
                                                        .show();
                                            }

                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();

                            }

                        })
                        .setNegativeButton("Cancel", null)
                        .show();

            });

            sortButton.setOnClickListener(v -> {

                sortByDistance = !sortByDistance;

                sortButton.setText(
                        sortByDistance
                                ? "Sort: Distance"
                                : "Sort: Recent");

                refreshAprsPane();

            });

            aprsList = paneView.findViewById(R.id.aprsList);
            aprsStatus = paneView.findViewById(R.id.aprsStatus);
            aprsStatus.setText("⚪ Waiting for APRSdroid...");
            aprsStatus.setTextColor(Color.LTGRAY);

            final int[] choices = {1, 2, 4, 8, 12, 24};
            AtakPreferences prefs = new AtakPreferences(MapView.getMapView().getContext());
            int savedHours = prefs.get(PREF_STALE_HOURS, 1);

            final int[] currentIndex = {0};
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] == savedHours) currentIndex[0] = i;
            }

            staleMillis = choices[currentIndex[0]] * 60L * 60L * 1000L;
            staleHoursText.setText("Stale Time: " + savedHours + " hour");

            staleMinus.setOnClickListener(v -> {
                if (currentIndex[0] > 0) {
                    currentIndex[0]--;
                    updateStaleTime(choices, currentIndex[0], staleHoursText, prefs);
                }
            });

            stalePlus.setOnClickListener(v -> {
                if (currentIndex[0] < choices.length - 1) {
                    currentIndex[0]++;
                    updateStaleTime(choices, currentIndex[0], staleHoursText, prefs);
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

    private void updateStaleTime(int[] choices, int index, TextView textView, AtakPreferences prefs) {
        staleMillis = choices[index] * 60L * 60L * 1000L;
        textView.setText("Stale Time: " + choices[index] + " hour");
        prefs.set(PREF_STALE_HOURS, choices[index]);
    }

    private void showStationDetails(String call) {

        StringBuilder text = new StringBuilder();

        text.append("Callsign: ").append(call);

        MapItem item = MapView.getMapView().getRootGroup().deepFindUID(getAprsUid(call));

        if (item instanceof Marker && MapView.getMapView().getSelfMarker() != null) {

            Marker marker = (Marker) item;

            double meters = MapView.getMapView()
                    .getSelfMarker()
                    .getPoint()
                    .distanceTo(marker.getPoint());

            double miles = meters * 0.000621371;

            text.append(String.format("\nDistance: %.1f mi", miles));
        }

        addIfNotEmpty(text, stationComments.get(call), "\nComment: ", "");
        addIfNotEmpty(text, stationAltitude.get(call), "\nAltitude: ", " ft");
        addIfNotEmpty(text, stationTemperature.get(call), "\nTemperature: ", "°F");
        addIfNotEmpty(text, stationHumidity.get(call), "\nHumidity: ", "%");
        addIfNotEmpty(text, stationBarometer.get(call), "\nBarometer: ", " mb");
        addIfNotEmpty(text, stationWind.get(call), "\nWind: ", "");
        addIfNotEmpty(text, stationRain1Hour.get(call), "\nRain 1hr: ", "");
        addIfNotEmpty(text, stationRain24Hour.get(call), "\nRain 24hr: ", "");
        addIfNotEmpty(text, stationRainMidnight.get(call), "\nRain Midnight: ", "");
        addIfNotEmpty(text, stationCourse.get(call), "\nCourse: ", "°");
        addIfNotEmpty(text, stationSpeed.get(call), "\nSpeed: ", " mph");

        Long heard = lastHeard.get(call);

        if (heard != null) {

            long ageMinutes = (System.currentTimeMillis() - heard) / 60000;

            long hrs = ageMinutes / 60;
            long mins = ageMinutes % 60;

            if (hrs > 0)
                text.append("\nLast Heard: ").append(hrs).append(" hr ").append(mins).append(" min ago");
            else
                text.append("\nLast Heard: ").append(mins).append(" min ago");
        }

        new AlertDialog.Builder(MapView.getMapView().getContext())
                .setTitle("Station Details")
                .setMessage(text.toString())
                .setPositiveButton("Back", null)
                .show();

    }

    private void refreshAprsPane() {

        if (aprsList == null)
            return;

        aprsList.removeAllViews();

        long now = System.currentTimeMillis();

        List<String> stations = new ArrayList<>(stationComments.keySet());

        if (sortByDistance && MapView.getMapView().getSelfMarker() != null) {

            stations.sort((a, b) -> {

                double da = Double.MAX_VALUE;
                double db = Double.MAX_VALUE;

                MapItem ia = MapView.getMapView().getRootGroup().deepFindUID(getAprsUid(a));
                MapItem ib = MapView.getMapView().getRootGroup().deepFindUID(getAprsUid(b));

                if (ia instanceof Marker) {
                    da = MapView.getMapView()
                            .getSelfMarker()
                            .getPoint()
                            .distanceTo(((Marker) ia).getPoint());
                }

                if (ib instanceof Marker) {
                    db = MapView.getMapView()
                            .getSelfMarker()
                            .getPoint()
                            .distanceTo(((Marker) ib).getPoint());
                }

                return Double.compare(da, db);

            });

        } else {

            stations.sort((a, b) ->
                    Long.compare(
                            lastHeard.getOrDefault(b, 0L),
                            lastHeard.getOrDefault(a, 0L)));

        }

        for (String call : stations) {

            MapItem item = MapView.getMapView()
                    .getRootGroup()
                    .deepFindUID(getAprsUid(call));

            double miles = -1;

            if (item instanceof Marker &&
                    MapView.getMapView().getSelfMarker() != null) {

                Marker marker = (Marker) item;

                double meters = MapView.getMapView()
                        .getSelfMarker()
                        .getPoint()
                        .distanceTo(marker.getPoint());

                miles = meters * 0.000621371;
            }

            String age = "?";

            Long heard = lastHeard.get(call);

            if (heard != null) {

                long ageMinutes = (now - heard) / 60000;

                long hrs = ageMinutes / 60;
                long mins = ageMinutes % 60;

                if (hrs > 0)
                    age = hrs + " hr " + mins + " min ago";
                else
                    age = mins + " min ago";
            }

            SpannableStringBuilder text = new SpannableStringBuilder();

            int start;
            int end;

            /* ---------- CALLSIGN ---------- */

            start = text.length();

            text.append(call);

            end = text.length();

            text.setSpan(
                    new ForegroundColorSpan(Color.CYAN),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            text.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            /* ---------- DISTANCE ---------- */

            if (miles >= 0) {

                start = text.length();

                text.append(String.format("    %.1f mi", miles));

                end = text.length();

                text.setSpan(
                        new ForegroundColorSpan(Color.WHITE),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            text.append("\n");

            /* ---------- LAST HEARD ---------- */

            start = text.length();

            text.append(age);

            end = text.length();

            text.setSpan(
                    new ForegroundColorSpan(Color.LTGRAY),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            /* ---------- COMMENT ---------- */

            String comment = stationComments.get(call);

            if (comment != null && !comment.isEmpty()) {

                text.append(" • ");

                start = text.length();

                text.append(comment);

                end = text.length();

                text.setSpan(
                        new ForegroundColorSpan(Color.GREEN),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            TextView row = new TextView(pluginContext);

            row.setText(text);

            row.setTextSize(16);

            row.setPadding(12, 10, 12, 10);

            row.setClickable(true);

            row.setOnClickListener(v -> {

                MapItem cotItem = MapView.getMapView()
                        .getRootGroup()
                        .deepFindUID(getAprsUid(call));

                if (cotItem instanceof Marker) {

                    MapView.getMapView()
                            .getMapController()
                            .panTo(((Marker) cotItem).getPoint(), true);
                }

                showStationDetails(call);

            });

            aprsList.addView(row);

            View divider = new View(pluginContext);

            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1));

            divider.setBackgroundColor(Color.DKGRAY);

            aprsList.addView(divider);
        }
    }

    private void removeStaleStations() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastHeard.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();

            if (now - entry.getValue() > staleMillis) {
                String call = entry.getKey();

                MapItem item = MapView.getMapView()
                        .getRootGroup()
                        .deepFindUID(getAprsUid(call));

                if (item != null && item.getGroup() != null) {
                    item.getGroup().removeItem(item);
                }

                geoChatBridge.forgetAprsContact(call);

                stationComments.remove(call);
                stationAltitude.remove(call);
                stationTemperature.remove(call);
                stationWind.remove(call);
                stationBarometer.remove(call);
                stationHumidity.remove(call);
                stationCourse.remove(call);
                stationSpeed.remove(call);
                stationRain1Hour.remove(call);
                stationRain24Hour.remove(call);
                stationRainMidnight.remove(call);

                it.remove();
            }
        }
    }

    private void promptForLocalCallsign() {

        final EditText localCallText = new EditText(pluginContext);
        localCallText.setHint("Your APRSdroid callsign, e.g. KD2VAR-7");

        new AlertDialog.Builder(MapView.getMapView().getContext())
                .setTitle("Set APRSdroid Callsign")
                .setMessage("The plugin needs your APRSdroid callsign to filter APRS messages before sending them to GeoChat.")
                .setView(localCallText)
                .setPositiveButton("Save", (dialog, which) -> {

                    String localCall = localCallText.getText()
                            .toString()
                            .trim()
                            .toUpperCase();

                    if (!localCall.isEmpty() && messageManager != null) {
                        messageManager.setLocalCallsign(localCall);
                    }

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getAprsUid(String callsign) {
        return "aprs." + callsign;
    }
}

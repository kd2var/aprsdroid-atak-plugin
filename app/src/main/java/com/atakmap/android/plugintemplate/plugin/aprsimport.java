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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

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

    private BroadcastReceiver aprsReceiver;

    // Station data
    private final HashMap<String, Long> lastHeard = new HashMap<>();
    private final HashMap<String, String> stationComments = new HashMap<>();
    private final HashMap<String, String> stationAltitude = new HashMap<>();
    private final HashMap<String, String> stationTemperature = new HashMap<>();
    private final HashMap<String, String> stationWind = new HashMap<>();
    private final HashMap<String, String> stationBarometer = new HashMap<>();
    private final HashMap<String, String> stationHumidity = new HashMap<>();
    private final HashMap<String, String> stationCourse = new HashMap<>();
    private final HashMap<String, String> stationSpeed = new HashMap<>();
    private final HashMap<String, String> aprsSymbols = new HashMap<>();

    private final CotDispatcher cotDispatcher = com.atakmap.android.cot.CotMapComponent.getInternalDispatcher();

    private static final String APRS_ICONSET_UID = "9c9f2b11-0d60-4f6c-a001-aprsiconset";
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
        filter.addAction("org.aprsdroid.app.HUD");

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

        String aprsSymbol = getSymbol(intent, packet);
        if (aprsSymbol == null || aprsSymbol.length() < 2) aprsSymbol = "/>";

        String rawComment = extractComment(intent, packet);
        String cleanedComment = cleanAprsComment(callsign, rawComment);

        stationComments.put(callsign, cleanedComment);
        lastHeard.put(callsign, System.currentTimeMillis());

        if (loc != null) {
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

    private String getSymbol(Intent intent, String packet) {
        if ("org.aprsdroid.app.HUD".equals(intent.getAction())) {
            String sym = intent.getStringExtra("org.aprsdroid.app.SYMBOL");
            if (sym != null && !sym.isEmpty()) {
                aprsSymbols.put(getCallsign(intent), sym);
                return sym;
            }
        }
        String callsign = getCallsign(intent);
        if (callsign != null && aprsSymbols.containsKey(callsign)) {
            return aprsSymbols.get(callsign);
        }
        return getAprsSymbol(packet);
    }

    private String extractComment(Intent intent, String packet) {
        if ("org.aprsdroid.app.HUD".equals(intent.getAction())) {
            String c = intent.getStringExtra("org.aprsdroid.app.COMMENT");
            return c != null ? c : "";
        }

        if (packet != null) {
            int pos = packet.indexOf(":=");
            if (pos < 0) pos = packet.indexOf(":!");
            if (pos >= 0) {
                String body = packet.substring(pos + 2);
                int slashA = body.indexOf("/A=");
                if (slashA >= 0) {
                    int space = body.indexOf(' ', slashA);
                    if (space > 0 && space < body.length() - 1) {
                        return body.substring(space + 1).trim();
                    }
                } else if (body.length() > 19) {
                    return body.substring(19).trim();
                }
            }
        }
        return "";
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
                    + "<event version=\"2.0\" uid=\"APRS-" + callsign + "\" type=\"a-f-G-U-C\" "
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

            UserIcon icon = new UserIcon(0, APRS_ICONSET_UID, group, fileName, cotType, bmp, 48);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);
            db.addIcon(icon, baos.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "Failed to install icon " + fileName, e);
        }
    }

    private String buildAprsIconPath(String aprsSymbol) {
        if (aprsSymbol == null || aprsSymbol.length() != 2) aprsSymbol = "/>";
        int index = getAprsIndex(aprsSymbol);
        if (index < 0) index = 0;

        String table = aprsSymbol.startsWith("\\") ? "table1" : "table0";
        return APRS_ICONSET_UID + "/" + table + "/symbol_" + String.format("%02d", index) + ".png";
    }

    private int getAprsIndex(String symbol) {
        if (symbol == null || symbol.length() != 2) return -1;
        symbol = normalizeAprsSymbol(symbol);
        return symbol.charAt(1) - 33;
    }

    private String normalizeAprsSymbol(String symbol) {
        if (symbol == null || symbol.length() != 2) return "/>";
        if (symbol.charAt(0) == 'I' && symbol.charAt(1) == '#') return "/#";
        return symbol;
    }

    private String getAprsSymbol(String packet) {
        if (packet == null) return null;
        int pos = packet.indexOf(':');
        if (pos < 0) return null;
        String body = packet.substring(pos + 1);
        if (body.length() < 20) return null;

        if (body.startsWith("@") || body.startsWith("/")) {
            return "" + body.charAt(17) + body.charAt(27);
        }
        if (body.startsWith("!") || body.startsWith("=")) {
            return "" + body.charAt(9) + body.charAt(19);
        }
        return null;
    }

    private String cleanAprsComment(String callsign, String comment) {
        if (comment == null) return "";
        comment = comment.trim();

        Matcher alt = Pattern.compile("/?A=(-?\\d{3,6})").matcher(comment);
        if (alt.find()) {
            stationAltitude.put(callsign, alt.group(1));
            comment = alt.replaceAll("").trim();
        }

        Matcher temp = Pattern.compile("t(\\d{3})").matcher(comment);
        if (temp.find()) stationTemperature.put(callsign, temp.group(1));

        Matcher wind = Pattern.compile("(\\d{3})/(\\d{3})g(\\d{3})").matcher(comment);
        if (wind.find()) stationWind.put(callsign, wind.group(1) + "/" + wind.group(2) + "g" + wind.group(3));

        Matcher baro = Pattern.compile("b(\\d{5})").matcher(comment);
        if (baro.find()) stationBarometer.put(callsign, String.format("%.1f", Integer.parseInt(baro.group(1)) / 10.0));

        Matcher hum = Pattern.compile("h(\\d{2,3})").matcher(comment);
        if (hum.find()) stationHumidity.put(callsign, hum.group(1));

        Matcher cs = Pattern.compile(">(\\d{1,3})/(\\d{1,3})").matcher(comment);
        if (cs.find()) {
            stationCourse.put(callsign, cs.group(1));
            stationSpeed.put(callsign, cs.group(2));
        }

        comment = comment.replaceAll("^\\d{3}/\\d{3}g.{0,40}?b\\d{5}", "").trim();
        comment = comment.replaceFirst("^PHG\\d{4,5}/?", "");
        comment = comment.replaceFirst("^RNG\\d{4}/?", "");
        comment = comment.replaceFirst("^DFS\\d{4}/?", "");

        if (comment.matches("^[^A-Za-z0-9]{1,4}$")) return "";

        return comment.trim();
    }

    private void showPane() {
        if (templatePane == null) {
            paneView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
            aprsList = paneView.findViewById(R.id.aprsList);

            Button staleMinus = paneView.findViewById(R.id.staleMinus);
            Button stalePlus = paneView.findViewById(R.id.stalePlus);
            TextView staleHoursText = paneView.findViewById(R.id.staleHoursText);

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

    private void refreshAprsPane() {
        if (aprsList == null) return;

        aprsList.removeAllViews();
        long now = System.currentTimeMillis();

        List<String> stations = new ArrayList<>(stationComments.keySet());
        stations.sort((a, b) -> Long.compare(lastHeard.getOrDefault(b, 0L), lastHeard.getOrDefault(a, 0L)));

        for (String call : stations) {
            Long heard = lastHeard.get(call);
            String age = "?";
            if (heard != null) {
                long minutes = (now - heard) / 60000;
                age = minutes < 60 ? minutes + "m" : (minutes / 60) + "h";
            }

            TextView row = new TextView(pluginContext);
            StringBuilder text = new StringBuilder();
            text.append(call).append("   ").append(age).append(" ago\n");

            MapItem item = MapView.getMapView().getRootGroup().deepFindUID("APRS-" + call);
            if (item instanceof Marker && MapView.getMapView().getSelfMarker() != null) {
                Marker marker = (Marker) item;
                double meters = MapView.getMapView().getSelfMarker().getPoint().distanceTo(marker.getPoint());
                double miles = meters * 0.000621371;
                text.append(String.format("%.1f mi\n", miles));
            }

            String comment = stationComments.get(call);
            if (comment != null && !comment.isEmpty()) text.append(comment);

            addIfNotEmpty(text, stationAltitude.get(call), "\nAltitude: ", " ft");
            addIfNotEmpty(text, stationTemperature.get(call), "\nTemp: ", "°F");
            addIfNotEmpty(text, stationWind.get(call), "\nWind: ", "");
            addIfNotEmpty(text, stationBarometer.get(call), "\nBaro: ", " mb");
            addIfNotEmpty(text, stationHumidity.get(call), "\nHumidity: ", "%");
            addIfNotEmpty(text, stationCourse.get(call), "\nCourse: ", "°");
            addIfNotEmpty(text, stationSpeed.get(call), "\nSpeed: ", " mph");

            row.setText(text.toString());
            row.setTextSize(16);
            row.setPadding(8, 8, 8, 16);
            row.setClickable(true);

            row.setOnClickListener(v -> {
                MapItem cotItem = MapView.getMapView().getRootGroup().deepFindUID("APRS-" + call);
                if (cotItem instanceof Marker) {
                    MapView.getMapView().getMapController().panTo(((Marker) cotItem).getPoint(), true);
                }
            });

            aprsList.addView(row);
        }
    }

    private void removeStaleStations() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = lastHeard.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > staleMillis) {
                String call = entry.getKey();
                stationComments.remove(call);
                stationAltitude.remove(call);
                stationTemperature.remove(call);
                stationWind.remove(call);
                stationBarometer.remove(call);
                stationHumidity.remove(call);
                stationCourse.remove(call);
                stationSpeed.remove(call);
                it.remove();
            }
        }
    }
}
package com.atakmap.android.plugintemplate.plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Intent;

public class AprsPacketParser {

    public enum PacketType {
        POSITION,
        WEATHER,
        OBJECT,
        ITEM,
        MESSAGE,
        STATUS,
        UNKNOWN
    }

    public static class ParsedData {

        public PacketType type = PacketType.UNKNOWN;

        public String comment = "";
        public String symbol;
        public String course;
        public String speed;

        public String windDirection;
        public String windSpeed;
        public String windGust;

        public String temperature;
        public String humidity;
        public String barometer;
        public String altitude;
        public String rain1Hour;
        public String rain24Hour;
        public String rainSinceMidnight;

    }

    public static ParsedData parse(String packet, String comment, String symbolHint) {

        ParsedData d = new ParsedData();

        d.comment = comment == null ? "" : comment;

        d.type = getPacketType(packet);

        d.symbol = getAprsSymbol(packet);

        if ((d.symbol == null || d.symbol.length() < 2)
                && symbolHint != null
                && symbolHint.length() >= 2) {
            d.symbol = symbolHint;
        }

        if (d.symbol != null && d.symbol.length() >= 2) {
            d.symbol = normalizeAprsSymbol(d.symbol);
        }

        if (d.type == PacketType.UNKNOWN || d.type == PacketType.POSITION) {
            if (d.comment.matches("^\\d{3}/\\d{3}g\\d{3}.*")) {
                d.type = PacketType.WEATHER;
            }
        }

        if (d.type == PacketType.WEATHER) {

            Matcher wind =
                    Pattern.compile("^(\\d{3})/(\\d{3})(?:g(\\d{3}))?")
                            .matcher(d.comment);

            if (wind.find()) {

                d.windDirection = wind.group(1);
                d.windSpeed = wind.group(2);
                d.windGust = wind.group(3);

                d.comment = d.comment.substring(wind.end()).trim();
            }

        } else {

            Matcher cs =
                    Pattern.compile("^(\\d{3})/(\\d{3})")
                            .matcher(d.comment);

            if (cs.find()) {

                d.course = cs.group(1);
                d.speed = cs.group(2);

                d.comment = d.comment.substring(cs.end()).trim();
            }

        }

        Matcher temp = Pattern.compile("t(\\d{3})").matcher(d.comment);
        if (temp.find()) {
            d.temperature = temp.group(1);
            d.comment = temp.replaceFirst("");
        }

        Matcher rain1 = Pattern.compile("r(\\d{3})").matcher(d.comment);
        if (rain1.find()) {
            d.rain1Hour = rain1.group(1);
            d.comment = rain1.replaceFirst("");
        }

        Matcher rain24 = Pattern.compile("p(\\d{3})").matcher(d.comment);
        if (rain24.find()) {
            d.rain24Hour = rain24.group(1);
            d.comment = rain24.replaceFirst("");
        }

        Matcher rainMidnight = Pattern.compile("P(\\d{3})").matcher(d.comment);
        if (rainMidnight.find()) {
            d.rainSinceMidnight = rainMidnight.group(1);
            d.comment = rainMidnight.replaceFirst("");
        }

        // Luminosity
        Matcher luminosity = Pattern.compile("L\\d{3}").matcher(d.comment);
        if (luminosity.find()) {
            d.comment = luminosity.replaceFirst("");
        }

        Matcher hum = Pattern.compile("h(\\d{2,3})").matcher(d.comment);
        if (hum.find()) {
            d.humidity = hum.group(1);
            d.comment = hum.replaceFirst("");
        }

        Matcher baro = Pattern.compile("b(\\d{5})").matcher(d.comment);
        if (baro.find()) {
            d.barometer = baro.group(1);
            d.comment = baro.replaceFirst("");
        }

        Matcher alt = Pattern.compile("/?A=(-?\\d{3,6})").matcher(d.comment);
        if (alt.find()) {
            d.altitude = alt.group(1);
            d.comment = alt.replaceAll("").trim();
        }

        // Remove APRS radio/range metadata from the remaining human comment
        d.comment = d.comment.replaceFirst("^PHG\\d{4,5}/?", "");
        d.comment = d.comment.replaceFirst("^RNG\\d{4}/?", "");
        d.comment = d.comment.replaceFirst("^DFS\\d{4}/?", "");
        d.comment = d.comment.replaceAll("L\\d{3}", "");

        d.comment = d.comment.replaceAll("\\s+", " ").trim();

        return d;

    }

    public static PacketType getPacketType(String packet) {

        if (packet == null)
            return PacketType.UNKNOWN;

        int colon = packet.indexOf(':');

        if (colon < 0)
            return PacketType.UNKNOWN;

        String body = packet.substring(colon + 1);

        if (body.startsWith("_"))
            return PacketType.WEATHER;

        if (body.startsWith("!"))
            return PacketType.POSITION;

        if (body.startsWith("="))
            return PacketType.POSITION;

        if (body.startsWith("@"))
            return PacketType.POSITION;

        if (body.startsWith("/"))
            return PacketType.POSITION;

        if (body.startsWith(";"))
            return PacketType.OBJECT;

        if (body.startsWith(")"))
            return PacketType.ITEM;

        if (body.startsWith(":"))
            return PacketType.MESSAGE;

        if (body.startsWith(">"))
            return PacketType.STATUS;

        return PacketType.UNKNOWN;

    }

    public static String getAprsSymbol(String packet) {
        if (packet == null) return null;

        int pos = packet.indexOf(':');
        if (pos < 0) return null;

        String body = packet.substring(pos + 1);
        if (body.length() < 20) return null;

        // Timestamped position: @DDHHMMz... or /DDHHMMz...
        if (body.startsWith("@") || body.startsWith("/")) {
            if (body.length() > 26) {
                return "" + body.charAt(16) + body.charAt(26);
            }
        }

        // Non-timestamped position: !lat/symbol/lon/symbol or =lat/symbol/lon/symbol
        if (body.startsWith("!") || body.startsWith("=")) {
            if (body.length() > 19) {
                return "" + body.charAt(9) + body.charAt(19);
            }
        }

        return null;
    }

    public static String normalizeAprsSymbol(String symbol) {
        if (symbol == null || symbol.length() != 2) return "/>";

        char table = symbol.charAt(0);
        char code = symbol.charAt(1);

        if (table == '/' || table == '\\') {
            return symbol;
        }

        return "\\" + code;
    }

    public static String extractComment(Intent intent, String packet) {

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

}
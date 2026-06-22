# APRS Import Plugin for ATAK

APRS Import is an ATAK plugin that imports APRS station data from APRSdroid and displays APRS stations as ATAK CoT map objects.

This plugin was developed and tested primarily with NA7Q's fork of APRSdroid.

## Features

* Imports APRS position broadcasts from APRSdroid into ATAK
* Displays APRS stations as ATAK map markers
* APRS symbol/icon support, including primary and alternate symbol tables
* APRS overlay symbol handling
* Station list with sorting by recent activity or distance
* Configurable stale-time cleanup
* Station details popup with distance, comment, altitude, weather, course, speed, and last-heard time
* Start APRSdroid, stop APRSdroid, and send beacon controls from inside ATAK
* APRS message sending through APRSdroid raw packet support
* Incoming APRS messages appear in ATAK GeoChat
* GeoChat replies are sent back out through APRSdroid as APRS messages
* Duplicate APRS message suppression for digipeated/repeated messages
* APRS contacts are created in ATAK Contacts/GeoChat
* Contacts can be opened from ATAK’s normal Contacts interface

## Supported ATAK Version

Tested with:

* ATAK CIV 5.6.0

## Requirements

* ATAK CIV
* APRSdroid
* NA7Q’s APRSdroid fork recommended
* Android device with working ATAK/APRSdroid installation

## Basic Usage

### Radio Control Page

Open the APRS Import plugin pane.

The Radio Control page includes:

* Start APRSdroid
* Stop APRSdroid
* Send Beacon
* New Message
* APRSdroid status indicator

### View Stations Page

The View Stations page includes:

* Sort by Recent or Distance
* Stale Time selector
* APRS station list

Tap a station to:

* Pan the ATAK map to that station
* Open a station details popup

## APRS Messaging

Incoming APRS messages addressed to the local APRSdroid callsign are imported into ATAK GeoChat.

GeoChat replies are sent back to APRSdroid as APRS messages using APRSdroid’s raw packet send interface.

If APRSdroid was already running before ATAK starts, the plugin may ask for the local APRSdroid callsign so it can filter APRS messages correctly.

## Stale Time

The stale-time setting controls when stations are removed from:

* The station list
* The ATAK map

When a station exceeds the selected stale time, the plugin removes both the list entry and the map marker.

## Notes

This plugin is designed to bridge APRSdroid and ATAK. It does not replace APRSdroid’s RF, APRS-IS, or modem functionality.

APRS parsing and symbol support are implemented inside the plugin, but APRS packet transmission still depends on APRSdroid.

## Known Limitations

* Primarily tested with NA7Q’s APRSdroid fork
* Contact/radial menu integration for APRS map markers is still being refined
* APRS object/item support may require additional testing
* Message filtering depends on knowing the local APRSdroid callsign

## Build

Build the debug APK with:

```bash
./gradlew assembleCivDebug
```

## Version

Current development target:

```text
v1.3.0
```

## Credits

Built for APRS-to-ATAK integration using APRSdroid and ATAK CIV.


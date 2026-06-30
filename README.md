# APRS Import Plugin for ATAK

The APRS Import Plugin is an ATAK CIV plugin that provides two-way integration between ATAK and the enhanced NA7Q fork of APRSdroid. It imports APRS stations into ATAK as CoT map objects, synchronizes APRS messaging with ATAK GeoChat, and allows APRS RF messaging directly from within ATAK through APRSdroid.

---

# Features

## APRS Station Import

- Imports APRS position broadcasts from APRSdroid
- Displays APRS stations as ATAK map markers
- Supports APRS primary and alternate symbol tables
- Automatic station updates
- Configurable stale-time cleanup
- Station sorting by distance or most recent activity
- Detailed station information including:
  - Callsign
  - Distance
  - Bearing
  - Course
  - Speed
  - Altitude
  - Weather
  - Comment
  - Last Heard

---

## APRS Messaging

- Incoming APRS messages are imported into ATAK GeoChat
- APRS stations automatically appear as ATAK Contacts
- Conversations can be opened directly from ATAK Contacts
- Replies sent from ATAK GeoChat are transmitted as APRS messages through APRSdroid
- Duplicate APRS message suppression
- Proper unread message handling
- Native ATAK GeoChat integration

---

## Radio Controls

The plugin can control APRSdroid directly from ATAK.

Functions include:

- Start APRSdroid
- Stop APRSdroid
- Send Beacon
- New APRS Message
- APRSdroid status indicator

---

# Compatibility

This plugin is currently supported on:

- ATAK CIV 5.6.x

The plugin was developed and tested using:

- Enhanced NA7Q APRSdroid fork

ATAK 5.7 is **not currently supported**.

---

# Requirements

- ATAK CIV 5.6.x
- Enhanced NA7Q APRSdroid fork
- Android device
- Working APRSdroid configuration

---

# Important

Version 1.3.3 works with the enhanced NA7Q fork of APRSdroid using its
documented `SEND_PACKET` API.

Standard APRS RF messaging works with the current APRSdroid release.

However, APRSdroid currently creates packets sent through the
`SEND_PACKET` API with a `TCPIP*` path instead of using the user's
configured digipeater path. Because of this, packets sent from ATAK may
not be digipeated over RF. Messaging to services such as WXBOT and other
stations that depend on digipeating may therefore not function as
expected.

A pull request has been submitted to NA7Q's APRSdroid project to correct
this behavior by making `SEND_PACKET` honor the configured digipeater
path. The change is fully backward compatible and affects only the
behavior of the documented API.

Until that change is merged, users who require reliable digipeated RF
messaging should use an APRSdroid build that includes the pending
`SEND_PACKET` fix.

---

# Basic Usage

## Radio Control

Open the APRS Import plugin.

Available controls:

- Start APRSdroid
- Stop APRSdroid
- Send Beacon
- New Message

---

## Viewing Stations

Open the **Stations** page.

Available functions:

- Sort by Distance
- Sort by Recent Activity
- Select stale timeout

Selecting a station will:

- Center the ATAK map
- Display station details

---

## APRS Messaging

Incoming APRS messages automatically appear inside ATAK GeoChat.

Replies sent from ATAK GeoChat are transmitted back through APRSdroid as
standard APRS messages.

If APRSdroid is already running when ATAK starts, the plugin may ask for
the local APRS callsign so incoming messages can be filtered correctly.

---

# Known Limitations

- Tested with ATAK CIV 5.6.x
- Developed for the enhanced NA7Q fork of APRSdroid
- APRS overlay symbols are not currently supported
- Some APRS Objects and Items require additional field testing
- APRSdroid performs all RF modem and APRS-IS functions

---

# Building

Debug build:

```bash
./gradlew assembleCivDebug
```

Release build:

```bash
./gradlew assembleCivRelease
```

---

# Current Version

v1.3.3

---

# Credits

Developed by Scott (KD2VAR)

Built using:

- ATAK CIV Plugin SDK
- Enhanced NA7Q APRSdroid fork
- APRS Parser (AB0OO)

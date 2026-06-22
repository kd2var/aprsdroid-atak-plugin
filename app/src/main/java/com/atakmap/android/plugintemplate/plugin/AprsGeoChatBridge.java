package com.atakmap.android.plugintemplate.plugin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.atakmap.android.chat.ChatDatabase;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import gov.tak.api.cot.CoordinatedTime;

public class AprsGeoChatBridge {

    private static final String TAG = "APRSGEOCHAT";

    private final Context context;
    private final ChatDatabase chatDb;
    private final Map<String, IndividualContact> aprsContacts = new HashMap<>();

    public AprsGeoChatBridge(Context context) {
        this.context = context;
        this.chatDb = ChatDatabase.getInstance(null);
    }

    public synchronized IndividualContact getOrCreateAprsContact(String callsign) {
        if (callsign == null || callsign.trim().isEmpty())
            return null;

        callsign = callsign.trim().toUpperCase();

        IndividualContact contact = aprsContacts.get(callsign);

        if (contact == null) {
            contact = new AprsContact(callsign);
            aprsContacts.put(callsign, contact);
            Contacts.getInstance().addContact(contact);
        }

        return contact;
    }

    public void receiveAprsMessage(String source, String dest, String body) {

        IndividualContact contact = getOrCreateAprsContact(source);

        if (contact == null)
            return;

        String selfUid = MapView.getMapView().getSelfMarker().getUID();
        String messageId = UUID.randomUUID().toString();

        CoordinatedTime now = new CoordinatedTime();

        Bundle messageBundle = new Bundle();
        messageBundle.putString("conversationId", contact.getUid());
        messageBundle.putString("messageId", messageId);
        messageBundle.putStringArray("destinations", new String[]{selfUid});
        messageBundle.putString("parent", "RootContactGroup");
        messageBundle.putString("status", "NONE");
        messageBundle.putString("conversationName", contact.getName());
        messageBundle.putString("uid", selfUid);
        messageBundle.putString("senderUid", contact.getUid());
        messageBundle.putString("message", body);
        messageBundle.putLong("sentTime", now.getMilliseconds());
        messageBundle.putString("senderCallsign", contact.getTitle());

        Log.d(TAG, "GeoChat contact uid=[" + contact.getUid()
                + "] name=[" + contact.getName()
                + "] title=[" + contact.getTitle()
                + "] messageBundleConversationId=["
                + messageBundle.getString("conversationId") + "] senderUid=["
                + messageBundle.getString("senderUid") + "] uid=["
                + messageBundle.getString("uid") + "]");

        chatDb.addChat(messageBundle);

        int unread = contact.getExtras().getInt("unreadMessageCount", 0);
        contact.getExtras().putInt("unreadMessageCount", unread + 1);
        Contacts.getInstance().updateTotalUnreadCount();

        Intent gotNewChat = new Intent();
        gotNewChat.setAction("com.atakmap.android.chat.NEW_CHAT_MESSAGE");
        gotNewChat.putExtra("id", messageBundle.getLong("id"));
        gotNewChat.putExtra("groupId", messageBundle.getLong("groupId"));
        gotNewChat.putExtra("conversationId", messageBundle.getString("conversationId"));

        AtakBroadcast.getInstance().sendBroadcast(gotNewChat);
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ContactPresenceDropdown.REFRESH_LIST));

        Log.d(TAG, "Added APRS message to GeoChat source=["
                + source + "] body=[" + body + "]");
    }

    public void sendAprsReply(String destination, String body) {
        Log.d(TAG, "GeoChat send placeholder dest=["
                + destination + "] body=[" + body + "]");
    }
}
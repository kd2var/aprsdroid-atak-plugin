package com.atakmap.android.plugintemplate.plugin;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactConnectorManager;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Objects;

public class AprsConnectorHandler extends
        ContactConnectorManager.ContactConnectorHandler {

    private static final String TAG = "AprsConnectorHandler";

    @Override
    public boolean isSupported(String type) {
        return Objects.equals(
                AprsChatConnector.PLUGIN_CONNECTOR.getConnectionType(),
                type);
    }

    @Override
    public boolean hasFeature(ContactConnectorManager.ConnectorFeature feature) {
        return feature == ContactConnectorManager.ConnectorFeature.NotificationCount;
    }

    @Override
    public String getName() {
        return AprsChatConnector.CONNECTOR_TYPE;
    }

    @Override
    public String getDescription() {
        return "Connects APRS messages with ATAK Contacts and Chat";
    }

    @Override
    public Object getFeature(String connectorType,
                             ContactConnectorManager.ConnectorFeature feature,
                             String contactUID,
                             String connectorAddress) {

        if (feature == ContactConnectorManager.ConnectorFeature.NotificationCount) {
            Contact c = Contacts.getInstance().getContactByUuid(contactUID);
            if (c != null) {
                return c.getExtras().getInt("unreadMessageCount", 0);
            }
        }

        return null;
    }

    @Override
    public boolean handleContact(String connectorType,
                                 String contactUID,
                                 String address) {

        Log.d(TAG, "handleContact connectorType=["
                + connectorType + "] contactUID=["
                + contactUID + "] address=["
                + address + "]");

        if (!AprsChatConnector.ACTION_SEND_CHAT_APRS.equals(address)
                && !AprsChatConnector.CONNECTOR_TYPE.equals(connectorType)
                && !com.atakmap.android.contact.PluginConnector.CONNECTOR_TYPE.equals(connectorType)) {
            return false;
        }

        if (!FileSystemUtils.isEmpty(contactUID)) {
            Log.d(TAG, "handleContact: " + contactUID + ", " + address);

            Contact list = Contacts.getInstance().getContactByUuid(contactUID);

            boolean editable = list == null
                    || list.getExtras().getBoolean(
                    "editable",
                    !(list instanceof GroupContact))
                    || list instanceof GroupContact
                    && !((GroupContact) list).getUnmodifiable();

            try {
                if (list != null) {
                    list.getExtras().putInt("unreadMessageCount", 0);
                }

                android.content.Intent markRead =
                        new android.content.Intent("com.atakmap.chat.markmessageread");

                markRead.putExtra("conversationId", contactUID);

                com.atakmap.android.ipc.AtakBroadcast.getInstance()
                        .sendBroadcast(markRead);

                Contacts.getInstance().updateTotalUnreadCount();

                Log.d(TAG, "Cleared APRS unread count for " + contactUID);
            } catch (Exception e) {
                Log.w(TAG, "Failed to clear APRS unread count for " + contactUID, e);
            }

            ChatManagerMapComponent.getInstance()
                    .openConversation(contactUID, editable);

            Contacts.getInstance().updateTotalUnreadCount();

            return true;
        }

        Log.w(TAG, "Unable to handleContact: " + contactUID + ", " + address);
        return false;
    }
}

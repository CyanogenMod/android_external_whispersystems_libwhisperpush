package org.whispersystems.whisperpush.api;

import android.net.Uri;
import android.util.Pair;

import java.util.List;

public interface MessagingBridge {

    Uri storeIncomingTextMessage(int subId, String sender,
                                 String message, long timestampSent, boolean read, boolean showNotification);

    void storeIncomingSecureMultimediaMessage(String sender, String message, List<Pair<String, String>> attachments,
                                              long sentTimestamp, boolean showNotification);

}

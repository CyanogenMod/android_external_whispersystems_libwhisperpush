package org.whispersystems.whisperpush.api;

import android.net.Uri;
import android.util.Pair;

import java.util.Collection;
import java.util.List;

public interface MessagingBridge {

    Uri storeIncomingTextMessage(int subId, String sender,
                                 String message, long timestampSent, boolean read, boolean showNotification);

    void storeIncomingMultimediaMessage(String sender, String message, List<Pair<String, String>> attachments,
                                        long sentTimestamp, boolean showNotification);

    void storeIncomingGroupMessage(String sender, String message, List<Pair<String, String>> attachments,
                                   long sentTimestamp, boolean showNotification,
                                   long threadId);

    void updateMessageGroup(byte[] groupId, Collection<String> members);

    long getThreadId(byte[] groupId);

}

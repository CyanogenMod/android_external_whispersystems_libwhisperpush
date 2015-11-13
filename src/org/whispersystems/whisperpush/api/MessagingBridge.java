package org.whispersystems.whisperpush.api;

import android.util.Pair;

import java.util.Collection;
import java.util.List;

public interface MessagingBridge {

    boolean isAddressBlacklisted(String address);

    void storeIncomingTextMessage(String sender, String message,
                                 long sentAt, boolean read, boolean showNotification);

    void storeIncomingMultimediaMessage(String sender, String message,
                                        List<Pair<String, String>> attachments,
                                        long sentAt, boolean showNotification);

    void storeIncomingGroupMessage(String sender, String message,
                                   List<Pair<String, String>> attachments,
                                   long sentAt, long threadId, boolean showNotification);

    void updateGroupMembers(byte[] groupId, Collection<String> members);

    void quitMemberFromGroup(byte[] groupId, String member);

    long getGroupThreadId(byte[] groupId);

}

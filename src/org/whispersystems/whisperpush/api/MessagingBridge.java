package org.whispersystems.whisperpush.api;

import android.util.Pair;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MessagingBridge {

    boolean isAddressBlacklisted(String address);

    void storeIncomingTextMessage(String sender, String message,
                                 long sentAt, boolean read);

    void storeIncomingMultimediaMessage(String sender, String message,
                                        List<Pair<byte[], byte[]>> attachments,
                                        long sentAt);

    void storeIncomingGroupMessage(String sender, String message,
                                   List<Pair<byte[], byte[]>> attachments,
                                   long sentAt, long threadId);

    long getThreadId(Set<String> recipients);

    Set<String> getRecipientsByThread(final long threadId);

}

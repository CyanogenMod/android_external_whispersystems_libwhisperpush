package org.whispersystems.whisperpush.api;

import org.whispersystems.textsecure.internal.util.Hex;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class MessageGroup {

    private final String groupId;
    private final long threadId;

    public MessageGroup(byte[] groupId, long threadId) {
        this.groupId = toStringId(groupId);
        this.threadId = threadId;
    }

    public MessageGroup(String groupId, long threadId) {
        this.groupId = groupId;
        this.threadId = threadId;
    }

    public String getGroupId() {
        return groupId;
    }

    public byte[] getGroupIdBytes() {
        return toBytesId(groupId);
    }

    public long getThreadId() {
        return threadId;
    }

    public static String toStringId(byte[] groupId) {
        return Hex.toStringCondensed(groupId);
    }

    public static byte[] toBytesId(String groupId) {
        try {
            return Hex.fromStringCondensed(groupId);
        } catch (IOException e) {
            throw new RuntimeException("Wrong groupId format, " + groupId, e);
        }
    }

    public static byte[] generateGroupId() {
        try {
            byte[] groupId = new byte[16];
            SecureRandom.getInstance("SHA1PRNG").nextBytes(groupId);
            return groupId;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageGroup that = (MessageGroup) o;

        if (threadId != that.threadId) return false;
        return groupId.equals(that.groupId);

    }

    @Override
    public int hashCode() {
        int result = groupId.hashCode();
        result = 31 * result + (int) (threadId ^ (threadId >>> 32));
        return result;
    }
}

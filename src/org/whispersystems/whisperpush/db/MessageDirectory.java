package org.whispersystems.whisperpush.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.whispersystems.whisperpush.db.table.MessageDirectoryTable;
import org.whispersystems.whisperpush.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.IS_TRANSPORT_SECURE;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.MESSAGE_ID;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.MESSAGE_TYPE;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.TABLE_NAME;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.TIMESTAMP;

public class MessageDirectory {

    public static final int TYPE_SMS_OUTGOING = 1;
    public static final int TYPE_SMS_INCOMING = 2;
    public static final int TYPE_MMS_OUTGOING = 3;
    public static final int TYPE_MMS_INCOMING = 4;

    private static volatile MessageDirectory mInstance;

    private final WhisperPushDbHelper mDbHelper;

    public static MessageDirectory getInstance(Context context) {
        if (mInstance == null) {
            synchronized (MessageDirectory.class) {
                if (mInstance == null) {
                    mInstance = new MessageDirectory(context.getApplicationContext());
                }
            }
        }
        return mInstance;
    }

    private MessageDirectory(Context appContext) {
        mDbHelper = WhisperPushDbHelper.getInstance(appContext);
    }

    private SQLiteDatabase getWritableDatabase() {
        return mDbHelper.getWritableDatabase();
    }

    private SQLiteDatabase getReadableDatabase() {
        return mDbHelper.getReadableDatabase();
    }

    public void putMessage(Uri messageUri, boolean isTransportSecure, int messageType, long timestamp) {
        putMessage(Util.extractMessageId(messageUri), isTransportSecure, messageType, timestamp);
    }

    public void putMessage(long messageId, boolean isTransportSecure, int messageType, long timestamp) {
        if (messageId <= 0) {
            return; //TODO
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MESSAGE_ID, messageId);
        values.put(MESSAGE_TYPE, messageType);
        values.put(IS_TRANSPORT_SECURE, isTransportSecure);
        values.put(TIMESTAMP, timestamp);
        db.replaceOrThrow(TABLE_NAME, null, values);
    }

    public boolean isMessageTransportSecured(long messageId) {
        return false;
    }

    public Set<Long> getSecuredMessages(Collection<Long> messageIds) {
        return Collections.emptySet();
    }

    public List<Boolean> getSecuredMessages(List<Long> messageIds) {
        return Collections.emptyList();
    }

}

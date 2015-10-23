package org.whispersystems.whisperpush.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static android.text.TextUtils.join;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.IS_TRANSPORT_SECURE;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.MESSAGE_ID;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.MESSAGE_TYPE;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.TABLE_NAME;
import static org.whispersystems.whisperpush.db.table.MessageDirectoryTable.TIMESTAMP;
import static org.whispersystems.whisperpush.util.Util.getSize;
import static org.whispersystems.whisperpush.util.Util.isEmpty;

public class MessageDirectory {

    private static final String TAG = "MessageDirectory";

    public static final int TYPE_SMS_OUTGOING = 1;
    public static final int TYPE_SMS_INCOMING = 2;
    public static final int TYPE_MMS_OUTGOING = 3;
    public static final int TYPE_MMS_INCOMING = 4;

    private static final String[] COLUMNS_SECURE = {IS_TRANSPORT_SECURE};
    private static final String[] COLUMNS_MESSAGE_ID = {MESSAGE_ID};

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

    public void putMessage(long messageId, boolean isTransportSecure, int messageType, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(MESSAGE_ID, messageId);
        values.put(MESSAGE_TYPE, messageType);
        values.put(IS_TRANSPORT_SECURE, isTransportSecure);
        values.put(TIMESTAMP, timestamp);
        getWritableDatabase().replaceOrThrow(TABLE_NAME, null, values);
    }

    public boolean isMessageSecured(long messageId) {
        String[] whereArgs = {String.valueOf(messageId)};
        Cursor cursor = getReadableDatabase()
                .query(TABLE_NAME, COLUMNS_SECURE, MESSAGE_ID + "=?", whereArgs, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) != 0;
            }
        } finally {
            cursor.close();
        }
        return false;
    }

    public Set<Long> getSecuredMessages(Collection<Long> messageIds) {
        int size = getSize(messageIds);
        if (size > 0) {
            Set<Long> result = new HashSet<Long>(size);
            getSecuredMessages(messageIds, result);
            return result;
        }
        return Collections.emptySet();
    }

    public void getSecuredMessages(Collection<Long> messageIds, Collection<Long> outSecureMessageIds) {
        if (isEmpty(messageIds)) {
            return;
        }
        String messageIdsArg = join(",", messageIds);
        Cursor cursor = getReadableDatabase()
                .query(TABLE_NAME, COLUMNS_MESSAGE_ID, MESSAGE_ID + " IN (" + messageIdsArg + ") AND " + IS_TRANSPORT_SECURE + "=1", null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    long messageId = cursor.getLong(0);
                    outSecureMessageIds.add(messageId);
                }
                while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
    }

}

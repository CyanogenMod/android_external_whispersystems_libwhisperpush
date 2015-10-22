package org.whispersystems.whisperpush.db.table;

import android.provider.BaseColumns;

public class MessageDirectoryTable {

    public static final String TABLE_NAME = "message_directory";

    public static final String ID = BaseColumns._ID;
    public static final String MESSAGE_ID = "message_id";
    public static final String MESSAGE_TYPE = "message_type";
    public static final String IS_TRANSPORT_SECURE = "is_transport_secure";
    public static final String TIMESTAMP = "timestamp";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" +
            ID + " INTEGER PRIMARY KEY," +
            MESSAGE_ID + " INTEGER UNIQUE," +
            MESSAGE_TYPE + " INTEGER," +
            IS_TRANSPORT_SECURE + " INTEGER," +
            TIMESTAMP + " INTEGER);";

    private MessageDirectoryTable() {
    }

}

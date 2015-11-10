package org.whispersystems.whisperpush.db.table;

import android.provider.BaseColumns;

public class ContactDirectoryTable {

    public static final String TABLE_NAME = "contact_directory";

    public static final String ID = BaseColumns._ID;
    public static final String NUMBER = "number";
    public static final String REGISTERED = "registered";
    public static final String RELAY = "relay";
    public static final String SUPPORTS_SMS = "supports_sms";
    public static final String TIMESTAMP = "timestamp";
    public static final String SESSION_ACTIVE = "session_active";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + "(" +
            ID + " INTEGER PRIMARY KEY, " +
            NUMBER + " TEXT UNIQUE, " +
            REGISTERED + " INTEGER, " +
            RELAY + " TEXT, " +
            SUPPORTS_SMS + " INTEGER, " +
            SESSION_ACTIVE + " INTEGER DEFAULT 0, " +
            TIMESTAMP + " INTEGER);";

    private ContactDirectoryTable() {
    }

}

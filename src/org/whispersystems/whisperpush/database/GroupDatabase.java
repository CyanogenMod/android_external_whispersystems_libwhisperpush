package org.whispersystems.whisperpush.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.whispersystems.whisperpush.api.MessageGroup;

public class GroupDatabase {

    private static final String TABLE_NAME = "message_groups";

    public static final String ID = "_id";
    public static final String GROUP_ID = "group_id";
    public static final String THREAD_ID = "thread_id";

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    ID + " INTEGER PRIMARY KEY, " +
                    GROUP_ID + " TEXT UNIQUE, " +
                    THREAD_ID + " INTEGER);";

    private final SQLiteDatabase database;

    public GroupDatabase(SQLiteOpenHelper databaseHelper) {
        this.database = databaseHelper.getWritableDatabase();
    }

    public void createOrUpdate(MessageGroup messageGroup) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GROUP_ID, messageGroup.getGroupId());
        contentValues.put(THREAD_ID, messageGroup.getThreadId());

        database.insertWithOnConflict(TABLE_NAME, null,
                contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public long getThreadId(String groupId) {
        Cursor cursor = database.query(TABLE_NAME,
                new String[] { THREAD_ID } ,
                GROUP_ID + " = ?",
                new String[] { groupId } ,
                null, null, null);
        try {
            if (cursor.moveToLast()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
            } else {
                return -1;
            }
        } finally {
            cursor.close();
        }
    }

    public String getGroupId(long threadId) {
        Cursor cursor = database.query(TABLE_NAME,
                new String[] { GROUP_ID } ,
                THREAD_ID + " = ?",
                new String[] { String.valueOf(threadId) } ,
                null, null, null);
        try {
            if (cursor.moveToLast()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(GROUP_ID));
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    public void remove(String groupId) {
        database.delete(TABLE_NAME, GROUP_ID + " = ?",
                new String[] { groupId } );
    }

    public static void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }
}

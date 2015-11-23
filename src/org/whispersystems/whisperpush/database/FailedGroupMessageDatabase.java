package org.whispersystems.whisperpush.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Collection;
import java.util.HashSet;

public class FailedGroupMessageDatabase {

    private static final String TABLE_NAME = "failed_group_message";

    public static final String ID = "_id";
    public static final String MESSAGE_ID = "message_id";
    public static final String RECIPIENT = "recipient";

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    ID + " INTEGER PRIMARY KEY, " +
                    MESSAGE_ID + " TEXT , " +
                    RECIPIENT + " TEXT, UNIQUE(" +
                    MESSAGE_ID + ", " + RECIPIENT +
                    "));";

    private static final String WHERE_MESSAGE_ID = MESSAGE_ID + " = ?";

    private final SQLiteDatabase database;

    FailedGroupMessageDatabase(SQLiteOpenHelper databaseHelper) {
        this.database = databaseHelper.getWritableDatabase();
    }

    public void insertOrUpdateFailedMessage(String messageId, Collection<String> recipients) {
        ContentValues values = new ContentValues();
        values.put(MESSAGE_ID, messageId);

        try {
            database.beginTransaction();
            for (String recipient : recipients) {
                values.put(RECIPIENT, recipient);
                database.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

    }

    public void clearMessageFails(String messageId) {
        database.delete(TABLE_NAME, WHERE_MESSAGE_ID,
                new String[]{messageId});
    }

    public Collection<String> getFailedRecipients(String messageId) {
        Collection<String> failedRecipients = new HashSet<>();
        Cursor cursor = database.query(TABLE_NAME, new String[] { RECIPIENT },
                WHERE_MESSAGE_ID, new String[] { messageId }, null, null, null);
        try {
            int recipientIndex = cursor.getColumnIndex(RECIPIENT);
            while (cursor.moveToNext()) {
                failedRecipients.add(cursor.getString(recipientIndex));
            }
        } finally {
            cursor.close();
        }

        return failedRecipients;
    }

    public static void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }
}

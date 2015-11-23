package org.whispersystems.whisperpush.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.whispersystems.whisperpush.database.CanonicalAddressDatabase;
import org.whispersystems.whisperpush.database.FailedGroupMessageDatabase;
import org.whispersystems.whisperpush.database.GroupDatabase;
import org.whispersystems.whisperpush.database.IdentityDatabase;
import org.whispersystems.whisperpush.database.PendingApprovalDatabase;
import org.whispersystems.whisperpush.db.table.ContactDirectoryTable;

public class WhisperPushDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "whisper_push.db";
    private static final int DATABASE_VERSION = 3;

    private static volatile WhisperPushDbHelper sInstance;

    public static WhisperPushDbHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (WhisperPushDbHelper.class) {
                if (sInstance == null) {
                    sInstance = new WhisperPushDbHelper(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private WhisperPushDbHelper(Context appContext) {
        super(appContext, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ContactDirectoryTable.CREATE_TABLE);
        CanonicalAddressDatabase.onCreate(db);
        IdentityDatabase.onCreate(db);
        PendingApprovalDatabase.onCreate(db);
        GroupDatabase.onCreate(db);
        FailedGroupMessageDatabase.onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            GroupDatabase.onCreate(db);
        }
        if (oldVersion < 3) {
            FailedGroupMessageDatabase.onCreate(db);
        }
    }

}

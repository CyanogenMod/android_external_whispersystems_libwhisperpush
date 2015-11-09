/**
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.whisperpush.directory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;
import android.text.TextUtils;

import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.db.WhisperPushDbHelper;
import org.whispersystems.whisperpush.db.table.ContactDirectoryTable;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.WhisperPush;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.NUMBER;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.REGISTERED;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.RELAY;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.SESSION_ACTIVE;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.SUPPORTS_SMS;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.TABLE_NAME;
import static org.whispersystems.whisperpush.db.table.ContactDirectoryTable.TIMESTAMP;
import static org.whispersystems.whisperpush.util.Util.isEmpty;

public class Directory {

  public static final int STATE_ALL_CONTACTS_SECURE = 1;
  public static final int STATE_ALL_CONTACTS_UNSECURE = 2;
  public static final int STATE_CONTACTS_MIXED = 3;

  private static final String TAG = Directory.class.getSimpleName();
  private static volatile Directory instance;

  public static Directory getInstance(Context context) {
    if (instance == null) {
      synchronized (Directory.class) {
        if (instance == null) {
          instance = new Directory(context.getApplicationContext());
        }
      }
    }

    return instance;
  }

  private final Context context;
  private final WhisperPushDbHelper databaseHelper;

  private Directory(Context context) {
    this.context = context;
    this.databaseHelper = WhisperPushDbHelper.getInstance(context);
  }

  public boolean isActiveNumber(String e164number) throws NotInDirectoryException {
    if (e164number == null || e164number.length() == 0) {
      return false;
    }
    Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME,
            new String[] { REGISTERED }, NUMBER + " = ?", new String[] { e164number },
            null, null, null);
    try {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0) == 1;
      } else {
        throw new NotInDirectoryException();
      }
    } finally {
      cursor.close();
    }
  }

  public int isAllActiveNumbers(Collection<String> numbers) {
      if (isEmpty(numbers)) {
          return STATE_ALL_CONTACTS_UNSECURE;
      }
      List<String> formattedNumbers = new ArrayList<String>();
      WhisperPush whisperPush = WhisperPush.getInstance(context);

      boolean hasWrongNumbers = false;
      for (String number : numbers) {
          try {
              String formattedNumber = whisperPush.formatNumber(number);
              formattedNumbers.add(formattedNumber);
          } catch (InvalidNumberException e) {
              Log.i(TAG, "Can't format number " + number, e);
              hasWrongNumbers = true;
          }
      }

      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      String where = REGISTERED + " = 1 AND " + NUMBER
              + " IN ('" + TextUtils.join("','", formattedNumbers) + "')";
      Cursor cursor = db.query(TABLE_NAME, new String[]{NUMBER}, where, null, null, null, null);

      int secureNumberCount = cursor.getCount();
      if (secureNumberCount == 0) {
          return STATE_ALL_CONTACTS_UNSECURE;
      } else if (hasWrongNumbers || secureNumberCount != numbers.size()) {
          return STATE_CONTACTS_MIXED;
      } else {
          return STATE_ALL_CONTACTS_SECURE;
      }
  }

  public void setActiveNumberAndRelay(String e164number, String relay) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(REGISTERED, 1);
    values.put(RELAY, relay);
    db.update(TABLE_NAME, values, NUMBER + " = ?", new String[] { e164number });
  }

  public String getRelay(String e164number) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor = database.query(TABLE_NAME, null, NUMBER + " = ?", new String[] { e164number },
            null, null, null);
    try {
      if (cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(RELAY));
      }
    } finally {
      cursor.close();
    }
    return null;
  }

  public void setNumber(ContactTokenDetails token, boolean active) {
    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    ContentValues  values = new ContentValues();
    values.put(NUMBER, token.getNumber());
    values.put(RELAY, token.getRelay());
    values.put(REGISTERED, active ? 1 : 0);
    values.put(SUPPORTS_SMS, /*token.isSupportsSms() ? 1 :*/ 0);
    values.put(TIMESTAMP, System.currentTimeMillis());
    db.replace(TABLE_NAME, null, values);
  }

  public void setNumbers(List<ContactTokenDetails> activeTokens, Collection<String> inactiveNumbers) {
    long timestamp    = System.currentTimeMillis();
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      for (ContactTokenDetails token : activeTokens) {
        Log.w("Directory", "Adding active token: " + token.getNumber() + ", " + token.getToken());
        ContentValues values = new ContentValues();
        values.put(NUMBER, token.getNumber());
        values.put(REGISTERED, 1);
        values.put(TIMESTAMP, timestamp);
        values.put(RELAY, token.getRelay());
        values.put(SUPPORTS_SMS, /*token.isSupportsSms() ? 1 :*/ 0);
        db.replace(TABLE_NAME, null, values);
      }

      for (String number : inactiveNumbers) {
        ContentValues values = new ContentValues();
        values.put(NUMBER, number);
        values.put(REGISTERED, 0);
        values.put(TIMESTAMP, timestamp);
        db.replace(TABLE_NAME, null, values);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public Set<String> getPushEligibleContactNumbers(String localNumber) {
    final Uri         uri     = Phone.CONTENT_URI;
    final Set<String> results = new HashSet<String>();
          Cursor      cursor  = null;

    try {
      cursor = context.getContentResolver().query(uri, new String[] {Phone.NUMBER}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        final String rawNumber = cursor.getString(0);
        if (rawNumber != null) {
          try {
            final String e164Number = PhoneNumberFormatter.formatNumber(rawNumber, localNumber);
            results.add(e164Number);
          } catch (InvalidNumberException e) {
            Log.w("Directory", "Invalid number: " + rawNumber);
          }
        }
      }

      if (cursor != null)
        cursor.close();

      final SQLiteDatabase readableDb = databaseHelper.getReadableDatabase();
      if (readableDb != null) {
        cursor = readableDb.query(TABLE_NAME, new String[]{NUMBER},
            null, null, null, null, null);

        while (cursor != null && cursor.moveToNext()) {
          results.add(cursor.getString(0));
        }
      }

      return results;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean hasActiveSession(String e164Number) {
    if (TextUtils.isEmpty(e164Number)) {
      return false;
    }
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor = db.query(ContactDirectoryTable.TABLE_NAME, new String[] { SESSION_ACTIVE },
            NUMBER + " = ?", new String[] { e164Number }, null, null, null);
    try {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0) == 1;
      }
    } finally {
      cursor.close();
    }
    return false;
  }

  public void setActiveSession(String number, boolean hasActiveSession) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    ContentValues values = new ContentValues(1);
    values.put(SESSION_ACTIVE, hasActiveSession ? 1 : 0);
    String where = NUMBER + "=?";
    String[] whereArgs = { number };
    db.update(ContactDirectoryTable.TABLE_NAME, values, where, whereArgs);
  }

}
/**
 * Copyright (C) 2015 The CyanogenMod Project
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
package org.whispersystems.whisperpush;

import java.io.IOException;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.api.MessagingBridge;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.directory.NotInDirectoryException;
import org.whispersystems.whisperpush.gcm.GcmHelper;
import org.whispersystems.whisperpush.service.WhisperPushMessageSender;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.util.Log;

import static org.whispersystems.whisperpush.util.Util.isEmpty;
import static org.whispersystems.whisperpush.util.Util.isRunningOnMainThread;

public class WhisperPush {

    private static final String TAG = WhisperPush.class.getSimpleName();

    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
    private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;
    private static final long UPDATE_INTERVAL = 7L * MILLIS_PER_DAY;

    public static final String UNREGISTER_ACTION =
            "org.whispersystems.whisperpush.action.WHISPERPUSH_UNREGISTERED";

    private static volatile WhisperPush mInstance;
    private static volatile MessagingBridge mMessagingBridge;

    private final Context mContext;
    private final WhisperPreferences mPreferences;
    private final Directory mContactDirectory;
    private volatile WhisperPushMessageSender mMessageSender;

    private static boolean visible = false;

    public static WhisperPush getInstance(Context context) {
        if (mInstance == null) {
            synchronized (WhisperPush.class) {
                if (mInstance == null) {
                    mInstance = new WhisperPush(context.getApplicationContext());
                }
            }
        }
        return mInstance;
    }

    private WhisperPush(Context appContext) {
        mContext = appContext;
        mPreferences = WhisperPreferences.getInstance(appContext);
        mContactDirectory = Directory.getInstance(appContext);
        onCreate();
    }

    public MessagingBridge getMessagingBridge() {
        MessagingBridge messagingBridge = mMessagingBridge;
        if (messagingBridge == null) {
            throw new IllegalStateException("Messaging Bridge is not set");
        }
        return messagingBridge;
    }

    public static void setMessagingBridge(MessagingBridge bridge) {
        mMessagingBridge = bridge;
    }

    private TextSecureAccountManager getTextSecureAccountManager() {
        return WhisperServiceFactory.createAccountManager(mContext);
    }

    public WhisperPushMessageSender getMessageSender() {
        if (mMessageSender == null) {
            synchronized (this) {
                if (mMessageSender == null) {
                    mMessageSender = WhisperPushMessageSender.getInstance(mContext);
                }
            }
        }
        return mMessageSender;
    }

    public Directory getContactDirectory() {
        return mContactDirectory;
    }

    public String getLocalNumber() {
        return mPreferences.getLocalNumber();
    }

    public String formatNumber(String number) throws InvalidNumberException {
        return PhoneNumberFormatter.formatNumber(number, getLocalNumber());
    }

    public boolean isSecureMessagingActive() {
        return mPreferences.isRegistered();
    }

    public boolean isRecipientSupportsSecureMessaging(String number, boolean allowAskServer) {
        if (allowAskServer && isRunningOnMainThread()) {
            throw new IllegalStateException("isRecipientSupportsSecureMessaging() with allowAskServer == true called on main thread.");
        }
        if (isEmpty(number)) {
            return false;
        }
        String localNumber = mPreferences.getLocalNumber();
        if (isEmpty(localNumber)) {
            return false;
        }
        String e164number;
        try {
            e164number = PhoneNumberFormatter.formatNumber(number, localNumber);
        } catch (InvalidNumberException e) {
            Log.w(TAG, e);
            return false;
        }
        try {
            return mContactDirectory.isActiveNumber(e164number);
        } catch (NotInDirectoryException e) {
            if (allowAskServer) {
                TextSecureAccountManager accountManager = getTextSecureAccountManager();
                try {
                    Optional<ContactTokenDetails> contactDetails = accountManager.getContact(e164number);
                    if (contactDetails.isPresent()) {
                        mContactDirectory.setNumber(contactDetails.get(), true);
                        return true;
                    }
                } catch (IOException ex) {
                    Log.w(TAG, "Can't get contact token details", ex);
                }
            }
            return false;
        }
    }

    private void onCreate() { //TODO fix and refactor
        long lastRegistered = WhisperPreferences.getGcmRegistrationTime(mContext);

        if(lastRegistered != -1
                && (lastRegistered + UPDATE_INTERVAL) < System.currentTimeMillis()) {
            //It has been a week, reregister
            launchGcmRegistration(mContext);
        }
    }

    private void launchGcmRegistration(final Context context) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String registrationId = GcmHelper.getRegistrationId(context);
                    if (registrationId != null) {
                        getTextSecureAccountManager().setGcmId(Optional.of(registrationId));
                    }
                    WhisperPreferences.setGcmRegistrationTime(context, System.currentTimeMillis());
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "GcmRecurringRegistration", e);
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (result) {
                    Log.i(TAG, "GcmRecurringRegistration reregistered");
                }
            }
        }.execute();
    }

    @Deprecated
    public static void activityResumed() {
        visible = true;
    }

    @Deprecated
    public static void activityPaused() {
        visible = false;
    }

    @Deprecated
    public static boolean isActivityVisible() {
        return visible;
    }
}

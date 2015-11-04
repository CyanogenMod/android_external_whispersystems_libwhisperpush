/**
 * Copyright (C) 2015 The CyanogenMod Project
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.whisperpush.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.util.List;
import java.util.Set;

public class DirectoryRefreshService extends IntentService {

    private static final String TAG = "DirectoryRefreshService";

    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_ALLOW_SHOW_TOAST = "showResultToast";

    private static final String ACTION_SYNC = "SYNC";

    public static Intent createSyncIntent(Context context) {
        return new Intent(context, DirectoryRefreshService.class)
                .putExtra(EXTRA_ACTION, ACTION_SYNC);
    }

    public static void requestSync(Context context, boolean allowShowToast) {
        context.startService(createSyncIntent(context)
                .putExtra(EXTRA_ALLOW_SHOW_TOAST, allowShowToast));
    }

    public DirectoryRefreshService() {
        super("secure-contacts-sync");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (action == null) {
            return;
        }
        if (ACTION_SYNC.equals(action)) {
            boolean allowShowToast = intent.getBooleanExtra(EXTRA_ALLOW_SHOW_TOAST, false);
            handleRefreshAction(allowShowToast);
        }
    }

    private void handleRefreshAction(boolean allowShowToast) {
        final Context context = this;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");
        wakeLock.acquire();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            Log.w(TAG, "Refreshing directory...");
            WhisperPush whisperPush = WhisperPush.getInstance(this);
            if (!whisperPush.isSecureMessagingActive()) {
                Log.w(TAG, "Unregistered. Sync aborted.");
                return;
            }
            Directory directory = whisperPush.getContactDirectory();
            String localNumber = whisperPush.getLocalNumber();
            TextSecureAccountManager manager = WhisperServiceFactory.createAccountManager(context);

            Set<String> eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
            List<ContactTokenDetails> activeTokens = manager.getContacts(eligibleContactNumbers);

            for (ContactTokenDetails activeToken : activeTokens) {
                eligibleContactNumbers.remove(activeToken.getNumber());
            }
            directory.setNumbers(activeTokens, eligibleContactNumbers);
            Log.w(TAG, "Directory refresh complete...");
            if (allowShowToast) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Secure contacts synchronized.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Contact Directory sync failed", e);
            if (allowShowToast) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Secure contacts sync failed.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        } finally {
            wakeLock.release();
        }
    }

}

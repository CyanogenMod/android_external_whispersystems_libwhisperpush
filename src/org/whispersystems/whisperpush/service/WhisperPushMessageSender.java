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
package org.whispersystems.whisperpush.service;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.api.OutgoingMessage;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.directory.NotInDirectoryException;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.IOException;
import java.util.List;

public class WhisperPushMessageSender {

    private static final String TAG = "WhisperPushMessageSender";

    private final Context context;
    private final WhisperPush whisperPush;

    private static volatile WhisperPushMessageSender instance;

    public static WhisperPushMessageSender getInstance(Context context) {
        if (instance == null) {
            synchronized (WhisperPushMessageSender.class) {
                if (instance == null) {
                    instance = new WhisperPushMessageSender(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private WhisperPushMessageSender(Context context) {
        this.context = context.getApplicationContext();
        this.whisperPush = WhisperPush.getInstance(context);
    }

    public boolean sendMessage(OutgoingMessage message) {
        Log.d(TAG, "Got outgoing message");

        List<String> destinations = message.getDestinations();
        if (Util.isEmpty(destinations)) {
            throw new IllegalArgumentException("destinations");
        }
        if (destinations.size() > 1) {
            throw new RuntimeException("Multiple destination support is not implemented yet");
        }
        String destination = destinations.get(0);

        if (!whisperPush.isRecipientSupportsSecureMessaging(destination, true)) {
            Log.w(TAG, "Not a registered user...");
            abortSendOperation(message, new UnregisteredUserException(destination, null));
            return false;
        }

        try {
            // put destination in same format as was passed by isRegistered User above
            String e164number = whisperPush.formatNumber(destination);
            TextSecureAddress address = new TextSecureAddress(e164number);
            TextSecureMessageSender sender = WhisperServiceFactory.createMessageSender(context);
            TextSecureMessage body = TextSecureMessage.newBuilder()
                    .withBody(message.getMessageBody())
                    .build();
            sender.sendMessage(address, body);

            notifySendComplete(message);
            completeSendOperation(message);
            return true;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable e) {
            Log.w(TAG, e);
            abortSendOperation(message, e);
        }
        return false;
    }

    private void completeSendOperation(OutgoingMessage message) {
        message.completeOperation();
        if (StatsUtils.isStatsActive(context)) {
            WhisperPreferences.setWasActive(context, true);
        }
    }

    private void abortSendOperation(OutgoingMessage message, Throwable ex) {
        message.abortOperation(ex);
    }

    private void notifySendComplete(OutgoingMessage candidate) {
        List<PendingIntent> sentIntents = candidate.getSentIntents();
        if (Util.isEmpty(sentIntents)) {
            Log.w(TAG, "Warning, no sent intents available!");
            return;
        }
        for (PendingIntent sentIntent : sentIntents) {
            try {
                sentIntent.send(Activity.RESULT_OK);
            } catch (PendingIntent.CanceledException e) {
                Log.w(TAG, e);
            }
        }
    }

}
/**
 * Copyright (C) 2013 The CyanogenMod Project
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
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.api.OutgoingMessage;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.directory.NotInDirectoryException;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.IOException;
import java.util.List;

public class MessageSender {

    private final Context context;
    private final Directory directory;

    private static volatile MessageSender instance;

    public static MessageSender getInstance(Context context) {
        if (instance == null) {
            synchronized (MessageSender.class) {
                if (instance == null) {
                    instance = new MessageSender(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private MessageSender(Context context) {
        this.context = context.getApplicationContext();
        this.directory = Directory.getInstance(context);
    }

    public boolean sendMessage(OutgoingMessage message) {
        Log.d("MessageSender", "Got outgoing message");

        List<String> destinations = message.getDestinations();
        if (Util.isEmpty(destinations)) {
            throw new IllegalArgumentException("destinations");
        }
        if (destinations.size() > 1) {
            throw new RuntimeException("Multiple destination support is not implemented yet");
        }
        String destination = destinations.get(0);

        if (!isRegisteredUser(destination)) {
            Log.w("MessageSender", "Not a registered user...");
            abortSendOperation(message);
            return false;
        }

        try {
            List<String>            messageParts = message.getParts();
            // put destination in same format as was passed by isRegistered User above
            String                  localNumber  = WhisperPreferences.getLocalNumber(context);
            String                  e164number   = PhoneNumberFormatter.formatNumber(destination, localNumber);
            TextSecureAddress       address      = new TextSecureAddress(e164number);
            TextSecureMessageSender sender       = WhisperServiceFactory.createMessageSender(context);
            TextSecureMessage       body         = TextSecureMessage.newBuilder()
                                                                    .withBody(TextUtils.join("", messageParts))
                                                                    .build();
            sender.sendMessage(address, body);

            notifySendComplete(message);
            completeSendOperation(message);
            return true;
        } catch (IOException e) {
            Log.w("MessageSender", e);
            abortSendOperation(message);
        } catch (InvalidNumberException e) {
            Log.w("MessageSender", e);
            abortSendOperation(message);
        } catch (UntrustedIdentityException e) {
            Log.w("MessageSender", e);
            abortSendOperation(message);
        }
        return false;
    }

    private void completeSendOperation(OutgoingMessage message) {
        message.completeOperation(Activity.RESULT_CANCELED);
        if (StatsUtils.isStatsActive(context)) {
            WhisperPreferences.setWasActive(context, true);
        }
    }

    private void abortSendOperation(OutgoingMessage message) {
        message.abortOperation();
    }

    private void notifySendComplete(OutgoingMessage candidate) {
        List<PendingIntent> sentIntents = candidate.getSentIntents();
        if (Util.isEmpty(sentIntents)) {
            Log.w("MessageSender", "Warning, no sent intents available!");
            return;
        }
        for (PendingIntent sentIntent : sentIntents) {
            try {
                sentIntent.send(Activity.RESULT_OK);
            } catch (PendingIntent.CanceledException e) {
                Log.w("MessageSender", e);
            }
        }
    }

    public boolean isRegisteredUser(String number) {
        Log.w("MessageSender", "Number to canonicalize: " + number);
        String    localNumber = WhisperPreferences.getLocalNumber(context);
        Directory directory   = Directory.getInstance(context);

        String e164number;

        try {
            e164number  = PhoneNumberFormatter.formatNumber(number, localNumber);
        } catch (InvalidNumberException e) {
            Log.w("MessageSender", e);
            return false;
        }

        if (e164number.equals(localNumber)) {
            return false;
        }

        try {
            return directory.isActiveNumber(e164number);
        } catch (NotInDirectoryException e) {
            try {
                TextSecureAccountManager      manager = WhisperServiceFactory.createAccountManager(context);
                Log.w("MessageSender", "Getting contact token for: " + e164number);
                Optional<ContactTokenDetails> details = manager.getContact(e164number);

                if (details.isPresent()) {
                    directory.setNumber(details.get(), true);
                    return true;
                } else {
                    // FIXME: figure out what to do here
                    //contactTokenDetails = new ContactTokenDetails(contactToken);
                    //directory.setToken(contactTokenDetails, false);
                    return false;
                }
            } catch (IOException e1) {
                Log.w("MessageSender", e1);
                return false;
            }
        }
    }
}
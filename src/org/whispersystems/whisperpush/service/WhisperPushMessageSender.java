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
import android.util.Log;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.SendReq;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.api.OutgoingMessage;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    public boolean sendTextMessage(OutgoingMessage message) {
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

    public void sendMultimediaMessage(SendReq message, List<TextSecureAttachment> attachments)
            throws MmsException, UntrustedIdentityException {
        send(message, attachments, null);
    }

    public void sendGroupMessage(SendReq message, List<TextSecureAttachment> attachments, byte[] id)
            throws MmsException, UntrustedIdentityException {
        send(message, attachments, new TextSecureGroup(id));
    }

    private void send(SendReq message, List<TextSecureAttachment> attachments, TextSecureGroup textSecureGroup)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(context);
        List<TextSecureAddress> recipients = getGroupRecipients(message);

        try {
            String body = getMessageText(message.getBody());
            TextSecureMessage.Builder builder = TextSecureMessage.newBuilder()
                    .withBody(body)
                    .withAttachments(attachments);
            if (textSecureGroup != null) {
                builder.asGroupMessage(textSecureGroup);
            }

            messageSender.sendMessage(recipients, builder.build());
        } catch (IOException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            throw new MmsException(eex);
        }

    }

    public void sendGroupUpdate(byte[] id, Collection<String> members)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(context);
        List<TextSecureAddress> recipients = new ArrayList<>(members.size());

        for (String destination : members) {
            String e164number;
            try {
                e164number = whisperPush.formatNumber(destination);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw new MmsException(e);
            }
            recipients.add(new TextSecureAddress(e164number));
        }
        members.add(whisperPush.getLocalNumber());

        try {
            TextSecureGroup textSecureGroup = new TextSecureGroup(
                    TextSecureGroup.Type.UPDATE,
                    id, null,
                    new ArrayList<>(members),
                    null);

            messageSender.sendMessage(recipients,
                    TextSecureMessage.newBuilder().asGroupMessage(textSecureGroup).build());

        } catch (IOException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            throw new MmsException(eex);
        }
    }

    private List<TextSecureAddress> getGroupRecipients(SendReq message) throws MmsException {
        EncodedStringValue[] destinations = message.getTo();
        EncodedStringValue[] values = message.getBcc();
        List<EncodedStringValue> destinationsToSkip = values != null ? Arrays.asList(values) : null;
        List<TextSecureAddress> recipients = new ArrayList<>(destinations.length);

        for (EncodedStringValue destination : destinations) {
            if (destinationsToSkip != null) {
                boolean shouldContinue = false;
                for (EncodedStringValue value : destinationsToSkip) {
                    if (value.getString().equals(destination.getString())) {
                        shouldContinue = true;
                        break;
                    }
                }
                if (shouldContinue) {
                    continue;
                }
            }

            String e164number;
            try {
                e164number = whisperPush.formatNumber(destination.getString());
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw new MmsException(e);
            }
            boolean recipientSupportsSecureMessaging = whisperPush.isRecipientSupportsSecureMessaging(e164number, true);
            if (!recipientSupportsSecureMessaging) {
                throw new MmsException("Recipient " + e164number + " doesn't support secure messaging");
            }
            recipients.add(new TextSecureAddress(e164number));
        }

        return recipients;
    }

    private String getMessageText(PduBody body) {
        String bodyText = null;

        for (int i = 0; i < body.getPartsNum(); i++) {
            if (ContentType.TEXT_PLAIN.equals(Util.toIsoString(body.getPart(i).getContentType()))) {
                String partText;

                try {
                    String characterSet = CharacterSets.getMimeName(body.getPart(i).getCharset());

                    if (characterSet.equals(CharacterSets.MIMENAME_ANY_CHARSET))
                        characterSet = CharacterSets.MIMENAME_ISO_8859_1;

                    if (body.getPart(i).getData() != null) {
                        partText = new String(body.getPart(i).getData(), characterSet);
                    } else {
                        partText = "";
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.w("PartParser", e);
                    partText = "Unsupported Encoding!";
                }

                bodyText = (bodyText == null) ? partText : bodyText + " " + partText;
            }
        }

        return bodyText;
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
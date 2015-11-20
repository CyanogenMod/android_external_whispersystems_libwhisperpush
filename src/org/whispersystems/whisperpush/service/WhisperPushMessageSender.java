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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.SendReq;

import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.api.MessageGroup;
import org.whispersystems.whisperpush.api.MessagingBridge;
import org.whispersystems.whisperpush.api.OutgoingMessage;
import org.whispersystems.whisperpush.database.DatabaseFactory;
import org.whispersystems.whisperpush.database.GroupDatabase;
import org.whispersystems.whisperpush.database.WPAxolotlStore;
import org.whispersystems.whisperpush.database.WPIdentityKeyStore;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
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

    private void checkAndHandleIdentityChange(EncapsulatedExceptions e) {
        for (UntrustedIdentityException ex : e.getUntrustedIdentityExceptions()) {
            checkAndHandleIdentityChange(ex);
        }
    }

    private void checkAndHandleIdentityChange(Throwable e) {
        if (e instanceof UntrustedIdentityException) {
            UntrustedIdentityException ex = ((UntrustedIdentityException) e);
            String number = ex.getE164Number();
            MessageNotifier.notifyIdentityChanged(context, number);
            WPIdentityKeyStore identityKeyStore = WPAxolotlStore.getInstance(context).getIdentityKeyStore();
            identityKeyStore.deleteIdentity(number);
        }
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
            TextSecureDataMessage body = TextSecureDataMessage.newBuilder()
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
            checkAndHandleIdentityChange(e);
            abortSendOperation(message, e);
        }
        return false;
    }

    public void sendMultimediaMessage(List<String> recipients,
                                      List<Pair<String, Uri>> attachments,
                                      String body)
            throws UntrustedIdentityException, EncapsulatedExceptions,
            IOException, InvalidNumberException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(context);
        List<TextSecureAttachment> convertedAttachments = convertAttachments(attachments);
        List<TextSecureAddress> convertedRecipients = convertRecipients(recipients);

        TextSecureDataMessage.Builder builder = TextSecureDataMessage.newBuilder()
                .withBody(body)
                .withAttachments(convertedAttachments);

        if (recipients.size() > 0) {
            TextSecureGroup textSecureGroup;
            MessagingBridge messagingBridge = whisperPush.getMessagingBridge();
            long threadId = messagingBridge.getThreadId(new HashSet<>(recipients));
            GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
            String groupId = groupDatabase.getGroupId(threadId);
            List<String> members = new ArrayList<>(recipients);
            members.add(whisperPush.getLocalNumber());
            if (TextUtils.isEmpty(groupId)) {
                MessageGroup group = MessageGroup.createNew(threadId);
                groupDatabase.createOrUpdate(group);
                groupId = group.getGroupId();
                try {
                    sendGroupUpdate(group.getGroupIdBytes(), members);
                } catch (EncapsulatedExceptions eex) {
                    Log.w(TAG, eex);
                    throw new IOException(eex);
                }

            }
            textSecureGroup = TextSecureGroup.newBuilder(TextSecureGroup.Type.DELIVER).
                    withId(Util.toIsoBytes(groupId)).
                    withMembers(members).
                    build();

            builder.asGroupMessage(textSecureGroup);
        }

        try {
            messageSender.sendMessage(convertedRecipients, builder.build());
        } catch (IOException e) {
            Log.w(TAG, e);
            throw e;
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            checkAndHandleIdentityChange(eex);
            throw eex;
        }
    }

    private List<TextSecureAttachment> convertAttachments(List<Pair<String, Uri>> attachments)
            throws IOException {
        List<TextSecureAttachment> convertedAttachments = new LinkedList<>();

        for (Pair<String, Uri> attachment : attachments) {
            try {
                InputStream stream = context.getContentResolver().openInputStream(attachment.second);
                convertedAttachments.add(TextSecureAttachment.newStreamBuilder()
                        .withStream(stream)
                        .withContentType(attachment.first)
                        .withLength((long) stream.available())
                        .build());
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Attachment retrieving failed", e);
            }
        }

        return convertedAttachments;
    }

    private List<TextSecureAddress> convertRecipients(List<String> recipients)
            throws InvalidNumberException, IOException {
        List<TextSecureAddress> secureRecipients = new ArrayList<>(recipients.size());
        for (String recipient : recipients) {
            String e164number;
            try {
                e164number = whisperPush.formatNumber(recipient);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw e;
            }
            boolean recipientSupportsSecureMessaging = whisperPush.isRecipientSupportsSecureMessaging(e164number, true);
            if (!recipientSupportsSecureMessaging) {
                throw new IOException("Recipient " + e164number + " doesn't support secure messaging");
            }
            secureRecipients.add(new TextSecureAddress(e164number));
        }
        return secureRecipients;
    }

    private void send(SendReq message, List<TextSecureAttachment> attachments, TextSecureGroup textSecureGroup)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(context);
        List<TextSecureAddress> recipients = getGroupRecipients(message);

        try {
            String body = getMessageText(message.getBody());
            TextSecureDataMessage.Builder builder = TextSecureDataMessage.newBuilder()
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
            checkAndHandleIdentityChange(eex);
            throw new MmsException(eex);
        }

    }

    public void sendGroupUpdate(byte[] id, Collection<String> members)
            throws UntrustedIdentityException, InvalidNumberException, EncapsulatedExceptions, IOException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(context);
        List<TextSecureAddress> recipients = new ArrayList<>(members.size());

        for (String destination : members) {
            String e164number;
            try {
                e164number = whisperPush.formatNumber(destination);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw e;
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
                    TextSecureDataMessage.newBuilder().asGroupMessage(textSecureGroup).build());

        } catch (IOException e) {
            Log.w(TAG, e);
            throw e;
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            checkAndHandleIdentityChange(eex);
            throw eex;
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
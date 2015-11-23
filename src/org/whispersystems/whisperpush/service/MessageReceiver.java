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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.crypto.TextSecureCipher;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.whisperpush.R;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.api.MessageGroup;
import org.whispersystems.whisperpush.api.MessagingBridge;
import org.whispersystems.whisperpush.attachments.AttachmentManager;
import org.whispersystems.whisperpush.contacts.Contact;
import org.whispersystems.whisperpush.contacts.ContactsFactory;
import org.whispersystems.whisperpush.crypto.IdentityMismatchException;
import org.whispersystems.whisperpush.database.DatabaseFactory;
import org.whispersystems.whisperpush.database.GroupDatabase;
import org.whispersystems.whisperpush.database.WPAxolotlStore;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.directory.NotInDirectoryException;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

public class MessageReceiver {

    private static final String TAG = MessageReceiver.class.getSimpleName();

    private static volatile MessageReceiver sInstance;

    private final Context context;
    private final WhisperPush whisperPush;
    private volatile TextSecureMessageReceiver mTextSecureReceiver;

    public static MessageReceiver getInstance(Context context) {
        if (sInstance == null) {
            synchronized (MessageReceiver.class) {
                if (sInstance == null) {
                    sInstance = new MessageReceiver(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private MessageReceiver(Context appContext) {
        this.context = appContext;
        this.whisperPush = WhisperPush.getInstance(appContext);
    }

    private Directory getContactDirectory() {
        return whisperPush.getContactDirectory();
    }

    private TextSecureMessageReceiver getTextSecureReceiver() {
        if (mTextSecureReceiver == null) {
            synchronized (this) {
                if (mTextSecureReceiver == null) {
                    mTextSecureReceiver = WhisperServiceFactory.createMessageReceiver(context);
                }
            }
        }
        return mTextSecureReceiver;
    }

    public void handleNotification() {
        List<TextSecureEnvelope> messages;
        try {
            messages = getTextSecureReceiver().retrieveMessages();
            for(TextSecureEnvelope message : messages) {
                handleEnvelope(message, true);
            }
        } catch (IOException e) {
            Log.w(TAG, e);
            MessageNotifier.notifyProblem(context,
                context.getString(R.string.GcmReceiver_error),
                // FIXME: probably a network error, and not badly formatted message?
                context.getString(R.string.GcmReceiver_received_badly_formatted_push_message));
        }
    }

    public void handleEnvelope(TextSecureEnvelope envelope, boolean sendExplicitReceipt) {
        String source = envelope.getSource();
        boolean isActiveNumber = whisperPush.isRecipientSupportsSecureMessaging(source, false);
        if (!isActiveNumber) {
            ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
            contactTokenDetails.setNumber(source);
            getContactDirectory().setNumber(contactTokenDetails, true);
        }

        if (envelope.isReceipt()) handleReceipt(envelope);
        else handleMessage(envelope);
    }

    private void handleReceipt(TextSecureEnvelope envelope) {
        Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
        // FIXME: don't know what to do with receipt
        //DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(envelope.getSource(),
        //                                                                         envelope.getTimestamp());
    }

    public void handleMessage(TextSecureEnvelope message) {
        if (message == null)
            return;

        String source = message.getSource();

        if (isNumberBlackListed(source)) {
            MessageNotifier.notifyBlacklisted(context, source);
            return;
        }

        try {
            TextSecureDataMessage content = getPlaintext(message);
            Optional<String> body = content.getBody();
            String textBody = body.isPresent() ? body.get() : "";

            if (content.isEndSession()) {
                Log.i(TAG, "Secure session reset.");
                WPAxolotlStore axolotlStore = WPAxolotlStore.getInstance(context);
                axolotlStore.deleteAllSessions(source);
                setActiveSession(source, false);
            } else {
                if (!getContactDirectory().hasActiveSession(source)) {
                    Log.d(TAG, "New session detected for " + source);
                    setActiveSession(source, true);
                    MessageNotifier.notifyNewSessionIncoming(context, message);
                }
                updateDirectoryIfNecessary(message);

                MessagingBridge messagingBridge = whisperPush.getMessagingBridge();

                Optional<TextSecureGroup> textSecureGroupOptional = content.getGroupInfo();
                long timestamp = message.getTimestamp();

                Optional<List<TextSecureAttachment>> attach = content.getAttachments();

                if (textSecureGroupOptional.isPresent()) {
                    handleGroupMessage(textSecureGroupOptional, messagingBridge,
                            source, attach, textBody, timestamp);
                } else if (attach.isPresent()) {
                    handleMultimediaMessage(messagingBridge, source, attach, textBody, timestamp);
                } else {
                    messagingBridge.storeIncomingTextMessage(source, textBody, timestamp, false);
                }

                if (StatsUtils.isStatsActive(context)) {
                    WhisperPreferences.setWasActive(context, true);
                }
            }
        } catch (IdentityMismatchException e) {
            Log.w(TAG, e);
            DatabaseFactory.getPendingApprovalDatabase(context).insert(message);
            MessageNotifier.updateNotifications(context);
        } catch (InvalidMessageException e) {
            Log.w(TAG, e);
            Contact contact = ContactsFactory.getContactFromNumber(context, source, false);
            MessageNotifier.notifyProblem(context, contact,
                    context.getString(R.string.MessageReceiver_received_badly_encrypted_message));
        }
    }

    private void handleGroupMessage(Optional<TextSecureGroup> textSecureGroupOptional,
                                    MessagingBridge messagingBridge, String source,
                                    Optional<List<TextSecureAttachment>> attach,
                                    String textBody, long timestamp)
            throws InvalidMessageException {
        TextSecureGroup textSecureGroup = textSecureGroupOptional.get();
        String groupId = MessageGroup.toStringId(textSecureGroup.getGroupId());
        TextSecureGroup.Type type = textSecureGroup.getType();
        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

        if (type == TextSecureGroup.Type.UPDATE) {
            Set<String> members = new HashSet<>();
            if (textSecureGroup.getMembers().isPresent()) {
                members.addAll(textSecureGroup.getMembers().get());
            }
            members.remove(WhisperPush.getInstance(context).getLocalNumber());
            members.add(source);
            groupDatabase.createOrUpdate(new MessageGroup(
                    groupId,
                    messagingBridge.getThreadId(members)
            ));
        } else if (type == TextSecureGroup.Type.DELIVER) {
            try {
                long threadId = groupDatabase.getThreadId(groupId);
                List<Pair<byte[], Uri>> attachments;
                if (attach.isPresent()) {
                    attachments = retrieveAttachments(attach.get(), timestamp);
                } else {
                    attachments = Collections.EMPTY_LIST;
                }
                messagingBridge.storeIncomingGroupMessage(
                        source, textBody, attachments, timestamp, threadId);
            } catch (IOException e) {
                Log.w(TAG, e);
                Contact contact = ContactsFactory.getContactFromNumber(context, source, false);
                MessageNotifier.notifyProblem(context, contact,
                        context.getString(R.string.MessageReceiver_unable_to_retrieve_encrypted_attachment_for_incoming_message));
            }
        } else if (type == TextSecureGroup.Type.QUIT) {
            long threadId = groupDatabase.getThreadId(groupId);
            Set<String> recipients = messagingBridge.getRecipientsByThread(threadId);
            recipients.remove(source);
            long newThreadId = messagingBridge.getThreadId(recipients);
            if (newThreadId == -1) {
                groupDatabase.remove(groupId);
            } else if (newThreadId != threadId) {
                groupDatabase.createOrUpdate(new MessageGroup(groupId, newThreadId));
            }
        }
    }

    private void handleMultimediaMessage(MessagingBridge messagingBridge, String source,
                                         Optional<List<TextSecureAttachment>> attach,
                                         String textBody, long timestamp) {
        try {
            List<Pair<byte[], Uri>> attachments = retrieveAttachments(attach.get(), timestamp);
            messagingBridge.storeIncomingMultimediaMessage(source, textBody, attachments, timestamp);
        } catch (Throwable e) {
            Log.w(TAG, e);
            Contact contact = ContactsFactory.getContactFromNumber(context, source, false);
            MessageNotifier.notifyProblem(context, contact,
                    context.getString(R.string.MessageReceiver_unable_to_retrieve_encrypted_attachment_for_incoming_message));
        }
    }

    private TextSecureDataMessage getPlaintext(TextSecureEnvelope envelope)
            throws IdentityMismatchException, InvalidMessageException
    {
        try {
            WPAxolotlStore store = WPAxolotlStore.getInstance(context);
            TextSecureAddress localAddress = new TextSecureAddress(whisperPush.getLocalNumber());
            TextSecureCipher cipher = new TextSecureCipher(localAddress, store);
            return cipher.decrypt(envelope).getDataMessage().get();
        } catch (Exception e) {
            if (e instanceof IdentityMismatchException) {
                throw (IdentityMismatchException) e;
            }
            if (e instanceof org.whispersystems.libaxolotl.UntrustedIdentityException
                    || e instanceof org.whispersystems.textsecure.api.crypto.UntrustedIdentityException) {
                throw new IdentityMismatchException(e.getMessage(), e);
            }
            // FIXME: not the best error handling approach?
            throw new InvalidMessageException(e.getMessage(), e);
        }
    }

    private List<Pair<byte[], Uri>> retrieveAttachments(List<TextSecureAttachment> list, long timestamp)
            throws IOException, InvalidMessageException
    {
        AttachmentManager attachmentManager = AttachmentManager.getInstance(context);
        List<Pair<byte[], Uri>> results = new LinkedList<>();
        TextSecureMessageReceiver receiver = getTextSecureReceiver();
        for (TextSecureAttachment attachment : list) {
            InputStream stream = null;
            byte[] attachmentBytes;

            try {
                if (attachment instanceof TextSecureAttachmentPointer) {
                    stream = attachmentManager.store((TextSecureAttachmentPointer) attachment, receiver);
                } else {
                    stream = attachment.asStream().getInputStream();
                }
                attachmentBytes = Util.readBytes(stream);
            } finally {
                if (stream != null) {
                    stream.close();
                }
            }

            byte[] contentType = Util.toIsoBytes(attachment.getContentType());
            // use sentAt timestamp as dummyId
            Uri uri = whisperPush.getMessagingBridge().persistPart(contentType, attachmentBytes, timestamp);
            if (uri != null) {
                results.add(new Pair<>(contentType, uri));
            } else {
                Log.w(TAG, "Cannot persist attachment");
            }
        }

        return results;
    }

    private void updateDirectoryIfNecessary(TextSecureEnvelope message) {
        String source = message.getSource();
        if (!isActiveNumber(source)) {
            Directory           directory           = Directory.getInstance(context);
            directory.setActiveNumberAndRelay(source, message.getRelay());
        }
    }

    private boolean isActiveNumber(String e164number) {
        try {
            return getContactDirectory().isActiveNumber(e164number);
        } catch (NotInDirectoryException e) {
            return false;
        }
    }

    private void setActiveSession(String e164number, boolean hasActiveSession) {
        getContactDirectory().setActiveSession(e164number, hasActiveSession);
    }

    private boolean isNumberBlackListed(String number) {
        return whisperPush.getMessagingBridge()
                .isAddressBlacklisted(number);
    }

    public synchronized void reset() {
        mTextSecureReceiver = null;
    }

}
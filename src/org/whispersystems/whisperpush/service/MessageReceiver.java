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
import java.util.LinkedList;
import java.util.List;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.crypto.TextSecureCipher;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.R;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.attachments.AttachmentManager;
import org.whispersystems.whisperpush.contacts.Contact;
import org.whispersystems.whisperpush.contacts.ContactsFactory;
import org.whispersystems.whisperpush.crypto.IdentityMismatchException;
import org.whispersystems.whisperpush.database.DatabaseFactory;
import org.whispersystems.whisperpush.database.WPAxolotlStore;
import org.whispersystems.whisperpush.db.CMDatabase;
import org.whispersystems.whisperpush.directory.Directory;
import org.whispersystems.whisperpush.directory.NotInDirectoryException;
import org.whispersystems.whisperpush.util.StatsUtils;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import android.content.Context;
import android.net.Uri;
import android.provider.Telephony;
import android.util.Log;
import android.util.Pair;

import com.android.internal.telephony.util.BlacklistUtils;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;

import static java.lang.String.valueOf;
import static org.whispersystems.whisperpush.util.Util.extractMessageId;

public class MessageReceiver {

    private static final String TAG = MessageReceiver.class.getSimpleName();

    private final Context context;
    private final TextSecureMessageReceiver receiver;
    private final WhisperPush whisperPush;

    public MessageReceiver(Context context) {
        this.context = context;
        this.receiver = WhisperServiceFactory.createMessageReceiver(context);
        this.whisperPush = WhisperPush.getInstance(context);
    }

    public void handleNotification() {
        List<TextSecureEnvelope> messages;
        try {
            messages = receiver.retrieveMessages();
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
            whisperPush.getContactDirectory()
                    .setNumber(contactTokenDetails, true);
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

        if (!hasActiveSession(source)) {
            Log.d(TAG, "New session detected for " + source);
            setActiveSession(source);
            MessageNotifier.notifyNewSessionIncoming(context, message);
        }
        updateDirectoryIfNecessary(message);

        try {
            TextSecureMessage content = getPlaintext(message);
            Optional<String> body = content.getBody();
            long timestamp = message.getTimestamp();
            List<Pair<String, String>> attachments;
            Optional<List<TextSecureAttachment>> attach = content.getAttachments();

            if (attach.isPresent()) {
                try {
                    attachments = retrieveAttachments(source, attach.get());
                    whisperPush.getMessagingBridge()
                            .storeIncomingSecureMultimediaMessage(source, body.get(), attachments, timestamp, true);
                } catch (IOException e) {
                    Log.w(TAG, e);
                    Contact contact = ContactsFactory.getContactFromNumber(context, source, false);
                    MessageNotifier.notifyProblem(context, contact,
                            context.getString(R.string.MessageReceiver_unable_to_retrieve_encrypted_attachment_for_incoming_message));
                }
            } else {
                Uri messageUri = whisperPush.getMessagingBridge()
                        .storeIncomingTextMessage(0, source, body.get(), timestamp, false, true);
                whisperPush.markMessageAsSecurelySent(messageUri);
            }

            if (StatsUtils.isStatsActive(context)) {
                WhisperPreferences.setWasActive(context, true);
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

    private TextSecureMessage getPlaintext(TextSecureEnvelope envelope)
            throws IdentityMismatchException, InvalidMessageException
    {
        try {
            WPAxolotlStore store = WPAxolotlStore.getInstance(context);
            TextSecureCipher cipher = new TextSecureCipher(store);
            return cipher.decrypt(envelope);
        } catch (Exception e) {
            if (e instanceof IdentityMismatchException) {
                throw (IdentityMismatchException)e;
            } else {
                // FIXME: not the best error handling approach?
                throw new InvalidMessageException(e.getMessage(), e);
            }
        }
    }

    private List<Pair<String, String>> retrieveAttachments(String from, List<TextSecureAttachment> list)
            throws IOException, InvalidMessageException
    {
        AttachmentManager attachmentManager = AttachmentManager.getInstance(context);
        List<Pair<String, String>> results = new LinkedList<Pair<String, String>>();

        long threadId = Telephony.Threads.getOrCreateThreadId(context, from);
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

            PduPersister pduPersister = PduPersister.getPduPersister(context);
            PduPart pduPart = new PduPart();
            pduPart.setContentType(Util.toIsoBytes(attachment.getContentType()));
            pduPart.setData(attachmentBytes);
            try {
                Uri uri = pduPersister.persistPart(pduPart, threadId, null);
                results.add(Pair.create(uri.toString(), attachment.getContentType()));
            } catch (MmsException e) {
                Log.e(TAG, "Cannot persist attachment", e);
            }
        }

        return results;
    }

    private void updateDirectoryIfNecessary(TextSecureEnvelope message) {
        if (!isActiveNumber(message.getSource())) {
            Directory           directory           = Directory.getInstance(context);
            directory.setActiveNumberAndRelay(message.getSource(), message.getRelay());
        }
    }

    private boolean isActiveNumber(String e164number) {
        try {
            return Directory.getInstance(context).isActiveNumber(e164number);
        } catch (NotInDirectoryException e) {
            return false;
        }
    }

    private boolean hasActiveSession(String e164number) {
        return CMDatabase.getInstance(context).hasActiveSession(e164number);
    }

    private void setActiveSession(String e164number) {
        CMDatabase.getInstance(context).setActiveSession(e164number);
    }

    private boolean isNumberBlackListed(String number) {
        String local     = WhisperPreferences.getLocalNumber(context);
        String formatted = PhoneNumberFormatter.formatE164(local, number);
        int type = BlacklistUtils.isListed(context, formatted, BlacklistUtils.BLOCK_MESSAGES);
        return type != BlacklistUtils.MATCH_NONE;
    }

    public interface SecureMessageSaver {
        void saveSecureMessage(String from,
                               String message,
                               List<Pair<String, String>> attachments, long sentTimestamp);
    }

}
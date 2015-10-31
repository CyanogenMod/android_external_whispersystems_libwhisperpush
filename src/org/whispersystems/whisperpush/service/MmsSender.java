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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.SendReq;

import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MmsSender {
    private static final String TAG = MmsSender.class.getSimpleName();

    private final Context mContext;

    public MmsSender(Context context) {
        this.mContext = context;
    }

    public void send(SendReq message, List<TextSecureAttachment> attachments)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(mContext);
        EncodedStringValue[] destinations = message.getTo();
        List<TextSecureAddress> recipients = new ArrayList<>(destinations.length);

        String localNumber = WhisperPreferences.getLocalNumber(mContext);
        for (EncodedStringValue destination : destinations) {
            String e164number;
            try {
                e164number = PhoneNumberFormatter.formatNumber(destination.getString(), localNumber);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw new MmsException(e);
            }
            recipients.add(new TextSecureAddress(e164number));
        }

        try {
            String body = getMessageText(message.getBody());

            messageSender.sendMessage(recipients,
                    TextSecureMessage.newBuilder()
                            .withBody(body)
                            .withAttachments(attachments)
                            .build());

        } catch (IOException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            throw new MmsException(eex);
        }
    }

    public void sendGroupMessage(SendReq message, List<TextSecureAttachment> attachments, byte[] id)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(mContext);
        EncodedStringValue[] destinations = message.getTo();
        List<TextSecureAddress> recipients = new ArrayList<>(destinations.length);

        String localNumber = WhisperPreferences.getLocalNumber(mContext);
        for (EncodedStringValue destination : destinations) {
            String e164number;
            try {
                e164number = PhoneNumberFormatter.formatNumber(destination.getString(), localNumber);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw new MmsException(e);
            }
            recipients.add(new TextSecureAddress(e164number));
        }

        try {
            String body = getMessageText(message.getBody());
            TextSecureGroup textSecureGroup = new TextSecureGroup(TextSecureGroup.Type.DELIVER,
                        id, null, null, null);

            messageSender.sendMessage(recipients,
                    TextSecureMessage.newBuilder()
                            .withBody(body)
                            .withAttachments(attachments)
                            .asGroupMessage(textSecureGroup)
                            .build());

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
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(mContext);
        List<TextSecureAddress> recipients = new ArrayList<>(members.size());

        String localNumber = WhisperPreferences.getLocalNumber(mContext);
        for (String destination : members) {
            String e164number;
            try {
                e164number = PhoneNumberFormatter.formatNumber(destination, localNumber);
            } catch (InvalidNumberException e) {
                Log.w(TAG, e);
                throw new MmsException(e);
            }
            recipients.add(new TextSecureAddress(e164number));
        }
        members.add(localNumber);

        try {
            TextSecureGroup textSecureGroup = new TextSecureGroup(TextSecureGroup.Type.UPDATE,
                        id, null, new ArrayList<>(members), null);

            messageSender.sendMessage(recipients,
                    TextSecureMessage.newBuilder()
                            .asGroupMessage(textSecureGroup)
                            .build());

        } catch (IOException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
        } catch (EncapsulatedExceptions eex) {
            Log.w(TAG, eex);
            throw new MmsException(eex);
        }
    }

    private String getMessageText(PduBody body) {
        String bodyText = null;

        for (int i = 0; i < body.getPartsNum(); i++) {
            if (ContentType.isTextType(Util.toIsoString(body.getPart(i).getContentType()))) {
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
}


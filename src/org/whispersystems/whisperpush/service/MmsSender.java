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
import android.util.Log;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.SendReq;

import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.api.util.PhoneNumberFormatter;
import org.whispersystems.whisperpush.util.Util;
import org.whispersystems.whisperpush.util.WhisperPreferences;
import org.whispersystems.whisperpush.util.WhisperServiceFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class MmsSender {
    private static final String TAG = MmsSender.class.getSimpleName();

    private final Context mContext;

    public MmsSender(Context context) {
        this.mContext = context;
    }

    public void send(SendReq message, List<TextSecureAttachment> attachments)
            throws MmsException, UntrustedIdentityException {
        TextSecureMessageSender messageSender = WhisperServiceFactory.createMessageSender(mContext);
        String destination = message.getTo()[0].getString();

        try {
            String localNumber = WhisperPreferences.getLocalNumber(mContext);
            String e164number = PhoneNumberFormatter.formatNumber(destination, localNumber);
            TextSecureAddress address = new TextSecureAddress(e164number);
            String body = getMessageText(message.getBody());

            messageSender.sendMessage(address,
                    TextSecureMessage.newBuilder()
                            .withBody(body)
                            .withAttachments(attachments)
                            .build());

        } catch (InvalidNumberException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
        } catch (IOException e) {
            Log.w(TAG, e);
            throw new MmsException(e);
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


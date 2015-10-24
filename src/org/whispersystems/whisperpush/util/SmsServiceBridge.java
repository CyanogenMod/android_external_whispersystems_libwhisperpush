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
package org.whispersystems.whisperpush.util;

import android.content.Context;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.util.Log;
import android.util.Pair;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.whisperpush.service.MessageReceiver;

import java.util.List;

/**
 * A helper class to handle the WhisperPush --> System Framework binding.
 */
public class SmsServiceBridge {

    public static void receivedPushMultimediaMessage(
            Context context, String source, Optional<String> message,
            List<Pair<String,String>> attachments, long sentTimestamp) {
        if (context.getApplicationContext() instanceof MessageReceiver.SecureMessageSaver) {
            ((MessageReceiver.SecureMessageSaver) context.getApplicationContext()).
                    saveSecureMessage(source, message.get(), attachments, sentTimestamp);
        }
    }

    public static Uri receivedPushTextMessage(Context context, int subId, String source,
                                              Optional<String> message, long timestampSent) {
        Uri messageUri = Sms.Inbox.addMessage(subId, context.getContentResolver(),
                source,
                message.get(), null /*subject*/, timestampSent,
                false /*read*/);
        return messageUri;
    }

    @SuppressWarnings("unused") // keep for debugging
    private static void logReceived(String source, List<String> destinations, String message,
                                    List<Pair<String,String>> attachments, long timestampSent)
    {
        Log.w("SmsServiceBridge", "Incoming Message Source: " + source);

        for (String destination : destinations) {
            Log.w("SmsServiceBridge", "Incoming Message Destination: " + destination);
        }

        Log.w("SmsServiceBridge", "Incoming Message Body: " + message);

        for (Pair<String, String> attachment : attachments) {
            Log.w("SmsServiceBridge", String.format("Incoming Message Attachment: %s, %s", attachment.first, attachment.second));
        }

        Log.w("SmsServiceBridge", "Incoming Message Sent Time: " + timestampSent);
    }

}

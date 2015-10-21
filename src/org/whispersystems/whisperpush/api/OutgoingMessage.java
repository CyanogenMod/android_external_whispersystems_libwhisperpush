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
package org.whispersystems.whisperpush.api;

import android.app.PendingIntent;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Intent;
import android.text.TextUtils;

import org.whispersystems.whisperpush.util.Util;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class OutgoingMessage {

    private static final String DESTINATION = "destAddr";
    private static final String PARTS = "parts";
    private static final String SENT_INTENTS = "sentIntents";
    private static final String DELIVERY_INTENTS = "deliveryIntents";

    private final List<String> mDestinations;
    private final String mMessageBody;
    private final List<PendingIntent> mSentIntents;
    private final List<PendingIntent> mDeliveryIntents;
    private final ResultListener mResultListener;
    private final long mTimestamp;

    public interface ResultListener {
        void onComplete();
        void onAbort(Throwable ex);
    }

    public static OutgoingMessage fromSmsBroadcast(Intent intent, final PendingResult pendingResult) {
        return new OutgoingMessage(intent, pendingResult != null ? new ResultListener() {
            @Override
            public void onComplete() {
                pendingResult.abortBroadcast();
                pendingResult.setResultCode(0);
                pendingResult.finish();
            }
            @Override
            public void onAbort(Throwable ex) {
                pendingResult.finish();
            }
        } : null);
    }

    private OutgoingMessage(Builder builder) {
        mDestinations = builder.mDestinations;
        mMessageBody = builder.mMessageBody;
        mSentIntents = builder.mSentIntents;
        mDeliveryIntents = builder.mDeliveryIntents;
        mResultListener = builder.mResultListener;
        mTimestamp = builder.mTimestamp;
    }

    private OutgoingMessage(Intent intent, ResultListener resultListener) {
        String destination = intent.getStringExtra(DESTINATION);
        if (TextUtils.isEmpty(destination)) {
            throw new IllegalArgumentException("destination");
        }
        this.mDestinations = Collections.singletonList(destination);
        this.mMessageBody = joinParts(intent.getStringArrayListExtra(PARTS));
        this.mSentIntents = intent.getParcelableArrayListExtra(SENT_INTENTS);
        this.mDeliveryIntents = intent.getParcelableArrayListExtra(DELIVERY_INTENTS);
        this.mResultListener = resultListener;
        this.mTimestamp = 0;
    }

    public List<String> getDestinations() {
        return mDestinations;
    }

    public String getMessageBody() {
        return mMessageBody;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public List<PendingIntent> getSentIntents() {
        return mSentIntents;
    }

    public List<PendingIntent> getDeliveryIntents() {
        return mDeliveryIntents;
    }

    public void completeOperation() {
        if (mResultListener != null) {
            mResultListener.onComplete();
        }
    }

    public void abortOperation(Throwable ex) {
        if (mResultListener != null) {
            mResultListener.onAbort(ex);
        }
    }

    private static String joinParts(List<String> messageParts) {
        if (Util.isEmpty(messageParts)) {
            return null;
        }
        return TextUtils.join("", messageParts);
    }

    public static class Builder {
        private List<String> mDestinations;
        private String mMessageBody;
        private List<PendingIntent> mSentIntents;
        private List<PendingIntent> mDeliveryIntents;
        private ResultListener mResultListener;
        private long mTimestamp;

        public Builder setDestination(String destination) {
            return setDestinations(asList(destination));
        }

        public Builder setDestinations(List<String> destinations) {
            this.mDestinations = destinations;
            return this;
        }

        public Builder setMessage(List<String> messageParts) {
            this.mMessageBody = joinParts(messageParts);
            return this;
        }

        public Builder setMessage(String message) {
            this.mMessageBody = message;
            return this;
        }

        public Builder setSentIntents(List<PendingIntent> sentIntents) {
            this.mSentIntents = sentIntents;
            return this;
        }

        public Builder setDeliveryIntents(List<PendingIntent> deliveryIntents) {
            this.mDeliveryIntents = deliveryIntents;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            this.mTimestamp = timestamp;
            return this;
        }

        public Builder setResultListener(ResultListener resultListener) {
            this.mResultListener = resultListener;
            return this;
        }

        public OutgoingMessage build() {
            return new OutgoingMessage(this);
        }
    }

}

package org.whispersystems.whisperpush.api;

import android.app.PendingIntent;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Intent;
import android.text.TextUtils;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class OutgoingMessage {

    private static final String DESTINATION = "destAddr";
    private static final String PARTS = "parts";
    private static final String SENT_INTENTS = "sentIntents";
    private static final String DELIVERY_INTENTS = "deliveryIntents";

    private List<String> mDestinations;
    private List<String> mMessageParts;
    private List<PendingIntent> mSentIntents;
    private List<PendingIntent> mDeliveryIntents;
    private PendingResult mPendingResult;

    public static OutgoingMessage fromSmsBroadcast(Intent intent, PendingResult pendingResult) {
        return new OutgoingMessage(intent, pendingResult);
    }

    private OutgoingMessage() {
    }

    private OutgoingMessage(Intent intent, PendingResult pendingResult) {
        String destination = intent.getStringExtra(DESTINATION);
        if (TextUtils.isEmpty(destination)) {
            throw new IllegalArgumentException("destination");
        }
        this.mDestinations = Collections.singletonList(destination);
        this.mMessageParts = intent.getStringArrayListExtra(PARTS);
        this.mSentIntents = intent.getParcelableArrayListExtra(SENT_INTENTS);
        this.mDeliveryIntents = intent.getParcelableArrayListExtra(DELIVERY_INTENTS);
        this.mPendingResult = pendingResult;
    }

    public List<String> getDestinations() {
        return mDestinations;
    }

    public List<String> getParts() {
        return mMessageParts;
    }

    public List<PendingIntent> getSentIntents() {
        return mSentIntents;
    }

    public List<PendingIntent> getDeliveryIntents() {
        return mDeliveryIntents;
    }

    public void completeOperation(int resultCode) {
        PendingResult pendingResult = mPendingResult;
        if (pendingResult != null) {
            pendingResult.abortBroadcast();
            pendingResult.setResultCode(resultCode);
            pendingResult.finish();
        }
    }

    public void abortOperation() {
        PendingResult pendingResult = mPendingResult;
        if (pendingResult != null) {
            pendingResult.finish();
        }
    }

    public static class Builder {
        private List<String> mDestinations;
        private List<String> mMessageParts;
        private List<PendingIntent> mSentIntents;
        private List<PendingIntent> mDeliveryIntents;
        private PendingResult mPendingResult;

        public Builder setDestination(String destination) {
            return setDestinations(asList(destination));
        }

        public Builder setDestinations(List<String> destinations) {
            this.mDestinations = destinations;
            return this;
        }

        public Builder setMessageParts(List<String> messageParts) {
            this.mMessageParts = messageParts;
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

        public Builder setPendingResult(PendingResult pendingResult) {
            this.mPendingResult = pendingResult;
            return this;
        }

        public OutgoingMessage build() {
            OutgoingMessage result = new OutgoingMessage();
            result.mDestinations = mDestinations;
            result.mMessageParts = mMessageParts;
            result.mSentIntents = mSentIntents;
            result.mDeliveryIntents = mDeliveryIntents;
            result.mPendingResult = mPendingResult;
            return result;
        }
    }

}

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


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.whisperpush.WhisperPush;
import org.whispersystems.whisperpush.database.DatabaseFactory;
import org.whispersystems.whisperpush.database.PendingApprovalDatabase;
import org.whispersystems.whisperpush.sms.OutgoingSmsQueue;
import org.whispersystems.whisperpush.api.OutgoingMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The service that does the work of delivering outgoing messages and processing
 * incoming messages.
 *
 * @author Moxie Marlinspike
 */
public class SendReceiveService extends Service {

    private static final String TAG = "SendReceiveService";

    public static final String RCV_NOTIFICATION = "org.whispersystems.SendReceiveService.RCV_NOTIFICATION";
    public static final String RCV_PENDING      = "org.whispersystems.SendReceiveService.RCV_PENDING";
    public static final String SEND_SMS         = "org.whispersystems.SendReceiveService.SEND_SMS";

    public  static final String DESTINATION  = "destAddr";

    private final ExecutorService  executor    = Executors.newCachedThreadPool();
    private final OutgoingSmsQueue outgoingQueue = OutgoingSmsQueue.getInstance();

    private volatile WhisperPush whisperPush;
    private MessageReceiver messageReceiver;

    private final BlockingQueue<Integer> startsQueue = new LinkedBlockingQueue<>(256); //TODO optimize: keep latest startId only and counter

    @Override
    public void onCreate() {
        this.whisperPush = WhisperPush.getInstance(this);
        this.messageReceiver = MessageReceiver.getInstance(this);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        startsQueue.offer(startId);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (intent != null) {
                    onHandleIntent(intent);
                    WakefulBroadcastReceiver.completeWakefulIntent(intent);
                }
                Integer startId = startsQueue.poll();
                if (startId != null) {
                    stopSelf(startId);
                } else {
                    Log.e(TAG, "startId == null");
                }
            }
        });
        return START_NOT_STICKY;
    }

    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (RCV_NOTIFICATION.equals(action)) {
            messageReceiver.handleNotification();
        } else if (RCV_PENDING.equals(action)) {
            long messageId = intent.getLongExtra("message_id", 0);
            PendingApprovalDatabase database = DatabaseFactory.getPendingApprovalDatabase(this);
            TextSecureEnvelope message = database.get(messageId);
            if (message != null) {
                database.delete(messageId);
                messageReceiver.handleEnvelope(message, true);
            }
        } else if (SEND_SMS.equals(action)) {
            OutgoingMessage message = outgoingQueue.get();
            if (message != null) {
                whisperPush.getMessageSender().sendTextMessage(message);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
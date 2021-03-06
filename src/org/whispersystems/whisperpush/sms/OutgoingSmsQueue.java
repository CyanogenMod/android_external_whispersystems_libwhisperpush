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
package org.whispersystems.whisperpush.sms;


import org.whispersystems.whisperpush.api.OutgoingMessage;

import java.util.LinkedList;

/**
 * A singleton queue that allows for passing data between the incoming broadcast
 * receiver and the service that processes that incoming data.  This is necessary
 * because the PendingResult for a broadcast that is being handled asynchronously
 * isn't parcelable and can't be passed through the standard intent communication
 * interface.
 *
 * Messages received by the broadcast receiver are appended to this queue, the service
 * is started, and the latter then pulls off this queue.
 *
 * @author Moxie Marlinspike
 */
public class OutgoingSmsQueue {

    private static final OutgoingSmsQueue instance = new OutgoingSmsQueue();

    public static OutgoingSmsQueue getInstance() {
        return instance;
    }

    private final LinkedList<OutgoingMessage> queue = new LinkedList<OutgoingMessage>();

    public synchronized void put(OutgoingMessage candidate) {
        queue.add(candidate);
    }

    public synchronized OutgoingMessage get() {
        if (queue.isEmpty())
            return null;

        return queue.removeFirst();
    }

}

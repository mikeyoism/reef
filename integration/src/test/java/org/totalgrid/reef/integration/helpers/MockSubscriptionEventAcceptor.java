/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.integration.helpers;

import org.totalgrid.reef.api.*;
import org.totalgrid.reef.api.javaclient.*;

import java.util.LinkedList;
import java.util.List;

public class MockSubscriptionEventAcceptor<T> implements SubscriptionEventAcceptor<T> {

    private boolean storeResults;
    private BlockingQueue<SubscriptionEvent<T>> queue = new BlockingQueue<SubscriptionEvent<T>>();
    private List<SubscriptionEvent<T>> results = new LinkedList<SubscriptionEvent<T>>();

    /**
     *
     * @param storeResults defaults to false if set we store the retrieved events
     *                     after "seeing" them using pop or waitFor. This allows us
     *                     to look through the event stream at the end of a test to
     */
    public MockSubscriptionEventAcceptor(boolean storeResults) {
        this.storeResults = storeResults;
    }

    public MockSubscriptionEventAcceptor() {
        this(false);
    }

    @Override
    public void onEvent(SubscriptionEvent<T> event) {
        queue.push(event);
    }

    public void clear() {
        queue.clear();
    }

    public SubscriptionEvent<T> pop(long timeoutms) throws InterruptedException {
        SubscriptionEvent<T> ret = queue.pop(timeoutms);
        if (storeResults) results.add(ret);
        return ret;
    }

    public boolean waitFor(T value, long timeoutms) {
        long start = System.currentTimeMillis();
        do {
            try {
                SubscriptionEvent<T> ret = queue.pop(timeoutms);
                if (storeResults) results.add(ret);
                if (ret.getValue().equals(value)) return true;
            } catch (Exception ex) {
                System.out.println(ex);
                return false;
            }
        } while (start + timeoutms > System.currentTimeMillis());

        return false;
    }

    public List<T> getPayloads() {
        if (!storeResults) throw new RuntimeException("Not storing results");
        List<T> list = new LinkedList<T>();
        for (SubscriptionEvent<T> p : results) {
            list.add(p.getValue());
        }
        return list;
    }

    public List<Envelope.Event> getEventCodes() {
        if (!storeResults) throw new RuntimeException("Not storing results");
        List<Envelope.Event> list = new LinkedList<Envelope.Event>();
        for (SubscriptionEvent<T> p : results) {
            list.add(p.getEventType());
        }
        return list;
    }

    public void clearResults() {
        results = new LinkedList<SubscriptionEvent<T>>();
    }
}
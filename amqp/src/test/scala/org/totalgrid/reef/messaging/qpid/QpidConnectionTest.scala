/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.messaging.qpid

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.totalgrid.reef.reactor.ReactActor

import org.totalgrid.reef.util.Conversion._
import org.totalgrid.reef.util.SyncVar
import org.totalgrid.reef.messaging._
import org.totalgrid.reef.api.{ ServiceIOException, IConnectionListener }

@RunWith(classOf[JUnitRunner])
class QpidConnectionTest extends FunSuite with ShouldMatchers {

  test("Qpid connect/disconnect events") {
    val default = BrokerConnectionInfo.loadInfo("test")
    val amqp = new AMQPConnectionReactor with ReactActor {
      val broker = new QpidBrokerConnection(default)
    }

    val listener = new BrokerConnectionState
    amqp.addConnectionListener(listener)

    10.times {
      amqp.start()
      listener.waitUntilConnected()
      amqp.stop()
      listener.waitUntilDisconnected()
    }

  }

  test("Connection Timeout") {
    val default = new BrokerConnectionInfo("127.0.0.1", 10000, "", "", "")
    val amqp = new AMQPConnectionReactor with ReactActor {
      val broker = new QpidBrokerConnection(default)
    }

    intercept[ServiceIOException] {
      amqp.connect(100)
    }

    amqp.disconnect(100)
    amqp.disconnect(100)
  }

  test("Qpid bind/unbind") {
    val default = BrokerConnectionInfo.loadInfo("test")
    val amqp = new AMQPConnectionReactor with ReactActor {
      val broker = new QpidBrokerConnection(default)
    }

    amqp.connect(1000)

    val channel = amqp.getChannel

    channel.declareExchange("test")
    channel.declareQueue("magic")
    channel.bindQueue("magic", "test", "hi", false)
    channel.bindQueue("magic", "test", "hi", false)

    import java.util.concurrent.atomic.AtomicInteger
    val value = new AtomicInteger(0)
    val sv = new SyncVar(value)

    channel.listen("magic", new MessageConsumer() {
      def receive(bytes: Array[Byte], replyTo: Option[Destination]) {
        value.addAndGet(1)
        sv.update(value)
      }
    })
    channel.start

    channel.publish("test", "hi", "hi".getBytes, None)

    // TODO: figure out why multiple bindings dont cause us to get duplicate messages
    sv.waitFor({ _.get == 1 }, 1000)

    channel.unbindQueue("magic", "test", "hi")
    value.set(0)

    channel.publish("test", "hi", "hi".getBytes, None)

    // theres no trigger we can wait for a non-event, just sleep for a bit and make
    // sure that we didn't recieve a message
    Thread.sleep(200)
    value.get should equal(0)
  }

  test("Qpid Close") {
    val default = BrokerConnectionInfo.loadInfo("test")
    val amqp = new AMQPConnectionReactor with ReactActor {
      val broker = new QpidBrokerConnection(default)
    }

    amqp.connect(1000)

    val channel = amqp.getChannel

    var expectedClose: Option[Boolean] = None

    channel.addCloseListener(new BrokerChannelCloseListener {
      def onClosed(channel: BrokerChannel, expected: Boolean) = {
        expectedClose = Some(expected)
      }
    })

    expectedClose should equal(None)

    channel.close

    expectedClose should equal(Some(true))
  }
}


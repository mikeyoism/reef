/**
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
package org.totalgrid.reef.reactor

import org.totalgrid.reef.util.{ ShutdownHook, Logging }

import scala.collection.immutable

object Lifecycle extends ShutdownHook {

  /**      Runs the specified components and blocks for a shutdown signal
   */
  def run(lc: Lifecycle)(beforeShutdownFun: => Unit): Unit = {
    lc.start()
    waitForShutdown {
      beforeShutdownFun
      lc.stop()
    }
  }

  def run(components: Seq[Lifecycle])(beforeShutdownFun: => Unit): Unit = {
    run(new LifecycleWrapper(components))(beforeShutdownFun)
  }

}

/** start components up and shut them down in reverse order
 */
class LifecycleWrapper(components: Seq[Lifecycle]) extends Lifecycle {
  override def doStart() = components.foreach { _.start }
  override def doStop() = components.reverse.foreach { _.stop }
}

/**
 * handles the start/stopping of a set of lifecycle objects including starting
 * objects that are added after we started the manager. 
 */
class LifecycleManager(components: List[Lifecycle] = Nil) extends Lifecycle {

  private var list = components

  def add(seq: Seq[Lifecycle]): Unit = seq.foreach(add)

  def add(lf: Lifecycle): Unit = mutex.synchronized {
    list = lf :: list
    if (started) lf.start
  }

  def remove(lf: Lifecycle) = mutex.synchronized {
    list = list.filterNot { l =>
      if (l.equals(lf) && started) l.stop()
      l.equals(lf)
    }
  }

  override def doStart() = list.reverse.foreach { _.start }

  override def doStop() = {
    list.foreach { _.stop }
    list = Nil
  }
}

/**     Abstracts the lifecycle of thread-like entities    
 */
trait Lifecycle extends Logging {

  protected val mutex = new Object
  protected var started = false

  final def start() = mutex.synchronized {
    if (started) throw new IllegalStateException("Already started")
    info("Starting " + this.getClass())
    this.dispatchStart()
    started = true
    info("Started " + this.getClass())
  }

  final def stop() = mutex.synchronized {
    if (!started) if (started) throw new IllegalStateException("Already stopped")
    info("Stopping " + this.getClass())
    this.dispatchStop()
    started = false
    info("Stopped " + this.getClass())
  }

  /// executes just after starting
  protected def afterStart() {}

  /// executes just before stopping
  protected def beforeStop() {}

  /* --- LifeCycle ---- */

  //  default implementations call afterStart/beforeStop  on calling  thread

  /// start execution and run fun just afterwards
  protected def dispatchStart() = {
    doStart()
    afterStart()
  }

  protected def dispatchStop() = {
    beforeStop()
    doStop()
  }

  // functions for implementing start/stop
  protected def doStart() {}
  protected def doStop() {}

}

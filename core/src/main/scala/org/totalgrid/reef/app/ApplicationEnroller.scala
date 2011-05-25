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
package org.totalgrid.reef.app

import org.totalgrid.reef.proto.Application.ApplicationConfig
import org.totalgrid.reef.proto.Auth._

import org.totalgrid.reef.reactor.{ Reactable, Lifecycle }

import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.sapi.client.{ Success, Failure, SingleSuccess }

import org.totalgrid.reef.util.Logging
import org.totalgrid.reef.messaging._
import org.totalgrid.reef.proto.ReefServicesList

object ApplicationEnroller extends Logging {

  def defaultUserName = SystemProperty.get("reef.user", "system")
  def defaultUserPassword = SystemProperty.get("reef.user.password", "-system-")
  def defaultNodeName = SystemProperty.get("reef.node", "node01")
  def defaultLocation = SystemProperty.get("reef.network", "any")
  def defaultNetwork = SystemProperty.get("reef.location", "any")

  def buildLogin(userName: Option[String] = None, userPassword: Option[String] = None) = {
    val agent = Agent.newBuilder
    agent.setName(userName.getOrElse(defaultUserName)).setPassword(userPassword.getOrElse(defaultUserPassword))
    val auth = AuthToken.newBuilder
    auth.setAgent(agent)
    auth.build
  }

  /// Use the system context to get user name, location, network and instanceName
  def buildConfig(capabilites: List[String], instanceName: Option[String] = None, userName: Option[String] = None, location: Option[String] = None, network: Option[String] = None): ApplicationConfig = {
    val b = ApplicationConfig.newBuilder()

    def randomString(n: Int): String = {
      var sb = new StringBuilder
      for (i <- 1 to n) sb.append(util.Random.nextPrintableChar)
      sb.toString
    }

    b.setInstanceName(instanceName.getOrElse(defaultNodeName + "-" + capabilites.mkString("-")))
    b.setUserName(userName.getOrElse(defaultUserName))
    b.setNetwork(network.getOrElse(defaultNetwork))
    b.setLocation(location.getOrElse(defaultLocation))
    capabilites.foreach(b.addCapabilites(_))
    b.setProcessId(randomString(8))
    b.build
  }
}

import ApplicationEnroller._

/**
 * handles the creation of the ApplicationConfig registration proto and then constructing the major components that
 * depend on the result of the registration (for output channels etc.).
 *
 * This object also handles the lifecycle of the
 *
 * @param amqp bus interface
 * @param processType should be either FEP or Processing
 * @param setupFun the construction function for the class using the components, must be StartStoppable
 */
abstract class ApplicationEnroller(amqp: AMQPProtoFactory, instanceName: Option[String], capabilites: List[String], setupFun: CoreApplicationComponents => Lifecycle) extends Reactable with Lifecycle with Logging {

  private var container: Option[Lifecycle] = None

  // we only need the registry to get the appClient, could special case for ApplicationConfig if bootstrapping exchange names
  // TODO - replace with SessionPool
  private var client: Option[ProtoClient] = None

  private def enroll() {
    freshClient
    client.foreach { c =>
      c.put(buildLogin()).listen { rsp =>
        rsp match {
          case SingleSuccess(status, single) =>
            val env = new RequestEnv
            env.addAuthToken(single.getToken)
            c.setDefaultHeaders(env)
            putAppConfig(c, env, buildConfig(capabilites, instanceName))
          case Success(_, list) =>
            error("Expected 1 AuthToken, but received " + rsp.list)
            reenroll()
          case x: Failure =>
            error("Error getting auth token. " + x)
            reenroll()
        }
      }
    }
  }

  private def reenroll() = delay(2000) { enroll() }

  def putAppConfig(client: ProtoClient, env: RequestEnv, configRequest: ApplicationConfig) = client.put(configRequest).listen {
    _ match {
      case SingleSuccess(_, config) =>
        val components = new CoreApplicationComponents(amqp, config, env)
        container = Some(setupFun(components))
        container.get.start
      case Failure(status, err) =>
        error("Error registering application: " + err)
        reenroll()
    }
  }

  /**
   * there is an implementation detail of AMQP that states that the underlying "session" we use to communicate to the broker
   * will be closed if we try to publish a message to an exchange that hasn't been defined. In our system, if an FEP or HMI
   * client comes up before the services have started (like after a reboot) and an "exchange not found error" is reported we
   * need to throw away the client and get a new one for each attempt to talk to the auth service.
   */
  private def freshClient() {
    client.foreach(_.close)
    client = Some(new ProtoClient(amqp, ReefServicesList, 5000))
  }

  /**
   * blocking start
   */
  override def afterStart() = enroll()

  /**
   * stops the contained object
   */
  override def beforeStop() = {
    container.foreach(_.stop)
    client.foreach(_.close)
  }

}

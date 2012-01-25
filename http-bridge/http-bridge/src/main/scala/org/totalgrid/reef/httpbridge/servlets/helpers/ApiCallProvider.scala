/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.httpbridge.servlets.helpers

import com.google.protobuf.Message
import org.totalgrid.reef.client.sapi.client.Promise
import org.totalgrid.reef.client.sapi.client.rest.Client

/**
 * ApiCall and its implementations are used to encapsulate an action we can take with a Client
 * that will produce a Promise() with data in it. We only require that the result of the promise
 * be a protobuf to make sure it is serializable.
 */
sealed trait ApiCall
case class SingleResultApiCall[A <: Message](executeFunction: (Client) => Promise[A]) extends ApiCall
case class OptionalResultApiCall[A <: Message](executeFunction: (Client) => Promise[Option[A]]) extends ApiCall
case class MultiResultApiCall[A <: Message](executeFunction: (Client) => Promise[List[A]]) extends ApiCall

/**
 * a container for looking up and preparing an ApiCall. It will parse the arguments
 * and be 100% ready to be sent to the server if this method returns not None
 */
trait ApiCallProvider {
  def prepareApiCall(function: String, args: ArgumentSource): Option[ApiCall]
}

/**
 * base trait for classes that are defining api functions. It provides helpers to make defining
 * a map of the available calls simple enough to enable generating the bindings. The call is done
 * in two steps, first arguments are collected, then the specific api call is executed. This makes
 * generation a bit easier and enables us to use a fake ArgumentSource to capture the necessary
 * arguments. We also require that the binding define the return class, this is not needed by the
 * type system, but it is useful to make the functions fully self describing.
 */
trait ApiCallLibrary[ServiceClass] extends ApiCallProvider {

  /**
   * each api library can define which service interfaces they want (AllScadaService, LoaderService, etc)
   */
  def serviceClass: Class[ServiceClass]

  /**
   * map that gets populated by the calls to single, optional and multi
   */
  private var apiCalls = Map.empty[String, PreparableApiCall[_]]

  /**
   * define a binding for a function that returns 1 resultClass object or throws exception
   */
  protected def single[A <: Message](name: String, resultClass: Class[A], prepareFunction: (ArgumentSource) => ((ServiceClass) => Promise[A])) = {
    val preparer = (args: ArgumentSource) => singleCall(prepareFunction(args))
    apiCalls += name -> PreparableApiCall(resultClass, preparer)
  }
  /**
   * define a binding for a function that returns 0 or 1 resultClass object or throws exception
   */
  protected def optional[A <: Message](name: String, resultClass: Class[A], prepareFunction: (ArgumentSource) => ((ServiceClass) => Promise[Option[A]])) = {
    val preparer = (args: ArgumentSource) => optionalCall(prepareFunction(args))
    apiCalls += name -> PreparableApiCall(resultClass, preparer)
  }
  /**
   * define a binding for a function that a list of resultClass object or throws exception
   */
  protected def multi[A <: Message](name: String, resultClass: Class[A], prepareFunction: (ArgumentSource) => ((ServiceClass) => Promise[List[A]])) = {
    val preparer = (args: ArgumentSource) => multiCall(prepareFunction(args))
    apiCalls += name -> PreparableApiCall(resultClass, preparer)
  }

  /**
   * implement ApiCallProvider interface
   */
  def prepareApiCall(function: String, args: ArgumentSource): Option[ApiCall] = {
    apiCalls.get(function).map(_.prepareFunction(args))
  }

  private case class PreparableApiCall[A <: Message](resultClass: Class[A], prepareFunction: (ArgumentSource) => ApiCall)

  /**
   * these helper functions allow us to keep the ApiCall interface "ServicesList" agnostic
   * and we convert to specific serviceClass
   */
  private def singleCall[A <: Message](executeFunction: (ServiceClass) => Promise[A]) = {
    SingleResultApiCall(c => executeFunction(c.getRpcInterface(serviceClass)))
  }
  private def optionalCall[A <: Message](executeFunction: (ServiceClass) => Promise[Option[A]]) = {
    OptionalResultApiCall(c => executeFunction(c.getRpcInterface(serviceClass)))
  }
  private def multiCall[A <: Message](executeFunction: (ServiceClass) => Promise[List[A]]) = {
    MultiResultApiCall(c => executeFunction(c.getRpcInterface(serviceClass)))
  }
}
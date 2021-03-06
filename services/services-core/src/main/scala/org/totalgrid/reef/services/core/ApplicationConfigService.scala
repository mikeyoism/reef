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
package org.totalgrid.reef.services.core

import org.totalgrid.reef.client.service.proto.Application._
import org.totalgrid.reef.services.framework._

import org.totalgrid.reef.client.service.proto.Descriptors
import org.totalgrid.reef.client.exception.BadRequestException

import org.squeryl.PrimitiveTypeMode._
import org.totalgrid.reef.client.service.proto.OptionalProtos._
import org.squeryl.Query
import java.util.UUID
import org.totalgrid.reef.models.{ Command, ApplicationNetworkAccess, Agent => AgentModel, ApplicationInstance, ApplicationSchema, ApplicationCapability }
import org.totalgrid.reef.authz.VisibilityMap

// implicit proto properties
import SquerylModel._ // implict asParam
import org.totalgrid.reef.models.UUIDConversions._
import org.totalgrid.reef.client.sapi.types.Optional._

import scala.collection.JavaConversions._

class ApplicationConfigService(val model: ApplicationConfigServiceModel)
    extends SyncModeledServiceBase[ApplicationConfig, ApplicationInstance, ApplicationConfigServiceModel]
    with DefaultSyncBehaviors {

  override val descriptor = Descriptors.applicationConfig
}

class ApplicationConfigServiceModel(procStatusModel: ProcessStatusServiceModel)
    extends SquerylServiceModel[Long, ApplicationConfig, ApplicationInstance]
    with EventedServiceModel[ApplicationConfig, ApplicationInstance]
    with ApplicationConfigConversion {

  val entityModel = new EntityServiceModel
  val agentModel = new AgentServiceModel

  private def createModelEntry(context: RequestContext, proto: ApplicationConfig, agent: AgentModel): ApplicationInstance = {
    val ent = entityModel.findOrCreate(context, proto.getInstanceName, "Application" :: Nil, None) //EntityQuery.findOrCreateEntity(proto.getInstanceName, "Application" :: Nil, None)
    val a = new ApplicationInstance(ent.id, proto.getInstanceName, agent.id, proto.version.getOrElse("unknown"), proto.getLocation)
    a.entity.value = ent
    a
  }

  override def createFromProto(context: RequestContext, req: ApplicationConfig): ApplicationInstance = {
    val sql = create(context, createModelEntry(context: RequestContext, req, context.agent))

    val caps = req.getCapabilitesList.toList
    ApplicationSchema.capabilities.insert(caps.map { x => new ApplicationCapability(sql.id, x) })

    val networks = getNetworks(req)
    ApplicationSchema.networks.insert(networks.map { x => new ApplicationNetworkAccess(sql.id, x) })

    // TODO: make heartbeating a capability
    val time = if (req.hasHeartbeatCfg) req.getHeartbeatCfg.getPeriodMs else 60000
    procStatusModel.addApplication(context, sql, time, req.getProcessId, caps)

    sql
  }

  private def updateCapabilites(req: ApplicationConfig, sql: ApplicationInstance): (List[String], List[String]) = {
    val newCaps: List[String] = req.getCapabilitesList.toList
    val oldCaps: List[String] = sql.capabilities.value.toList

    val addedCaps = newCaps.diff(oldCaps)
    val removedCaps = oldCaps.diff(newCaps)

    ApplicationSchema.capabilities.insert(addedCaps.map { new ApplicationCapability(sql.id, _) })
    ApplicationSchema.capabilities.deleteWhere(c => c.applicationId === sql.id and (c.capability in removedCaps.map { _.id }))

    (addedCaps, removedCaps)
  }

  private def getNetworks(req: ApplicationConfig): List[String] = {
    var newNetworks: List[String] = req.getNetworksList.toList

    if (req.hasNetwork) newNetworks ::= req.getNetwork

    newNetworks
  }

  private def updateNetworks(req: ApplicationConfig, sql: ApplicationInstance): Boolean = {

    val newNetworks: List[String] = getNetworks(req)
    val oldNetworks: List[String] = sql.networks.value.toList

    val addedNetworks = newNetworks.diff(oldNetworks)
    val removedNetworks = oldNetworks.diff(newNetworks)

    ApplicationSchema.networks.insert(addedNetworks.map { new ApplicationNetworkAccess(sql.id, _) })
    ApplicationSchema.networks.deleteWhere(c => c.applicationId === sql.id and (c.network in removedNetworks.map { _.id }))

    !addedNetworks.isEmpty | !removedNetworks.isEmpty
  }

  override def updateFromProto(context: RequestContext, req: ApplicationConfig, existing: ApplicationInstance): (ApplicationInstance, Boolean) = {

    var (sql, updated) = update(context, createModelEntry(context, req, context.agent), existing)

    val (newCaps, removedCaps) = updateCapabilites(req, sql)
    updated |= updateNetworks(req, sql)

    procStatusModel.notifyModels(context, sql, false, removedCaps)

    val time = if (req.hasHeartbeatCfg) req.getHeartbeatCfg.getPeriodMs else 60000
    procStatusModel.addApplication(context, sql, time, req.getProcessId, req.getCapabilitesList.toList)

    (sql, updated)
  }

  override def preDelete(context: RequestContext, sql: ApplicationInstance) {
    procStatusModel.takeApplicationOffline(context, sql)
    procStatusModel.delete(context, sql.heartbeat.value)
  }

  override def postDelete(context: RequestContext, sql: ApplicationInstance) {
    ApplicationSchema.protocols.deleteWhere(c => c.applicationId === sql.id)
    ApplicationSchema.capabilities.deleteWhere(c => c.applicationId === sql.id)
    entityModel.delete(context, sql.entity.value)
  }
}

trait ApplicationConfigConversion
    extends UniqueAndSearchQueryable[ApplicationConfig, ApplicationInstance] {

  val table = ApplicationSchema.apps

  def sortResults(list: List[ApplicationConfig]) = list.sortBy(_.getInstanceName)

  def getRoutingKey(proto: ApplicationConfig) = ProtoRoutingKeys.generateRoutingKey {
    proto.uuid.value :: proto.instanceName :: Nil
  }

  def relatedEntities(entries: List[ApplicationInstance]) = {
    entries.map { _.agent.value.entityId }
  }

  private def resourceId = Descriptors.applicationConfig.id

  private def visibilitySelector(entitySelector: Query[UUID], sql: ApplicationInstance) = {
    sql.id in from(table, ApplicationSchema.agents)((app, agent) =>
      where(
        (app.agentId === agent.id) and (agent.entityId in entitySelector))
        select (app.id))
  }

  override def selector(map: VisibilityMap, sql: ApplicationInstance) = {
    map.selector(resourceId) { visibilitySelector(_, sql) }
  }

  override def searchQuery(context: RequestContext, proto: ApplicationConfig, sql: ApplicationInstance) = {
    def networkQuery(nets: List[String]) = {
      sql.id in from(ApplicationSchema.networks)(sql => where(sql.network in nets) select (sql.applicationId))
    }
    List(
      proto.version.asParam(sql.version === _),
      proto.network.asParam(net => networkQuery(List(net))),
      proto.networks.asParam(nets => networkQuery(nets)),
      proto.location.asParam(sql.location === _))
  }

  override def uniqueQuery(context: RequestContext, proto: ApplicationConfig, sql: ApplicationInstance) = {
    val eSearch = EntitySearch(proto.uuid.value, proto.instanceName, proto.instanceName.map(x => List("Application")))
    List(eSearch.map(es => sql.entityId in EntityPartsSearches.searchQueryForId(context, es, { _.id })).unique)
  }

  def isModified(entry: ApplicationInstance, existing: ApplicationInstance): Boolean = {
    entry.location != existing.location || entry.agentId != existing.agentId || entry.version != existing.version
  }

  def convertToProto(entry: ApplicationInstance): ApplicationConfig = {

    val hbeat = entry.heartbeat.value

    val h = HeartbeatConfig.newBuilder
      .setPeriodMs(hbeat.periodMS)
      .setProcessId(hbeat.processId)
      .setInstanceName(entry.instanceName)

    val b = ApplicationConfig.newBuilder
      .setUuid(makeUuid(entry))
      .setUserName(entry.agent.value.entityName)
      .setInstanceName(entry.instanceName)
      .setNetwork(entry.networks.value.head)
      .setVersion(entry.version)
      .setLocation(entry.location)
      .setHeartbeatCfg(h)
      .setOnline(hbeat.isOnline)
      .setTimesOutAt(hbeat.timeoutAt)
      .setProcessId(hbeat.processId)

    entry.networks.value.foreach { b.addNetworks(_) }

    entry.capabilities.value.foreach { b.addCapabilites(_) }
    b.build
  }
}

object ApplicationConfigConversion extends ApplicationConfigConversion


/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.protocol.dnp3.slave

import org.totalgrid.reef.client.service.proto.Measurements.Measurement
import org.totalgrid.reef.client.service.proto.Mapping.MeasMap

class MeasurementOutputScaler(measList: List[MeasMap]) {

  private val scalingMap = measList.map { m =>
    if (m.hasScaling) Some(m.getPointName -> m.getScaling)
    else None
  }.flatten.toMap

  def scaleMeasurement(m: Measurement) = {
    scalingMap.get(m.getName) match {
      case None => m
      case Some(factor) => m.getType match {
        case Measurement.Type.DOUBLE => m.toBuilder.setDoubleVal(m.getDoubleVal * factor).build
        case Measurement.Type.INT => m.toBuilder.setIntVal((m.getIntVal * factor).toInt).build
        case _ => m
      }
    }
  }
}

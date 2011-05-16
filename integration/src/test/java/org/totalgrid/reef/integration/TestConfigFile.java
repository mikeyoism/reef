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
package org.totalgrid.reef.integration;

import org.junit.Test;
import org.totalgrid.reef.api.ReefServiceException;
import org.totalgrid.reef.api.request.builders.EventConfigRequestBuilders;
import org.totalgrid.reef.api.request.builders.EventRequestBuilders;
import org.totalgrid.reef.integration.helpers.JavaBridgeTestBase;
import org.totalgrid.reef.proto.Model;

public class TestConfigFile extends JavaBridgeTestBase {

    /**
     * example that shows the scala implementation of the api classes can be called from java code
     */
    @Test
    public void testCreatingAndDeletingConfigFile()  throws ReefServiceException {

        Model.Entity ent = helpers.getEntityByName("StaticSubstation");

        Model.ConfigFile created = helpers.createConfigFile("test-config-file", "text", new byte[]{0,0,0}, ent.getUuid());

        helpers.deleteConfigFile(created);

    }
}

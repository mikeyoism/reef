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
package org.totalgrid.reef.client;

import org.totalgrid.reef.client.exception.ReefServiceException;

/**
 * Interface that defines how a connection is created. A concrete implementation of this
 * interface is the only implementation class that an application needs.
 *
 * Connection factory is thread-safe.
 */
public interface ConnectionFactory
{

    /**
     * Attempts to establish a new, single-use connection.
     * @return active connection, ready to be used
     * @throws ReefServiceException if connection cannot be established
     */
    Connection connect() throws ReefServiceException;

    /**
     * Permanently shutdown the underlying machinery for the connection factory. No
     * more calls to connect() are allowed.
     */
    void terminate();
}

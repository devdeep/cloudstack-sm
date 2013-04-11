/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.motion;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.springframework.stereotype.Component;

import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.host.Host;
import com.cloud.utils.exception.CloudRuntimeException;

@Component
public class DataMotionServiceImpl implements DataMotionService {
    @Inject
    List<DataMotionStrategy> strategies;

    @Override
    public void copyAsync(DataObject srcData, DataObject destData,
            AsyncCompletionCallback<CopyCommandResult> callback) {

        if (srcData.getDataStore().getDriver().canCopy(srcData, destData)) {
            srcData.getDataStore().getDriver()
                    .copyAsync(srcData, destData, callback);
            return;
        } else if (destData.getDataStore().getDriver()
                .canCopy(srcData, destData)) {
            destData.getDataStore().getDriver()
                    .copyAsync(srcData, destData, callback);
            return;
        }

        for (DataMotionStrategy strategy : strategies) {
            if (strategy.canHandle(srcData, destData)) {
                strategy.copyAsync(srcData, destData, callback);
                return;
            }
        }
        throw new CloudRuntimeException("can't find strategy to move data");
    }

    @Override
    public void copyAsync(Map<VolumeInfo, DataStore> volumeMap, VirtualMachineTO vmTo,
            Host srcHost, Host destHost, AsyncCompletionCallback<CopyCommandResult> callback) {
        for (DataMotionStrategy strategy : strategies) {
            if (strategy.canHandle(volumeMap, srcHost, destHost)) {
                strategy.copyAsync(volumeMap, vmTo, srcHost, destHost, callback);
                return;
            }
        }
        throw new CloudRuntimeException("can't find strategy to move data");
    }
}

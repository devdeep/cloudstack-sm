// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.xen.resource;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.resource.ServerResource;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.storage.MigrateVolumeAnswer;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.agent.api.to.StorageFilerTO;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.Network;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Task;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VIF;
import com.xensource.xenapi.VM;

@Local(value=ServerResource.class)
public class XenServer610Resource extends XenServer56FP1Resource {
    private static final Logger s_logger = Logger.getLogger(XenServer610Resource.class);

    public XenServer610Resource() {
        super();
    }

    @Override
    protected String getGuestOsType(String stdType, boolean bootFromCD) {
        return CitrixHelper.getXenServer602GuestOsType(stdType, bootFromCD);
    }

    @Override
    protected List<File> getPatchFiles() {
        List<File> files = new ArrayList<File>();
        String patch = "scripts/vm/hypervisor/xenserver/xenserver60/patch";
        String patchfilePath = Script.findScript("" , patch);
        if (patchfilePath == null) {
            throw new CloudRuntimeException("Unable to find patch file " + patch);
        }
        File file = new File(patchfilePath);
        files.add(file);
        return files;
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof MigrateVolumeCommand) {
            return execute((MigrateVolumeCommand) cmd);
        } else {
            return super.executeRequest(cmd);
        }
    }

    protected MigrateVolumeAnswer execute(MigrateVolumeCommand cmd) {
        Connection connection = getConnection();
        String volumeUUID = cmd.getVolumePath();
        StorageFilerTO poolTO = cmd.getPool();

        try {
            SR destinationPool = getStorageRepository(connection, poolTO.getUuid());
            VDI srcVolume = getVDIbyUuid(connection, volumeUUID);
            Map<String, String> other = new HashMap<String, String>();
            other.put("live", "true");

            // Live migrate the vdi across pool.
            Task task = srcVolume.poolMigrateAsync(connection, destinationPool, other);
            long timeout = (_migratewait) * 1000L;
            waitForTask(connection, task, 1000, timeout);
            checkForSuccess(connection, task);
            VDI dvdi = Types.toVDI(task, connection);

            return new MigrateVolumeAnswer(cmd, true, null, dvdi.getUuid(connection));
        } catch (Exception e) {
            String msg = "Catch Exception " + e.getClass().getName() + " due to " + e.toString();
            s_logger.error(msg, e);
            return new MigrateVolumeAnswer(cmd, false, msg, null);
        }
    }
}

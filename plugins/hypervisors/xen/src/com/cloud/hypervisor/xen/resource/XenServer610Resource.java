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
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import javax.ejb.Local;
import org.apache.log4j.Logger;

import com.cloud.resource.ServerResource;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MigrateWithStorageAnswer;
import com.cloud.agent.api.MigrateWithStorageCommand;
import com.cloud.agent.api.storage.MigrateVolumeAnswer;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.agent.api.to.NicTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.network.Networks.TrafficType;
import com.cloud.hypervisor.xen.resource.XenServerConnectionPool.XenServerConnection;
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
        if (cmd instanceof MigrateWithStorageCommand) {
            return execute((MigrateWithStorageCommand) cmd);
        } else if (cmd instanceof MigrateVolumeCommand) {
            return execute((MigrateVolumeCommand) cmd);
        } else {
            return super.executeRequest(cmd);
        }
    }

    private XenServerConnection getCrossPoolConnection(String host, String username, String password) {
        Queue<String> pwdList = new LinkedList<String>();
        pwdList.add(password);
        return _connPool.getCrossPoolConnection(host, username, pwdList);
    }

    protected MigrateWithStorageAnswer execute(MigrateWithStorageCommand cmd) {
        Connection destConn = getConnection();
        Connection srcConn = getCrossPoolConnection(cmd.getUrl(), cmd.getUsername(), cmd.getPassword());
        Task task = null;
        Task task1 = null;
        String vmName = cmd.getVmName();
        VirtualMachineTO vm = cmd.getVirtualMachine();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Preparing host for migrating " + vmName);
        }

        try {
            Map<String,String> result = null;
            Map<String, String> other = new HashMap<String, String>();
            other.put("live", "true");
            Network networkForSm = getNativeNetworkForTraffic(destConn, TrafficType.Storage, null).getNetwork();
            Host host = Host.getByUuid(destConn, _host.uuid);
            result = host.migrateReceive(destConn, networkForSm, other);

            // Step 2
            NicTO[] nics = vm.getNics();
            Network new_network = null;
            for (NicTO nic : nics) {
                new_network = getNetwork(destConn, nic);
                s_logger.warn("-------------getNetwork(destConn, nic)=" + nic.getUuid() + ", " + nic.getName());
                s_logger.warn("-------------Network uuid=" + new_network.getUuid(destConn) + ", " + new_network.getNameLabel(destConn));
            }

            Set<VM> vms = VM.getByNameLabel(srcConn, vm.getName());
            s_logger.warn("----------------vm size = " + vms.size());
            VM mvm = vms.iterator().next();

            Map<VIF, Network> vifMap = new HashMap<VIF, Network>();
            Set<VIF> vifs = mvm.getVIFs(srcConn);
            for (VIF vif : vifs) {
                s_logger.warn("-------------VIF uuid=" + vif.getUuid(srcConn));
                vifMap.put(vif, new_network);
            }

            Map<VDI, SR> vdiMap = new HashMap<VDI, SR>();
            Set<VBD> vbds = mvm.getVBDs(srcConn); 
            s_logger.warn("-------------VBD count=" + vbds.size());
            for( VBD vbd : vbds) {
                s_logger.warn("-------------VBD uuid=" + vbd.getUuid(srcConn) + " type=" + vbd.getType(srcConn));
                if( vbd.getType(srcConn) == Types.VbdType.DISK)  {
                    VDI vdi = vbd.getVDI(srcConn);
                    s_logger.warn("-------------VDI= uuid=" + vdi.getUuid(srcConn));
                    SR sr = vdi.getSR(srcConn);
                    s_logger.warn("-------------SR= uuid=" + sr.getUuid(srcConn));
                    vdiMap.put(vdi, sr);
                }
            }

            task = mvm.assertCanMigrateAsync(srcConn, result, true, vdiMap, vifMap, other);
            try {
                // poll every 1 seconds 
                long timeout = (_migratewait) * 1000L;
                waitForTask(srcConn, task, 1000, timeout);
                checkForSuccess(srcConn, task);
            } catch (Types.HandleInvalid e) {
                throw e;
            }

            Map<VDI, SR> nvdiMap = new HashMap<VDI, SR>();
            Set<SR> srs = SR.getAll(destConn);
            for (SR sr : srs) {
                if (SRType.NFS.equals(sr.getType(destConn))) {
                    s_logger.warn("-------------SR=" + sr.getNameLabel(destConn));
                    for (Map.Entry<VDI, SR> entry : vdiMap.entrySet()) { 
                        VDI tvdi = entry.getKey();
                        nvdiMap.put(tvdi, sr);
                    }
                    break;
                }
            }

            task1 = mvm.migrateSendAsync(srcConn, result, true, nvdiMap, vifMap, other);
            s_logger.warn("-------------migrateSendAsync Task created");
            try {
                // poll every 1 seconds 
                long timeout = (_migratewait) * 1000L;
                waitForTask(srcConn, task1, 1000, timeout);
                checkForSuccess(srcConn, task1);
            } catch (Types.HandleInvalid e) {
                throw e;
            }

            return new MigrateWithStorageAnswer(cmd);
        } catch (Exception e) {
            s_logger.warn("Catch Exception " + e.getClass().getName() +
                    ". Storage motion failed due to " + e.toString(), e);
            return new MigrateWithStorageAnswer(cmd, e);
        } finally {
            if( task != null) {
                try {
                    task.destroy(destConn);
                } catch (Exception e1) {
                    s_logger.debug("Unable to destroy task(" + task.toString() +
                            ") on host(" + _host.uuid +") due to " + e1.toString());
                }
            }

            if( task1 != null) {
                try {
                    task1.destroy(destConn);
                } catch (Exception e2) {
                    s_logger.debug("Unable to destroy task(" + task1.toString() +
                            ") on host(" + _host.uuid +") due to " + e2.toString());
                }
            }
        }
    }

    protected MigrateVolumeAnswer execute(MigrateVolumeCommand cmd) {
        Connection connection = getConnection();
        String volumeUUID = cmd.getVolumePath();
        StorageFilerTO poolTO = cmd.getPool();

        try {
            SR destinationPool = getStorageRepository(connection, poolTO);
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

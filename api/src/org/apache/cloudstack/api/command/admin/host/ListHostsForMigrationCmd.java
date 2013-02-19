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
package org.apache.cloudstack.api.command.admin.host;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.cloudstack.api.APICommand;
import org.apache.log4j.Logger;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiConstants.HostDetails;
import org.apache.cloudstack.api.BaseListCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ClusterResponse;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.PodResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import com.cloud.async.AsyncJob;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;

@APICommand(name = "listHostsForMigration", description="Lists hosts.", responseObject=HostResponse.class)
public class ListHostsForMigrationCmd extends BaseListCmd {
    public static final Logger s_logger = Logger.getLogger(ListHostsForMigrationCmd.class.getName());

    private static final String s_name = "listhostsformigrationresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name=ApiConstants.VIRTUAL_MACHINE_ID, type=CommandType.UUID, entityType = UserVmResponse.class,
            required=false, description="lists hosts in the same cluster as this VM and flag hosts with enough CPU/RAm to host this VM")
    private Long virtualMachineId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute(){
        ListResponse<HostResponse> response = null;
        Pair<List<? extends Host>,Integer> result;
        List<? extends Host> hostsWithCapacity = new ArrayList<Host>();
        Map<Host, Boolean> hostsRequiringStorageMotion;

        Ternary<Pair<List<? extends Host>,Integer>, List<? extends Host>, Map<Host, Boolean>> hostsForMigration = 
                _mgr.listHostsForMigrationOfVM(getVirtualMachineId(), this.getStartIndex(), this.getPageSizeVal());
        result = hostsForMigration.first();
        hostsWithCapacity = hostsForMigration.second();
        hostsRequiringStorageMotion = hostsForMigration.third();

        response = new ListResponse<HostResponse>();
        List<HostResponse> hostResponses = new ArrayList<HostResponse>();
        for (Host host : result.first()) {
            HostResponse hostResponse = _responseGenerator.createHostResponse(host);
            Boolean suitableForMigration = false;
            if (hostsWithCapacity.contains(host)) {
                suitableForMigration = true;
            }
            hostResponse.setSuitableForMigration(suitableForMigration);

            Boolean requiresStorageMotion = hostsRequiringStorageMotion.get(host);
            if (requiresStorageMotion != null && requiresStorageMotion) {
                hostResponse.setRequiresStorageMotion(true);
            } else {
                hostResponse.setRequiresStorageMotion(false);
            }

            hostResponse.setObjectName("host");
            hostResponses.add(hostResponse);
        }

        response.setResponses(hostResponses, result.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);

    }
}

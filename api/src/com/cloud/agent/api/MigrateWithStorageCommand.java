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
package com.cloud.agent.api;

import com.cloud.agent.api.to.VirtualMachineTO;

public class MigrateWithStorageCommand extends Command {
    String vmName;
    VirtualMachineTO vm;
    long srcHostId;
    String url;
    String username;
    String password;
    boolean isWindows;


    protected MigrateWithStorageCommand() {
    }

    public MigrateWithStorageCommand(VirtualMachineTO vm, String vmName, long srcHostId, String url, String username, String password, boolean isWindows) {
        this.vmName = vmName;
        this.isWindows = isWindows;
        this.vm = vm;
        this.srcHostId = srcHostId;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public MigrateWithStorageCommand(VirtualMachineTO vm, String vmName, long srcHostId, boolean isWindows) {
        this.vmName = vmName;
        this.isWindows = isWindows;
        this.vm = vm;
        this.srcHostId = srcHostId;
        this.url = null;
        this.username = null;
        this.password = null;
    }

    public boolean isWindows() {
        return isWindows;
    }

    public String getVmName() {
        return vmName;
    }

    public VirtualMachineTO getVirtualMachine() {
        return vm;
    }

    public long getSrcHostId() {
        return srcHostId;
    }
    
    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean executeInSequence() {
        return true;
    }
}

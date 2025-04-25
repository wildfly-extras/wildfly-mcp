/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class VMInfo {
    public String name;
    public String specName;
    public String specVendor;
    public String specVersion;
    public String vmName;
    public String vmVendor;
    public String vmVersion;
    public List<String> inputArguments = new ArrayList<>();
    public String startTime;
    public String upTime;
    public String consumedMemory;
    public String consumedCPU;
    public String systemLoadAverage;
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import java.util.ArrayList;
import java.util.List;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise
 */
public class WildFlyDMRStatus {

    private static final String RUNNING = "running";
    private static final String NORMAL = "NORMAL";
    private static final String SUCCESS = "success";
    private static final String OK = "OK";

    private final String serverState;
    private final String runningMode;
    private final List<ModelNode> bootErrors;
    private final List<ModelNode> deploymentsStatus;

    WildFlyDMRStatus(String serverState, String runningMode, List<ModelNode> bootErrors, List<ModelNode> deploymentsStatus) {
        this.serverState = serverState;
        this.runningMode = runningMode;
        this.bootErrors = bootErrors;
        this.deploymentsStatus = deploymentsStatus;
    }

    public boolean isOk() {
        return serverState.equals(RUNNING) && runningMode.equals(NORMAL) && bootErrors.isEmpty() && allDeploymentsOK();
    }

    private boolean allDeploymentsOK() {
        for (ModelNode d : deploymentsStatus) {
            if (!d.get("outcome").asString().equals(SUCCESS) || !d.get("result").asString().equals(OK)) {
                return false;
            }
        }
        return true;
    }

    public List<String> getStatus() {
        List<String> res = new ArrayList<>();
        res.add("Server state: " + serverState);
        res.add("Running Mode: " + runningMode);
        res.add("Boot errors: " + bootErrors);
        res.add("Deployments: " + deploymentsStatus);
        return res;
    }
}

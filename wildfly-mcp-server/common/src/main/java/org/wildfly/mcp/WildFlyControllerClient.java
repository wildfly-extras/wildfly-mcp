/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise
 */
public class WildFlyControllerClient {

    public static class Deployment {

        public List<Map<String, String>> address;
        public String outcome;
        public String result;
        @JsonProperty("rolled-back")
        public String rolledBack;

        @Override
        public String toString() {
            if (address.isEmpty()) {
                return "No deployments.";
            }
            return " Deployment : " + address.get(address.size() - 1).get("deployment") + "\n, status " + result + ", outcome " + outcome;
        }
    }

    public abstract static class ManagementRequest {

        public final List<String> address = new ArrayList<>();
        public final String operation;

        private final Server server;
        private final User user;
        private final boolean stream;

        protected ManagementRequest(String operation, Server server, User user) {
            this(operation, server, user, false);
        }

        protected ManagementRequest(String operation, Server server, User user, boolean stream) {
            this.operation = operation;
            this.server = server;
            this.user = user;
            this.stream = stream;
        }

        protected void addArguments(ModelNode op) {

        }

        String getResponse(ModelNode resp) throws JsonProcessingException {
            return resp.toJSONString(false);
        }
    }

    public static class CheckDeploymentRequest extends ManagementRequest {

        CheckDeploymentRequest(Server server, User user, String name) {
            super("read-resource", server, user);
            address.add("deployment");
            address.add(name);
        }
    }
    
    public static class UndeployRequest extends ManagementRequest {

        UndeployRequest(Server server, User user, String name) {
            super("remove", server, user);
            address.add("deployment");
            address.add(name);
        }
    }
    
    public static class ShutdownRequest extends ManagementRequest {
        
        private final Integer timeout;
        ShutdownRequest(Server server, User user, Integer timeout) {
            super("shutdown", server, user);
            this.timeout = timeout;
        }
        @Override
        protected void addArguments(ModelNode op) {
            if(timeout != null) {
            op.get("timeout").set(timeout);
            }
        }
    }

    public static class AddLoggerRequest extends ManagementRequest {

        AddLoggerRequest(Server server, User user, String category) {
            super("add", server, user);
            address.add("subsystem");
            address.add("logging");
            address.add("logger");
            address.add(category);
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("level").set("ALL");
        }
    }

    public static class GetAbstractMXBean extends ManagementRequest {

        GetAbstractMXBean(Server server, User user, String name) {
            super("read-resource", server, user);
            address.add("core-service");
            address.add("platform-mbean");
            address.add("type");
            address.add(name);
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("include-runtime").set(true);
        }
    }

    public static class GetRuntimeMXBean extends GetAbstractMXBean {

        GetRuntimeMXBean(Server server, User user) {
            super(server, user, "runtime");
        }
    }

    public static class GetLoggingMXBean extends GetAbstractMXBean {

        GetLoggingMXBean(Server server, User user) {
            super(server, user, "platform-logging");
        }
    }

    public static class GetMemoryMXBean extends GetAbstractMXBean {

        GetMemoryMXBean(Server server, User user) {
            super(server, user, "memory");
        }
    }

    public static class GetOperatingSystemMXBean extends GetAbstractMXBean {

        GetOperatingSystemMXBean(Server server, User user) {
            super(server, user, "operating-system");
        }
    }

    public static class EnableLoggerRequest extends ManagementRequest {

        EnableLoggerRequest(Server server, User user, String category) {
            super("write-attribute", server, user);
            address.add("subsystem");
            address.add("logging");
            address.add("logger");
            address.add(category);
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("name").set("level");
            op.get("value").set("ALL");
        }
    }

    public abstract static class ReadAttributeRequest extends ManagementRequest {

        public String name;

        protected ReadAttributeRequest(Server server, User user, String attribute) {
            super("read-attribute", server, user);
            this.name = attribute;
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("name").set(name);
        }
    }

    public static class ReadServerStateRequest extends ReadAttributeRequest {

        ReadServerStateRequest(Server server, User user) {
            super(server, user, "server-state");
        }
    }

    public static class ReadRunningModeRequest extends ReadAttributeRequest {

        ReadRunningModeRequest(Server server, User user) {
            super(server, user, "running-mode");
        }
    }

    public static class ReadBootErrorsRequest extends ManagementRequest {

        ReadBootErrorsRequest(Server server, User user) {
            super("read-boot-errors", server, user);
            address.add("core-service");
            address.add("management");
        }
    }

    public static class ReadConfigAsXmlRequest extends ManagementRequest {

        ReadConfigAsXmlRequest(Server server, User user) {
            super("read-config-as-xml-file", server, user, true);
        }
    }

    public static class ReadDeploymentsStatusRequest extends ReadAttributeRequest {

        ReadDeploymentsStatusRequest(Server server, User user) {
            super(server, user, "status");
            address.add("deployment");
            address.add("*");
        }
    }

    public static class GetLoggingFileRequest extends ManagementRequest {

        public int lines;

        GetLoggingFileRequest(Server server, String numLines, User user) {
            super("read-log-file", server, user);
            if (numLines == null || numLines.isEmpty()) {
                numLines = "200";
            }
            lines = Integer.parseInt(numLines);
            address.add("subsystem");
            address.add("logging");
            address.add("log-file");
            address.add("server.log");
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("lines").set(lines);
        }
    }

    public static class GetLoggersRequest extends ManagementRequest {

        GetLoggersRequest(Server server, User user) {
            super("read-children-names", server, user);
            address.add("subsystem");
            address.add("logging");
        }

        @Override
        protected void addArguments(ModelNode op) {
            op.get("child-type").set("logger");
        }
    }

    public static class RemoveLoggerRequest extends ManagementRequest {

        RemoveLoggerRequest(Server server, User user, String category) {
            super("remove", server, user);
            address.add("subsystem");
            address.add("logging");
            address.add("logger");
            address.add(category);
        }
    }

    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyManagementClient");

    private ModelControllerClient buildController(Server server, User user) {
        final ModelControllerClientConfiguration.Builder builder = new ModelControllerClientConfiguration.Builder()
                .setHostName(server.host)
                .setPort(Integer.parseInt(server.port));
        if (user.userName != null) {
            builder.setHandler(new ClientCallbackHandler(user.userName, user.userPassword));
        }
        return ModelControllerClient.Factory.create(builder.build());
    }

    public ModelNode call(ManagementRequest request) throws Exception {
        ModelNode addr;
        if (request.address.isEmpty()) {
            addr = new ModelNode().setEmptyList();
        } else {
            addr = Operations.createAddress(request.address);
        }
        ModelNode dmr = Operations.createOperation(request.operation, addr);
        request.addArguments(dmr);
        ModelControllerClient client = buildController(request.server, request.user);
        return client.execute(dmr);

    }

    public ModelNode call(Server server, User user, ModelNode op) throws Exception {
        ModelControllerClient client = buildController(server, user);
        ModelNode mn = client.execute(op);
        return mn;
    }
    public OperationResponse callOperation(Server server, User user, ModelNode op) throws Exception {
        ModelControllerClient client = buildController(server, user);
        OperationBuilder opBuilder = new OperationBuilder(op);
        OperationResponse mn = client.executeOperation(opBuilder.build(), OperationMessageHandler.DISCARD);
        return mn;
    }
    public OperationResponse callOperation(Server server, User user, ModelNode op, String deploymentPath) throws Exception {
        ModelControllerClient client = buildController(server, user);
        OperationBuilder opBuilder = new OperationBuilder(op);
        opBuilder.addFileAsAttachment(new File(deploymentPath));
        OperationResponse mn = client.executeOperation(opBuilder.build(), OperationMessageHandler.DISCARD);
        return mn;
    }
    public WildFlyDMRStatus getStatus(Server server, User user) throws Exception {
        String serverState = call(new ReadServerStateRequest(server, user)).get("result").asString();
        String runningMode = call(new ReadRunningModeRequest(server, user)).get("result").asString();
        List<ModelNode> deployments = call(new ReadDeploymentsStatusRequest(server, user)).get("result").asList();
        List<ModelNode> bootErrors = call(new ReadBootErrorsRequest(server, user)).get("result").asList();
        return new WildFlyDMRStatus(serverState, runningMode, bootErrors, deployments);
    }

    private boolean isAuthenticationError(int code) {
        return code == 401;
    }

    private boolean isForbiddenError(int code) {
        return code == 403;
    }

    public static String getAttachment(OperationResponse response) throws CommandLineException {
        String uuid = response.getResponseNode().get("result").get("uuid").asString();
        return getStream(uuid, response);
    }

    private static String getStream(String uuid, OperationResponse response)
            throws CommandLineException {
        OperationResponse.StreamEntry entry = response.getInputStream(uuid);
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            while ((bytesRead = entry.getStream().read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return new String(out.toByteArray());
        } catch (IOException ex) {
            throw new CommandLineException("Exception reading stream ", ex);
        }
    }
}

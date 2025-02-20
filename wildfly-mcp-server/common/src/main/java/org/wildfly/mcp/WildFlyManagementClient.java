/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author jdenise
 */
public class WildFlyManagementClient {
    
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

    public abstract static class ManagementRequest<R extends ManagementResponse> {
        
        public final List<String> address = new ArrayList<>();
        public final String operation;
        
        private final Server server;
        private final User user;
        private final Class<R> responseClass;
        private final boolean stream;

        protected ManagementRequest(Class<R> responseClass, String operation, Server server, User user) {
            this(responseClass, operation, server, user, false);
        }
        protected ManagementRequest(Class<R> responseClass, String operation, Server server, User user, boolean stream) {
            this.responseClass = responseClass;
            this.operation = operation;
            this.server = server;
            this.user = user;
            this.stream = stream;
        }
        
        String toJson() throws JsonProcessingException {
            ObjectWriter ow = new ObjectMapper().writer();
            return ow.writeValueAsString(this);
        }
        
        R getResponse(String json) throws JsonProcessingException {
            return new ObjectMapper().readValue(json, responseClass);
        }
    }
    
    public static class AddLoggerRequest extends ManagementRequest<ManagementResponse> {
        
        public String level = "ALL";
        
        AddLoggerRequest(Server server, User user, String category) {
            super(ManagementResponse.class, "add", server, user);
            address.add("subsystem");
            address.add("logging");
            address.add("logger");
            address.add(category);
        }
    }
    
    public abstract static class ReadAttributeRequest<R extends ReadAttributeResponse> extends ManagementRequest<R> {
        
        public String name;
        
        protected ReadAttributeRequest(Class<R> responseClass, Server server, User user, String attribute) {
            super(responseClass, "read-attribute", server, user);
            this.name = attribute;
        }
    }
    
    public static class ReadServerStateRequest extends ReadAttributeRequest<ReadAttributeResponse> {
        
        ReadServerStateRequest(Server server, User user) {
            super(ReadAttributeResponse.class, server, user, "server-state");
        }
    }
    
    public static class ReadRunningModeRequest extends ReadAttributeRequest<ReadAttributeResponse> {
        
        ReadRunningModeRequest(Server server, User user) {
            super(ReadAttributeResponse.class, server, user, "running-mode");
        }
    }
    
    public static class ReadBootErrorsRequest extends ManagementRequest<ReadBootErrorsResponse> {
        
        ReadBootErrorsRequest(Server server, User user) {
            super(ReadBootErrorsResponse.class, "read-boot-errors", server, user);
            address.add("core-service");
            address.add("management");
        }
    }
    
    public static class ReadConfigAsXmlRequest extends ManagementRequest<ReadConfigAsXmlResponse> {
        
        ReadConfigAsXmlRequest(Server server, User user) {
            super(ReadConfigAsXmlResponse.class, "read-config-as-xml-file", server, user, true);
        }

        @Override
        ReadConfigAsXmlResponse getResponse(String fileContent) throws JsonProcessingException {
            ReadConfigAsXmlResponse resp = new ReadConfigAsXmlResponse();
            resp.result = fileContent;
            return resp;
        }
    }
    
    public static class ReadDeploymentsStatusRequest extends ReadAttributeRequest<ReadDeploymentsStatusResponse> {
        
        ReadDeploymentsStatusRequest(Server server, User user) {
            super(ReadDeploymentsStatusResponse.class, server, user, "status");
            address.add("deployment");
            address.add("*");
        }
    }
    
    public static class GetLoggingFileRequest extends ManagementRequest<GetLoggingFileResponse> {
        
        public String name = "server.log";
        public String lines;
        
        GetLoggingFileRequest(Server server, String numLines, User user) {
            super(GetLoggingFileResponse.class, "read-log-file", server, user);
            if (numLines == null || numLines.isEmpty()) {
                numLines = "200";
            }
            lines = numLines;
            address.add("subsystem");
            address.add("logging");
            address.add("log-file");
            address.add("server.log");
        }
    }
    
    public static class GetLoggersRequest extends ManagementRequest<GetLoggersResponse> {
        
        @JsonProperty("child-type")
        public String childType = "logger";
        
        GetLoggersRequest(Server server, User user) {
            super(GetLoggersResponse.class, "read-children-names", server, user);
            address.add("subsystem");
            address.add("logging");
        }
    }
    
    public static class RemoveLoggerRequest extends ManagementRequest<ManagementResponse> {
        
        RemoveLoggerRequest(Server server, User user, String category) {
            super(ManagementResponse.class, "remove", server, user);
            address.add("subsystem");
            address.add("logging");
            address.add("logger");
            address.add(category);
        }
    }
    
    public static class ManagementResponse {
        
        public String outcome;
        @JsonProperty("failure-description")
        public String failureDescription;
        @JsonProperty("response-headers")
        Map<String, Object> responseHeaders;
    }
    
    public static class GetLoggersResponse extends ManagementResponse {
        
        public Set<String> result;
    }
    
    public static class GetLoggingFileResponse extends ManagementResponse {
        
        public List<String> result;
    }
    
    public static class ReadAttributeResponse extends ManagementResponse {
        
        public String result;
    }
    
    public static class ReadConfigAsXmlResponse extends ManagementResponse {
        
        public String result;
    }
    
    public static class ReadDeploymentsStatusResponse extends ReadAttributeResponse {
        
        public List<Deployment> result;
    }
    
    public static class ReadBootErrorsResponse extends ManagementResponse {
        
        public List<Object> result;
    }
    
    static Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyManagementClient");
    
    public <T extends ManagementResponse> T call(ManagementRequest<T> request) throws Exception {
       return request.getResponse(call(request.server, request.user, request.toJson(), request.stream));
        
    }

    public String call(Server server, User user, ParsedCommandLine parsedLine) throws Exception {
        return call(server, user, toJSON(parsedLine), false);
    }
    public String call(Server server, User user, String json, boolean stream) throws Exception {
        HttpClientBuilder builder = HttpClients.custom();
        if (user != null && user.userName != null && user.userPassword != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(server.host, Integer.parseInt(server.port), "ManagementRealm", "digest"),
                    new UsernamePasswordCredentials(user.userName, user.userPassword));
            builder.setDefaultCredentialsProvider(credsProvider);
        }
        try (CloseableHttpClient httpclient = builder.build()) {
            HttpPost httppost = new HttpPost("http://" + server.host + ":" + server.port + "/management" + (stream ? "/?useStreamAsResponse" : ""));
            StringEntity requestEntity = new StringEntity(
                    json,
                    ContentType.APPLICATION_JSON);
            httppost.setEntity(requestEntity);
            LOGGER.info("Executing request " + httppost.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httppost);
            try {
                int code = response.getStatusLine().getStatusCode();
                if (isAuthenticationError(code)) {
                    throw new AuthenticationException("Authentication error, could you check the provided username and password.");
                }
                if (isForbiddenError(code)) {
                    throw new ForbiddenException("Authentication error, the provided user is not allowed to interact with the server.");
                }
                LOGGER.info("----------------------------------------");
                LOGGER.info(response.getStatusLine().toString());
                String reply = EntityUtils.toString(response.getEntity());
                LOGGER.info(reply);
                return reply;
            } finally {
                response.close();
            }
        }
    }
    
    public static String toJSON(ParsedCommandLine parsedLine)
            throws CommandFormatException {
        if (parsedLine.getFormat() != OperationFormat.INSTANCE) {
            throw new OperationFormatException("The line does not follow the operation request format");
        }
        ModelNode request = new ModelNode();
        ModelNode addressNode = request.get(Util.ADDRESS);
        if (parsedLine.getAddress().isEmpty()) {
            addressNode.setEmptyList();
        } else {
            Iterator<OperationRequestAddress.Node> iterator = parsedLine.getAddress().iterator();
            while (iterator.hasNext()) {
                OperationRequestAddress.Node node = iterator.next();
                if (node.getName() != null) {
                    addressNode.add(node.getType(), node.getName());
                } else if (iterator.hasNext()) {
                    throw new OperationFormatException(
                            "The node name is not specified for type '"
                            + node.getType() + "'");
                }
            }
        }
        
        final String operationName = parsedLine.getOperationName();
        if (operationName == null || operationName.isEmpty()) {
            throw new OperationFormatException("The operation name is missing or the format of the operation request is wrong.");
        }
        request.get(Util.OPERATION).set(operationName);
        for (String propName : parsedLine.getPropertyNames()) {
            String value = parsedLine.getPropertyValue(propName);
            if (propName == null || propName.trim().isEmpty()) {
                throw new OperationFormatException("The argument name is not specified: '" + propName + "'");
            }
            if (value == null || value.trim().isEmpty()) {
                throw new OperationFormatException("The argument value is not specified for " + propName + ": '" + value + "'");
            }
            final ModelNode toSet = ModelNode.fromString(value);
            request.get(propName).set(toSet);
        }
        return request.toJSONString(false);
    }

    public WildFlyStatus getStatus(Server server, User user) throws Exception {
        String serverState = call(new ReadServerStateRequest(server, user)).result;
        String runningMode = call(new ReadRunningModeRequest(server, user)).result;
        List<Deployment> deployments = call(new ReadDeploymentsStatusRequest(server, user)).result;
        List<Object> bootErrors = call(new ReadBootErrorsRequest(server, user)).result;
        return new WildFlyStatus(serverState, runningMode, bootErrors, deployments);
    }
    
    private boolean isAuthenticationError(int code) {
        return code == 401;
    }
    
    private boolean isForbiddenError(int code) {
        return code == 403;
    }
}

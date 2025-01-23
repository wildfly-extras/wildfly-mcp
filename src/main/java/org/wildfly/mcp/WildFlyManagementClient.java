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

        protected ManagementRequest(Class<R> responseClass, String operation, Server server, User user) {
            this.responseClass = responseClass;
            this.operation = operation;
            this.server = server;
            this.user = user;
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

    public static class ReadDeploymentsStatusResponse extends ReadAttributeResponse {

        public List<Deployment> result;
    }

    public static class ReadBootErrorsResponse extends ManagementResponse {

        public List<Object> result;
    }

    static Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyManagementClient");

    public <T extends ManagementResponse> T call(ManagementRequest<T> request) throws Exception {
        HttpClientBuilder builder = HttpClients.custom();
        if (request.user != null) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(request.server.host, Integer.parseInt(request.server.port), "ManagementRealm", "digest"),
                    new UsernamePasswordCredentials(request.user.userName, request.user.userPassword));
            builder.setDefaultCredentialsProvider(credsProvider);
        }
        try (CloseableHttpClient httpclient = builder.build()) {
            HttpPost httppost = new HttpPost("http://" + request.server.host + ":" + request.server.port + "/management");
            StringEntity requestEntity = new StringEntity(
                    request.toJson(),
                    ContentType.APPLICATION_JSON);
            httppost.setEntity(requestEntity);
            LOGGER.info("Executing request " + httppost.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(httppost);
            try {
                int code = response.getStatusLine().getStatusCode();
                if (isAuthenticationError(code)) {
                    throw new AuthenticationException("Authentication error, could you check the provided username and passord.");
                }
                if (isForbiddenError(code)) {
                    throw new ForbiddenException("Authentication error, the provided user is not allowed to interact with the server.");
                }
                LOGGER.info("----------------------------------------");
                LOGGER.info(response.getStatusLine().toString());
                String json = EntityUtils.toString(response.getEntity());
                LOGGER.info(json);
                return request.getResponse(json);
            } finally {
                response.close();
            }
        }
    }

    public WildFlyStatus getStatus(Server server) throws Exception {
        String serverState = call(new ReadServerStateRequest(server, null)).result;
        String runningMode = call(new ReadRunningModeRequest(server, null)).result;
        List<Deployment> deployments = call(new ReadDeploymentsStatusRequest(server, null)).result;
        List<Object> bootErrors = call(new ReadBootErrorsRequest(server, null)).result;
        return new WildFlyStatus(serverState, runningMode, bootErrors, deployments);
    }

    private boolean isAuthenticationError(int code) {
        return code == 401;
    }

    private boolean isForbiddenError(int code) {
        return code == 403;
    }
}

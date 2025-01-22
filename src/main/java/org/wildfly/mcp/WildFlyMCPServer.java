/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import io.quarkiverse.mcp.server.TextContent;
import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.User.NullUserException;
import org.wildfly.mcp.WildFlyManagementClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersResponse;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileResponse;
import org.wildfly.mcp.WildFlyManagementClient.RemoveLoggerRequest;

public class WildFlyMCPServer {

    static final Logger LOGGER = Logger.getLogger("org.wildfly.mcp.WildFlyMCPServer");

    public record Status(
            String name,
            String outcome,
            List<HealthValue> data) {

    }

    public record HealthValue(
            String value) {

    }

    WildFlyManagementClient wildflyClient = new WildFlyManagementClient();
    @RestClient
    WildFlyMetricsClient wildflyMetricsClient;
    @RestClient
    WildFlyHealthClient wildflyHealthClient;

    @Tool(description = "Get the list of the enabled logging categories for the WildFly server running on the provided host and port arguments. User name and password must be provided.")
    ToolResponse getWildFlyLoggingCategories(String host, String port, 
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName, 
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        try {
            User user = new User(userName, userPassword);
            LOGGER.info("Received user " + user.userName + " password " + user.userPassword);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(host, port, user));
            Set<String> enabled = new TreeSet<>();
            for (String e : response.result) {
                enabled.addAll(getHighLevelCategory(e));
            }
            return buildResponse("The list of enabled logging caterories is: " + enabled);
        } catch (Exception ex) {
            return handleException(ex, host, port, "retrieving the logging categories");
        }
    }

    @Tool(description = "Enable a logging category for the WildFly server running on the provided host and port arguments. User name and password must be provided.")
    ToolResponse enableWildFlyLoggingCategory(String host, String port, String loggingCategory, 
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName, 
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        try {
            User user = new User(userName, userPassword);
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(host, port, user));
            if (response.result != null && response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is already enabled. You can get the enabled logging categories from the getLoggingCategories operation.");
            }
            wildflyClient.call(new AddLoggerRequest(host, port, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been enabled by using the " + category + " logger");
        } catch (Exception ex) {
            return handleException(ex, host, port, "enabling the logger " + loggingCategory);
        }
    }

    @Tool(description = "Disable a logging category for the WildFly server running on the provided host and port arguments. User name and password must be provided.")
    ToolResponse disableWildFlyLoggingCategory(String host, String port, String loggingCategory, 
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName, 
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        try {
            User user = new User(userName, userPassword);
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(host, port, user));
            if (response.result != null && !response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is not already enabled, you should first enabled it.");
            }
            wildflyClient.call(new RemoveLoggerRequest(host, port, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been removed by using the " + category + " logger.");
        } catch (Exception ex) {
            return handleException(ex, host, port, "disabling the logger " + loggingCategory);
        }
    }

    @Tool(description = "Get the log file content of the WildFly server running on the provided host and port arguments. User name and password must be provided.")
    ToolResponse getWildFlyLogFileContent(String host, String port, 
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName, 
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        try {
            User user = new User(userName, userPassword);
            GetLoggingFileResponse response = wildflyClient.call(new GetLoggingFileRequest(host, port, user));
            StringBuilder builder = new StringBuilder();
            for (String line : response.result) {
                builder.append(line).append("\n");
            }
            return buildResponse("WildFly server log file Content: `" + builder.toString() + "`");
        } catch (Exception ex) {
            return handleException(ex, host, port, "retrieving the log file ");
        }
    }

    @Tool(description = "Get the status of the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyStatus(String host, String port) {
        try {
            String url = "http://" + host + ":" + port + "/health";
            List<Status> statusList = wildflyHealthClient.getHealth(url);
            String consumedMemory = getWildFlyConsumedMemory(host, port).content().get(0).asText().text();
            String cpuUsage = getWildFlyConsumedCPU(host, port).content().get(0).asText().text();
            return buildResponse("Server is running. \n" + consumedMemory + "\n" + cpuUsage);
        } catch (Exception ex) {
            return handleException(ex, host, port, "retrieving the status ");
        }
    }

    @Tool(description = "Get the percentage of memory consumed by the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyConsumedMemory(String host, String port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics";
            String metrics = wildflyMetricsClient.getMetrics(url);
            double max = 0;
            double used = 0;
            for (String l : metrics.split("\\n")) {
                if (l.startsWith("base_memory_maxHeap_bytes")) {
                    max = Double.parseDouble(l.substring(l.indexOf(" ")));
                } else {
                    if (l.startsWith("base_memory_usedHeap_bytes")) {
                        used = Double.parseDouble(l.substring(l.indexOf(" ")));
                    }
                }
            }
            double result = (used * 100) / max;
            //int remains = 100 - (int)result;
            return buildResponse("The percentage of consumed memory is " + (int) result + "%");
        } catch (Exception ex) {
            return handleException(ex, host, port, "retrieving the consumed memory");
        }
    }

    @Tool(description = "Get the percentage of cpu consumed by the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyConsumedCPU(String host, String port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics";
            String metrics = wildflyMetricsClient.getMetrics(url);
            double val = 0;
            for (String l : metrics.split("\\n")) {
                if (l.startsWith("base_cpu_processCpuLoad")) {
                    val = Double.parseDouble(l.substring(l.indexOf(" ")));
                    break;
                }
            }
            return buildResponse("The percentage of consumed cpu is " + (int) val * 100 + "%");
        } catch (Exception ex) {
            return handleException(ex, host, port, "retrieving the consumed CPU");
        }
    }

    @RegisterRestClient(baseUri = "http://foo:9990/metrics/")
    public interface WildFlyMetricsClient {

        @GET
        String getMetrics(@Url String url);
    }

    @RegisterRestClient(baseUri = "http://foo:9990/health")
    public interface WildFlyHealthClient {

        @GET
        List<Status> getHealth(@Url String url);
    }

    private String findCategory(String category) {
        if (category.contains("security")) {
            return "org.wildfly.security";
        } else {
            if (category.contains("web") || category.contains("http")) {
                return "io.undertow";
            }
            return category.trim();
        }
    }

    Set<String> getHighLevelCategory(String logger) {
        Set<String> hc = new TreeSet<>();
        if (logger.equals("org.wildfly.security")) {
            hc.add("security");
        } else {
            if (logger.equals("io.undertow")) {
                hc.add("web");
                hc.add("http");
            } else {
                hc.add(logger);
            }
        }
        return hc;
    }

    private ToolResponse handleException(Exception ex, String host, String port, String action) {
        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        if (ex instanceof ClientWebApplicationException clientWebApplicationException) {
            Response resp = clientWebApplicationException.getResponse();
            resp.getStatus();
            return buildErrorResponse(" Error when " + action + " for the server running on host " + host + " and port " + port);
        } else {
            if (ex instanceof AuthenticationException || ex instanceof ForbiddenException) {
                return buildErrorResponse(ex.getMessage());
            } else {
                if (ex instanceof HttpHostConnectException) {
                    return buildErrorResponse(" Error when connecting to the server " + host + ":" + port + ". Could you check the host and port.");
                } else {
                    if (ex instanceof UnknownHostException) {
                        return buildErrorResponse("The server host " + host + " is not a known server name");
                    } else {
                        if (ex instanceof NullUserException) {
                            return buildErrorResponse("A user name and password are required to interact with WildFly management entrypoint.");
                        } else {
                            return buildErrorResponse(ex.getMessage());
                        }
                    }
                }
            }

        }
    }
    
    private ToolResponse buildResponse(String content) {
        return buildResponse(false, content);
    }
    
    private ToolResponse buildErrorResponse(String content) {
        return buildResponse(true, content);
    }

    private ToolResponse buildResponse(boolean isError, String content) {
        TextContent text = new TextContent(content);
        List<TextContent> lst = new ArrayList<>();
        lst.add(text);
        return new ToolResponse(isError, lst);
    }
}

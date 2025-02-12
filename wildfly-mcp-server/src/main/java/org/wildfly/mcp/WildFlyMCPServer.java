/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.sun.management.OperatingSystemMXBean;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.dmr.ModelNode;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.WildFlyManagementClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggersResponse;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyManagementClient.GetLoggingFileResponse;
import org.wildfly.mcp.WildFlyManagementClient.ReadConfigAsXmlRequest;
import org.wildfly.mcp.WildFlyManagementClient.ReadConfigAsXmlResponse;
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
    
    @Tool(description = "Get the list of the enabled logging categories for the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyLoggingCategories(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            Set<String> enabled = new TreeSet<>();
            for (String e : response.result) {
                enabled.addAll(getHighLevelCategory(e));
            }
            return buildResponse("The list of enabled logging caterories is: " + enabled);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }
    
    @Tool(description = "Gets the server configuration xml file content of the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyServerConfigurationFile(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            ReadConfigAsXmlResponse response = wildflyClient.call(new ReadConfigAsXmlRequest(server, user));
            return buildResponse(response.result);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }
    
    @Tool(description = "Invoke a single WildFly CLI operation on the WildFly server running on the provided host and port arguments.")
    ToolResponse invokeWildFlyCLIOperation(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            String operation,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode mn = ctx.buildRequest(operation);
            // TODO, implement possible rules if needed to disallow some operations.
            String value = wildflyClient.call(server, user, mn.toJSONString(false), false);
            return buildResponse(value);
        } catch (Exception ex) {
            return handleException(ex, server, "invoking operations ");
        }
    }
    
    @Tool(description = "Enable a logging category for the WildFly server running on the provided host and port arguments.")
    ToolResponse enableWildFlyLoggingCategory(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            String loggingCategory,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.result != null && response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is already enabled. You can get the enabled logging categories from the getLoggingCategories operation.");
            }
            wildflyClient.call(new AddLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been enabled by using the " + category + " logger");
        } catch (Exception ex) {
            return handleException(ex, server, "enabling the logger " + loggingCategory);
        }
    }
    
    @Tool(description = "Disable a logging category for the WildFly server running on the provided host and port arguments.")
    ToolResponse disableWildFlyLoggingCategory(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            String loggingCategory,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            String category = findCategory(loggingCategory);
            GetLoggersResponse response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.result != null && !response.result.contains(category)) {
                return buildErrorResponse("The logging category " + loggingCategory + " is not already enabled, you should first enabled it.");
            }
            wildflyClient.call(new RemoveLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been removed by using the " + category + " logger.");
        } catch (Exception ex) {
            return handleException(ex, server, "disabling the logger " + loggingCategory);
        }
    }
    
    @Tool(description = "Get the log file content of the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyLogFileContent(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "numberOfLines", description = "The optional number of log file lines to retrieve. By default the last 200 lines are retrieved. Use `-1` to get all lines.", required = false) String numLines,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            User user = new User(userName, userPassword);
            GetLoggingFileResponse response = wildflyClient.call(new GetLoggingFileRequest(server, numLines, user));
            StringBuilder builder = new StringBuilder();
            for (String line : response.result) {
                builder.append(line).append("\n");
            }
            return buildResponse("WildFly server log file Content: `" + builder.toString() + "`");
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the log file ");
        }
    }
    
    @Tool(description = "Get the status of the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyStatus(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        try {
            String consumedMemory = getWildFlyConsumedMemory(host, port, userName, userPassword).content().get(0).asText().text();
            String cpuUsage = getWildFlyConsumedCPU(host, port, userName, userPassword).content().get(0).asText().text();
            // First attempt with health check.
            String url = "http://" + server.host + ":" + server.port + "/health";
            String state = null;
            try {
                List<Status> statusList = wildflyHealthClient.getHealth(url);
                state = "Server is running.";
            } catch (Exception ex) {
                // XXX OK, let's try with the management API.
            }
            if (state == null) {
                User user = new User(userName, userPassword);
                WildFlyStatus status = wildflyClient.getStatus(server, user);
                if (status.isOk()) {
                    state = "Server is running.";
                } else {
                    List<String> ret = new ArrayList<>();
                    ret.add("Server is in an invalid state");
                    ret.addAll(status.getStatus());
                    ret.add(consumedMemory);
                    ret.add(cpuUsage);
                    return buildErrorResponse(ret.toArray(String[]::new));
                }
            }
            return buildResponse(state, consumedMemory, cpuUsage);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the status ");
        }
    }
    
    @Tool(description = "Get the percentage of memory consumed by the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyConsumedMemory(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        User user = new User(userName, userPassword);
        try {
            try (JMXSession session = new JMXSession(server, user)) {
                MemoryMXBean proxy
                        = ManagementFactory.newPlatformMXBeanProxy(session.connection,
                                ManagementFactory.MEMORY_MXBEAN_NAME,
                                MemoryMXBean.class);
                double max = proxy.getHeapMemoryUsage().getMax();
                double used = proxy.getHeapMemoryUsage().getUsed();
                double result = (used * 100) / max;
                //int remains = 100 - (int)result;
                return buildResponse("The percentage of consumed memory is " + (int) result + "%");
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed memory");
        }
    }
    
    @Tool(description = "Get the percentage of cpu consumed by the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyConsumedCPU(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port,
            @ToolArg(name = "userName", description = "Optional user name", required = false) String userName,
            @ToolArg(name = "userPassword", description = "Optional user password", required = false) String userPassword) {
        Server server = new Server(host, port);
        User user = new User(userName, userPassword);
        try {
            try (JMXSession session = new JMXSession(server, user)) {
                OperatingSystemMXBean proxy
                        = ManagementFactory.newPlatformMXBeanProxy(session.connection,
                                ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
                                OperatingSystemMXBean.class);
                double val = proxy.getProcessCpuLoad();
                return buildResponse("The percentage of consumed cpu is " + (int) val * 100 + "%");
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed CPU");
        }
    }
    
    @Tool(description = "Get the metrics (in prometheus format) of the WildFly server running on the provided host and port arguments.")
    ToolResponse getWildFlyPrometheusMetrics(
            @ToolArg(name = "host", description = "Optional WildFly server host name. By default localhost is used.", required = false) String host,
            @ToolArg(name = "port", description = "Optional WildFly server port. By default 9990 is used.", required = false) String port) {
        Server server = new Server(host, port);
        try {
            String url = "http://" + server.host + ":" + server.port + "/metrics";
            try {
                return buildResponse(wildflyMetricsClient.getMetrics(url));
            } catch (ClientWebApplicationException ex) {
                if (ex.getResponse().getStatus() == 404) {
                    return buildResponse("The WildFly metrics are not available in the WildFly server running on " + server.host + ":" + server.port);
                } else {
                    throw ex;
                }
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed CPU");
        }
    }
    
    @Prompt(name = "wildFly-prometheus-metrics-chart", description = "WildFly, prometheus metrics chart")
    PromptMessage prometheusMetricsChart() {
        return PromptMessage.withUserRole(new TextContent("Using available tools, get Prometheus metrics from wildfly server. " +
            "You will repeat the invocation 3 times, being sure to wait 2 seconds between each invocation. " + 
            "After all the 3 invocation has been completed you will organize the data in a table. " + 
            "Then you will use this table to create a bar chart to visually compare the data. " + 
            "Be sure to use at least 5 different data column and be sure to represent all data as bar in the chart"));
    }
    
    @Prompt(name = "wildFly-security-audit", description = "WildFly, security audit. Analyze the server log file for potential attacks")
    PromptMessage securityAudit(@PromptArg(name = "loggingCategories", 
            description = "Comma separated list of logging categories to enable. By default the security category is enabled.", 
            required=false) String arg) {
        String additionalCategories = (arg == null || arg.isEmpty()) ? "" : " " + arg;
        return PromptMessage.withUserRole(new TextContent("Using available tools, enable the org.wildfly.security"+ additionalCategories + " logging categories. " +
                "Then wait 10 seconds. Finally get the server log file, analyze it and report any issue related to security."));
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
        category = category.toLowerCase();
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
    
    private ToolResponse handleException(Exception ex, Server server, String action) {
        LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        if (ex instanceof ClientWebApplicationException clientWebApplicationException) {
            Response resp = clientWebApplicationException.getResponse();
            resp.getStatus();
            return buildErrorResponse(" Error when " + action + " for the server running on host " + server.host + " and port " + server.port);
        } else {
            if (ex instanceof AuthenticationException || ex instanceof ForbiddenException) {
                return buildErrorResponse(ex.getMessage());
            } else {
                if (ex instanceof HttpHostConnectException) {
                    return buildErrorResponse(" Error when connecting to the server " + server.host + ":" + server.port + ". Could you check the host and port.");
                } else {
                    if (ex instanceof UnknownHostException) {
                        return buildErrorResponse("The server host " + server.host + " is not a known server name");
                    } else {
                        return buildErrorResponse(ex.getMessage());
                    }
                }
            }
        }
    }
    
    private ToolResponse buildResponse(String... content) {
        return buildResponse(false, content);
    }
    
    private ToolResponse buildErrorResponse(String... content) {
        return buildResponse(true, content);
    }
    
    private ToolResponse buildResponse(boolean isError, String... content) {
        List<TextContent> lst = new ArrayList<>();
        for (String str : content) {
            TextContent text = new TextContent(str);
            lst.add(text);
        }
        return new ToolResponse(isError, lst);
    }
}

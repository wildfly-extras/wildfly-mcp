/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

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
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.conn.HttpHostConnectException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.wildfly.mcp.WildFlyControllerClient.AddLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.EnableLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggersRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetLoggingFileRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetMemoryMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetOperatingSystemMXBean;
import org.wildfly.mcp.WildFlyControllerClient.GetRuntimeMXBean;
import org.wildfly.mcp.WildFlyControllerClient.RemoveLoggerRequest;
import org.wildfly.mcp.WildFlyControllerClient.GetDeploymentRequest;
import org.wildfly.mcp.WildFlyControllerClient.AddDeploymentRequest;
import org.wildfly.mcp.WildFlyControllerClient.DisableDeploymentRequest;
import org.wildfly.mcp.WildFlyControllerClient.RemoveDeploymentRequest;

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

    WildFlyControllerClient wildflyClient = new WildFlyControllerClient();
    @RestClient
    WildFlyMetricsClient wildflyMetricsClient;
    @RestClient
    WildFlyHealthClient wildflyHealthClient;

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getWildFlyServerConfiguration(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest(":read-resource(recursive=true)");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            // enforce some cleanup
            result.remove("extension");
            result.remove("core-service");
            result.remove("path");
            result.remove("system-properties");
            cleanupUndefined(result);
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the server configuration");
        }
    }

    @Tool(description = "Retrieve the JSON of the metamodel for the provided resource path.")
    @RolesAllowed("admin")
    ToolResponse getWildFlyMetaModel(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "resource-path", required = true) String resourcePath) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest(resourcePath + ":read-resource-description(recursive=false)");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            cleanupUndefined(result);
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the server meta model");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getDeploymentFilePaths(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", required = false) String name) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            name = (name == null || name.isEmpty()) ? "ROOT.war" : name;
            ModelNode mn = ctx.buildRequest("/deployment=" + name + ":browse-content");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode result = node.get("result");
            return buildResponse(result.toJSONString(false));
        } catch (Exception ex) {
            return handleException(ex, server, "browsing the deployment");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getDeploymentFileContent(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", required = false) String name,
            @ToolArg(name = "path", required = true) String path) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            name = (name == null || name.isEmpty()) ? "ROOT.war" : name;
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            // This call, if done with the Monitor role, will be filtered. No sensitive information present.
            ModelNode mn = ctx.buildRequest("/deployment=" + name + ":read-content(path=" + path + ")");
            OperationResponse value = wildflyClient.callOperation(server, user, mn);
            String content = WildFlyControllerClient.getAttachment(value);
            return buildResponse(content);
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the logging categories");
        }
    }

    private static void cleanupUndefined(ModelNode mn) {
        Set<String> toRemove = new HashSet<>();
        for (String key : mn.keys()) {
            ModelNode field = mn.get(key);
            if (!field.isDefined()) {
                toRemove.add(key);
            } else {
                if (field.getType() == ModelType.OBJECT) {
                    cleanupUndefined(field);
                }
            }
        }
        for (String k : toRemove) {
            mn.remove(k);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse invokeWildFlyCLIOperation(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String operation) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode mn = ctx.buildRequest(operation);
            // TODO, implement possible rules if needed to disallow some operations.
            String value = wildflyClient.call(server, user, mn).toJSONString(false);
            return buildResponse(value);
        } catch (Exception ex) {
            return handleException(ex, server, "invoking operations ");
        }
    }

    @Tool(description = "Deploy a new application to wildfly. If it already exists, remove it and deploy it again.")
    @RolesAllowed("admin")
    ToolResponse deployWildFlyApplication(@ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "name", description = "deployment name.", required = true) String name,
            @ToolArg(name = "deploymentPath", description = "War or Application directory path", required = true) String deploymentPath,
            @ToolArg(name = "archive", description = "Set false to deploy exploded.", required = true) String archive,
            @ToolArg(name = "runtime-name", description = "Context path. (*.war)", required = true) String runtimename) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            ModelNode deployments = wildflyClient.call(new GetDeploymentRequest(server, user));
            List<String> removedlist = new ArrayList<>();
            if (deployments.hasDefined("result")) {
                for (ModelNode dep : deployments.get("result").asList()) {
                    ModelNode deploymentInfo = dep.asProperty().getValue();
                    String deployedName = deploymentInfo.get("name").asString();
                    String deployedRuntimeName = deploymentInfo.get("runtime-name").asString();
                    if (deployedName.equals(name) || deployedRuntimeName.equals(runtimename)) {
                        ModelNode disableResult = wildflyClient.call(new DisableDeploymentRequest(server, user, deployedName));
                        if (!"success".equals(disableResult.get("outcome").asString())) {
                            return buildErrorResponse("Failed to undeploy existing deployment: " + deployedName);
                           }
                        ModelNode removeResult = wildflyClient.call(new RemoveDeploymentRequest(server, user, deployedName));
                        if (!"success".equals(removeResult.get("outcome").asString())) {
                            return buildErrorResponse("Failed to remove existing deployment: " + deployedName);
                           }
                        removedlist.add("deployedName: " + deployedName + "( deployedRuntimeName: " + deployedRuntimeName + " ) ");
                        }
                    }
                }
            ModelNode response = wildflyClient.call(new AddDeploymentRequest(server, user, deploymentPath, name, runtimename, archive));
            if ("success".equals(response.get("outcome").asString())) {
                String successDesc = (removedlist.size() > 0) ?
                        "Removed duplicate deployments ( Total: " + removedlist.size() + " ) : " + removedlist.toString() +
                        " And Successfully deployed application from path: " + deploymentPath
                        : "Successfully deployed application from path: " + deploymentPath;
                return buildResponse(successDesc);
            } else {
                String failureDesc = response.has("failure-description") ?
                        response.get("failure-description").asString()
                        : "Unknown error";
                return buildErrorResponse("Failed to deploy application: " + failureDesc);
                }
        } catch (Exception ex) {
            return handleException(ex, server, "deploying application from " + deploymentPath);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse enableWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            ModelNode response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.get("result") != null) {
                boolean found = false;
                for (ModelNode cat : response.get("result").asList()) {
                    if (cat.asString().equals(loggingCategory)) {
                        found = true;
                    }
                }
                if (found) {
                    wildflyClient.call(new EnableLoggerRequest(server, user, category));
                } else {
                    wildflyClient.call(new AddLoggerRequest(server, user, category));
                }
            } else {
                wildflyClient.call(new AddLoggerRequest(server, user, category));
            }
            return buildResponse("The logging category " + loggingCategory + " has been enabled by using the " + category + " logger");
        } catch (Exception ex) {
            return handleException(ex, server, "enabling the logger " + loggingCategory);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse removeWildFlyLoggingCategory(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            String loggingCategory) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            String category = findCategory(loggingCategory);
            ModelNode response = wildflyClient.call(new GetLoggersRequest(server, user));
            if (response.get("result") != null) {
                boolean found = false;
                for (ModelNode cat : response.get("result").asList()) {
                    if (cat.asString().equals(loggingCategory)) {
                        found = true;
                    }
                }
                if (!found) {
                    return buildErrorResponse("The logging category " + loggingCategory + " is not already enabled, you should first enabled it.");
                }
            }
            wildflyClient.call(new RemoveLoggerRequest(server, user, category));
            return buildResponse("The logging category " + loggingCategory + " has been removed by using the " + category + " logger.");
        } catch (Exception ex) {
            return handleException(ex, server, "disabling the logger " + loggingCategory);
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getWildFlyLogFileContent(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port,
            @ToolArg(name = "numberOfLines", description = "200 by default, use `-1` for all lines.", required = false) String numLines,
            @ToolArg(name = "onlyForLastServerStart", description = "True by default.", required = false) Boolean lastStart) {
        Server server = new Server(host, port);
        try {
            User user = new User();
            ModelNode response = wildflyClient.call(new GetLoggingFileRequest(server, numLines, user));
            StringBuilder builder = new StringBuilder();
            List<String> lst = new ArrayList<>();
            lastStart = lastStart == null ? Boolean.TRUE : lastStart;
            if (lastStart && numLines == null) {
                for (ModelNode line : response.get("result").asList()) {
                    if (line.asString().contains("WFLYSRV0049")) {
                        lst = new ArrayList<>();
                    }
                    lst.add(line.asString());
                }
                for (String line : lst) {
                    builder.append(line).append("\n");
                }
            } else {
                for (ModelNode line : response.get("result").asList()) {
                    builder.append(line.asString()).append("\n");
                }
            }
            return buildResponse("WildFly server log file Content: `" + builder.toString() + "`");
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the log file ");
        }
    }

    @Tool()
    @RolesAllowed("admin")
    ToolResponse getWildFlyServerAndJVMInfo(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
        Server server = new Server(host, port);
        User user = new User();
        try {
            VMInfo info = new VMInfo();
            ModelNode response = wildflyClient.call(new GetRuntimeMXBean(server, user));
            ModelNode result = response.get("result");
            info.name = result.get("name").asString();
            for (ModelNode a : result.get("input-arguments").asList()) {
                info.inputArguments.add(a.asString());
            }
            info.specName = result.get("spec-name").asString();
            info.specVendor = result.get("spec-vendor").asString();
            info.specVersion = result.get("spec-version").asString();
            info.startTime = new Date(result.get("start-time").asLong()).toString();
            info.upTime = (result.get("uptime").asLong() / 1000) + "seconds";
            info.vmName = result.get("vm-name").asString();
            info.vmVendor = result.get("vm-vendor").asString();
            info.vmVersion = result.get("vm-version").asString();

            // Not supported by WildFly
//            response = wildflyClient.call(new GetOperatingSystemMXBean(server, user));
//            result = response.get("result");
//            double val = result.get("process-cpu-load").asLong();
//            info.consumedCPU = "" + (int) val + "%";
            info.consumedCPU = "not available";
            response = wildflyClient.call(new GetOperatingSystemMXBean(server, user));
            result = response.get("result");
            double val = result.get("system-load-average").asLong();
            info.systemLoadAverage = "" + (int) val + "%";

            response = wildflyClient.call(new GetMemoryMXBean(server, user));
            result = response.get("result");
            double max = result.get("heap-memory-usage").get("max").asLong();
            double used = result.get("heap-memory-usage").get("used").asLong();
            double res = (used * 100) / max;
            info.consumedMemory = "" + (int) res + "%";
            ServerInfo serverInfo = new ServerInfo();
            serverInfo.vmInfo = info;
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode mn = ctx.buildRequest(":read-resource(recursive=false)");
            ModelNode node = wildflyClient.call(server, user, mn);
            ModelNode res2 = node.get("result");
            serverInfo.nodeName = res2.get("name").asString();
            serverInfo.productName = res2.get("product-name").asString();
            serverInfo.productVersion = res2.get("product-version").asString();
            serverInfo.coreVersion = res2.get("release-version").asString();
            return buildResponse(toJson(serverInfo));

        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the consumed memory");
        }
    }

    @Tool()
    ToolResponse getWildFlyPrometheusMetrics(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {
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
            return handleException(ex, server, "retrieving the metrics");
        }
    }

    String toJson(Object value) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(value);
    }

    @Tool()
    ToolResponse getWildFlyServerAndDeploymentsStatus(
            @ToolArg(name = "host", required = false) String host,
            @ToolArg(name = "port", required = false) String port) {

        Server server = new Server(host, port);
        try {
            String url = "http://" + server.host + ":" + server.port + "/health";
            try {
                return buildResponse(wildflyHealthClient.getHealth(url));
            } catch (ClientWebApplicationException ex) {
                if (ex.getResponse().getStatus() == 404) {
                    User user = new User();
                    WildFlyDMRStatus status = wildflyClient.getStatus(server, user);
                    List<String> ret = new ArrayList<>();
                    ret.addAll(status.getStatus());
                    return buildResponse(ret.toArray(String[]::new));
                } else {
                    if (ex.getResponse().getStatus() == 503) {
                        String e = ex.getResponse().readEntity(String.class);
                        return buildResponse(e);
                    } else {
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            return handleException(ex, server, "retrieving the health");
        }
    }

    @Prompt(name = "wildfly-server-prometheus-metrics-chart", description = "WildFly, prometheus metrics chart")
    PromptMessage prometheusMetricsChart(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Using available tools, get Prometheus metrics from wildfly server Wildfly server running on host " + server.host + ", port " + server.port
                + ". You will repeat the invocation 3 times, being sure to wait 5 seconds between each invocation. "
                + "After all the 3 invocation has been completed you will organize the data in a table. "
                + "Then you will use this table to create a bar chart to visually compare the data. "
                + "Be sure to use at least 5 different data column and be sure to represent all data as bar in the chart"));
    }

    @Prompt(name = "wildfly-server-security-audit", description = "WildFly, security audit. Analyze the server log file for potential attacks")
    PromptMessage securityAudit(@PromptArg(name = "loggingCategories",
            description = "Comma separated list of logging categories to enable. By default the security category is enabled.",
            required = false) String arg,
            @PromptArg(name = "host",
                    description = "Optional WildFly server host name. By default localhost is used.",
                    required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        String additionalCategories = (arg == null || arg.isEmpty()) ? "" : " " + arg;
        return PromptMessage.withUserRole(new TextContent("Using available tools, enable the org.wildfly.security" + additionalCategories + " logging categories of the Wildfly server running on host " + server.host + ", port " + server.port
                + ". Then wait 10 seconds. Finally get the server log file, analyze it and report issues related to authentication failures."));
    }

    @Prompt(name = "wildfly-server-resources-consumption", description = "WildFly and JVM resource consumption status. Analyze the consumed resources.")
    PromptMessage consumedResources(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Check the consumed resources of the JVM and the Wildfly server running on host " + server.host + ", port " + server.port + ": consumed memory, max memory and cpu utilization, prometheus metrics. "
                + "Your reply should be short with a strong focus on what is wrong and your recommendations."));
    }

    @Prompt(name = "wildfly-server-metrics-analyzer", description = "WildFly and JVM metrics. Analyze and summarize the metrics.")
    PromptMessage analyzeMetrics(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the metrics of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "Analyze the metrics then provide a summary that should highlights potential reached limits. Make sure to not deep dive into the details and provide a compact summary."));
    }

    @Prompt(name = "wildfly-server-memory-consumption-over-time", description = "WildFly, memory consumption over time")
    PromptMessage memOverTimeChart(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Get the JVM memory consumption from the Wildfly server running on host " + server.host + ", port " + server.port
                + ". You will repeat the invocation 3 times, being sure to wait 5 seconds between each invocation. "
                + "After all the 3 invocation have been completed you will organize the data in a table. "
                + "Then you will use this table to create a graph to visually compare the data. "
                + "Use the time in X axis, and mem consumption in Y axis"));
    }

    @Prompt(name = "wildfly-deployment-errors", description = "WildFly deployed applications, identify potential errors.")
    PromptMessage deploymentError(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port,
            @PromptArg(name = "deploymentName",
                    description = "The deployment name.",
                    required = false) String deploymentName) {
        Server server = new Server(host, port);
        if (deploymentName == null) {
            deploymentName = "deployments";
        } else {
            deploymentName = "the deployed application " + deploymentName;
        }
        return PromptMessage.withUserRole(new TextContent("Check that the status of " + deploymentName + " in the Wildfly server running on host " + server.host + ", port " + server.port + " is OK. "
                + "Retrieve the lines of the server log of the last time the server started. Then check that no errors are found in the traces older than the last time the server was starting."
                + "If you find errors, and if the files exist, access the web.xml and jboss-web.xml files of the " + deploymentName + " and check for faulty content that could explain the error seen in the log file."));
    }

    @Prompt(name = "wildfly-server-log-errors", description = "WildFly server, identify errors.")
    PromptMessage serverLogErrors(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the server log of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "If you see lines containing ERROR, analyze the error and report the findings. If you don't see lines with ERROR, reply that the log file doesn't contain any errors."));
    }

    @Prompt(name = "wildfly-server-log-analyzer", description = "WildFly server, analyze the log file.")
    PromptMessage serverAnalyzeLogFile(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Retrieve the server log of the Wildfly server running on host " + server.host + ", port " + server.port + ". "
                + "If you see lines containing ERROR or WARN, analyze the error and report the findings. If you don't see lines with ERROR nor WARN, provide a short summary of what the traces contain."));
    }

    @Prompt(name = "wildfly-server-status", description = "WildFly server, running status.")
    PromptMessage serverBootErrors(@PromptArg(name = "host",
            description = "Optional WildFly server host name. By default localhost is used.",
            required = false) String host,
            @PromptArg(name = "port",
                    description = "Optional WildFly server port. By default 9990 is used.",
                    required = false) String port) {
        Server server = new Server(host, port);
        return PromptMessage.withUserRole(new TextContent("Check that the server and deployments running on host " + server.host + ", port " + server.port
                + " status is ok. Then retrieve the last 100 lines of the server log, then check that the trace WFLYSRV0025 is found in the server log traces of the last time the server started. In addition your reply must contain the WildFly and JVM versions."));
    }

    @RegisterRestClient(baseUri = "http://foo:9990/metrics/")
    public interface WildFlyMetricsClient {

        @GET
        String getMetrics(@Url String url);
    }

    @RegisterRestClient(baseUri = "http://foo:9990/health")
    public interface WildFlyHealthClient {

        @GET
        String getHealth(@Url String url);
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

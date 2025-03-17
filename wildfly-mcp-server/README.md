# WildFly MCP Server

[![smithery badge](https://smithery.ai/badge/@wildfly-extras/wildfly-mcp-server)](https://smithery.ai/server/@wildfly-extras/wildfly-mcp-server)
A WildFly [MCP server](https://github.com/modelcontextprotocol/servers) to integrate with your AI chatbot in order to interact with WildFly server using natural language.
This MCP server is a Java quarkus fat jar application that you can configure in your chatbot mcp configuration.

This MCP server (a Tool in AI terminologie) helps you troubleshoot running WildFly servers using natural language.
You can ask questions such as:

`Hi, could you connect to the WildFly server running on host localhost and port 9990 and get the content of the log file, analyze it and check for errors?`

### Installing via Smithery

To install WildFly MCP Server for Claude Desktop automatically via [Smithery](https://smithery.ai/server/@wildfly-extras/wildfly-mcp-server):

```bash
npx -y @smithery/cli install @wildfly-extras/wildfly-mcp-server --client claude
```

## Build WildFly MCP quarkus uber jars

Make sure to use JDK21+.
 
`mvn clean install`

The STDIO based mcp server jar is `stdio/target/wildfly-mcp-server-stdio-runner.jar`.
The SSE based mcp server jar is `sse/target/wildfly-mcp-server-sse-runner.jar`.

## Configure the chatbot

Add the following json to the chatbot configuration file:

```
{
  "mcpServers": {
    "wildfly": {
            "command": "java",
            "args": ["-jar",
                    "[path to the repository]/wildfly-mcp-server/stdio/target/wildfly-mcp-server-stdio-runner.jar"]
    }
  }
}
```

If you are using [jbang](http://jbang.dev), you can add the following json content:

```
{
  "mcpServers": {
    "wildfly": {
            "command": "jbang",
            "args": ["--quiet",
                    "org.wildfly:wildfly-mcp-server-stdio:1.0.0.Final-SNAPSHOT:runner"]
    }
  }
}
```

If you are using the WildFly MCP server container image with podman, you can add the following json content:

NOTE: You must first pull the `quay.io/wildfly-snapshots/wildfly-mcp-server:latest` image.

```
{
  "mcpServers": {
    "wildfly": {
            "command": "podman",
            "args": [
                     "run",
                     "--rm",
                     "-i",
                     "--network=host",
                     "quay.io/wildfly-snapshots/wildfly-mcp-server:latest"]
    }
  }
}
```

* For [claude.ai](http://claude.ai), on Fedora, add it to the file `~/.config/Claude/claude_desktop_config.json`.

* For [MCPHost](https://github.com/mark3labs/mcphost), add to a file named `mcp.json` and call: `./mcphost_Linux_x86_64/mcphost --config [path to the file]/mcp.json --model ollama:llama3.1:8b`

## Configuring the WildFly MCP SSE server

The access to the [WildFly MCP SSE server](sse) is secured with OIDC (OAuth2 'Resource Owner Password Credentials Grant').

In the following configuration (from the [WildFly chat bot](../wildfly-chat-bot/README.md)), a keycloak realm named `wildfly-mcp-server` that contains a client named `token-service` is used to authenticate users.
The WildFly chatbot will prompt you for username and password that will then be used to retrieve Tokens from the keycloak server allowing to access 
to the SSE MCP server.

```
{
  "mcpSSEServers": {
    "wildfly": {
            "url": "http://localhost:8081/mcp/sse"
            "providerUrl": "http://localhost:8180/realms/wildfly-mcp-server",
            "clientId": "token-service"
    }
  }
}
``` 

WARNING: This configuration is subject to change once the 
[Authorization specification draft](https://spec.modelcontextprotocol.io/specification/draft/basic/authorization/) 
is integrated in the [MCP protocol specification](https://spec.modelcontextprotocol.io/specification/2024-11-05/).

## Admin user credentials

You can set the user name and password that you have been using to secure the WildFly server in the tool shell command using the system properties `-Dorg.wildfly.user.name=<user name>` and `-Dorg.wildfly.user.password=<user password>`

NOTE: When using the container image, `WILDFLY_MCP_SERVER_USER_NAME` and `WILDFLY_MCP_SERVER_USER_PASSWORD` env variables can be used.

```
{
  "mcpServers": {
    "wildfly": {
            "command": "java",
            "args": ["-Dorg.wildfly.user.name=chatbot-user",
                     "-Dorg.wildfly.user.password=chatbot-user",
                     "-jar",
                     "[path to the repository]/wildfly-mcp-server/stdio/target/wildfly-mcp-server-stdio-runner.jar"]
    }
  }
}
``` 

NOTE: For SSE server, you can set the same system properties when starting the server.

### Note on security

Allowing full access to your WildFly servers from the chatbot is not advised. Although you would then be able to call any WildFly management operations,
it must be done carefully. In particular when the chat bot is connected to a public LLM, no sentive information should be exposed.

When the chat bot interacts with a locally managed model (e.g.: locally managed `ollama` model, the default), this is less of a problem.
 
In any case it is advised that you benefit from the [WildFly RBAC](https://docs.wildfly.org/35/Admin_Guide.html#RBAC) configuration and define a user named `chatbot-user` with role `Monitor`.

Make sure to first add the user by invoking: `$JBOSS_HOME/bin/add-user.sh -p chatbot-user -u chatbot-user`

To enable RBAC, you can use the WildFly CLI command line and invoke the following commands:

```
/core-service=management/access=authorization:write-attribute(name=provider,value=rbac)
reload
/core-service=management/access=authorization/role-mapping=Monitor:add
/core-service=management/access=authorization/role-mapping=Monitor/include=chatbot-user:add(name=chatbot-user,type=USER)
```

When a user with the `Monitor` role is used, the following tools would fail:

* `enableWildFlyLoggingCategory` tool.

* `disableWildFlyLoggingCategory` tool.

In addition, write operations are not allowed when invoking the `invokeWildFlyCLIOperation` tool.

## Note on sensitive information

* Any sensitive information located in your server configuration file are not exposed when 
accessing to the server with the `chatbot-user` (actually any user with the `Monitor` role).

* WildFly server doesn't log sensitive information. Make sure that the deployments running in the WildFly servers are not logging sensitive information that could be exposed.

## Default WildFly server host and port

If no host is provided, `localhost` is used. If no port is provided, `9990` is used.
You can configure default host and port using the system properties `-Dorg.wildfly.host.name=<host name>` and `-Dorg.wildfly.port=<port>`

## SSE security token configuration

The Keycloak server URL, client and optional secret can be configured using the following system properties:

| System property    | Description |
| -------- | ------- |
quarkus.oidc.auth-server-url | The URL to the keycloak server realm, default to `http://localhost:8180/realms/wildfly-mcp-server` |
quarkus.oidc.client-id | The client id, default to `token-service` |
quarkus.oidc.credentials.secret | The client secret, optional, default to `secret` | 

## Available Tools

### getWildFlyStatus
Get the status of the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### getJVMInfo
Get the JVM (Java VM) information (version, input arguments, startime, uptime, consumed memory, consumed cpu) 
as JSON format. The Java VM is the one used to execute the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### getWildFlyLogFileContent
Get the log file content of the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.
- `numberOfLines`: The optional number of log file lines to retrieve. By default the last 200 lines are retrieved. Use `-1` to get all lines.

### getWildFlyLoggingCategories
Get the list of the enabled logging categories for the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### enableWildFlyLoggingCategory
Enable a logging category for the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.
- `loggingCategory`: The logging category. Can be a high level category (such as `security`, `web`, `http`) or a specific logger (`io.undertow`).

### disableWildFlyLoggingCategory
Disable a logging category for the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.
- `loggingCategory`: The logging category. Can be a high level category (such as `security`, `web`, `http`) or a specific logger (`io.undertow`).

### getWildFlyPrometheusMetrics
Get the metrics (in prometheus format) of the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### invokeWildFlyCLIOperation
Invoke a single WildFly CLI operation on the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### getWildFlyServerConfiguration
Gets the server configuration in JSON format of the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.

### browseDeployment
Get all the file paths contained inside a deployment deployed in the WildFly server running on the provided host and port arguments. The returned value is in JSON format.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.
- `name`: Optional deployment name. By default ROOT.war is used.

### getDeploymentFileContent
Get the content of a file located inside a deployment deployed in the WildFly server running on the provided host and port arguments.

**Inputs**:
- `host`: The host name on which the WildFly server is running. Optional, `localhost` is used by default.
- `port`: The port the WildFly server is listening on. Optional, `9990` is used by default.
- `name`: Optional deployment name. By default ROOT.war is used.
- `path`: Required path to the deployment content.

## Available prompts

Prompts are pre-built complex questions that you can invoke from your chat bot.

### wildFly-prometheus-metrics-chart
Build a table and chart from WildFly prometheus metrics.

### securityAudit
WildFly, security audit. Analyze the server log file for potential attacks.

### wildFly-resources-consumption
WildFly and JVM resource consumption status. Analyze the consumed resources.

### wildFly-deployment-errors
WildFly deployed applications, identify potential for errors.

## Example of questions to ask to the WildFly server

Make sure to first start you WildFly sever.

### Health and resource consumption

* Hi, could you connect to the WildFly server and get the status of the server?
* Hi, is the WildFly server activity normal?
* Hi, could you connect again to the WildFly server and give me the status?
* Hi, could you connect to the WildFly server running on localhost and port 7777 and get the status of the server?

### Logging

* Hi, could you connect to the WildFly server running on host localhost and port 9990 then enable the security logging?
* Hi, could you connect to the WildFly server and get the content of the log file, analyze it and check for errors?
* Hi, could you disable the security logging?

### Metrics

* Could you retrieve the metrics of the WildFly server?
* Hi, could you connect to the WildFly server and check the available memory and cpu usage?
* Hi, could you connect to the WildFly server and check if it has enough available memory to run?
* Hi, could you connect to the WildFly server and check if the cpu usage is not too high?

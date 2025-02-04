# MCP STDIO to SSE protocol Gateway

* Build the gateway:

```
mvn clean install
```

* Example of MCP configuration that uses the gateway to proxy an SSE MCP server:

```
{
  "mcpServers": {
    "weather": {
            "command": "java",
            "args": [ "-jar",
                    "mcp-stdio-sse-gateway/target/mcp-stdio-sse-gateway-1.0.0.Final-SNAPSHOT-client.jar",
                    "http://127.0.0.1:8080/sse"]
    }
  }
}

```

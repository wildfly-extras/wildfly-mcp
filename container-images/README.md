# WildFly Chat Bot container images

The `chat-bot-image` contains both the chat bot and the WildFly MCP server (STDIO protocol). Starts this image to interact with any WildFly server.

The `mcp-server-image` contains the WildFly MCP server (STDIO protocol).

## Build the chat bot image

You need podman installed.

`sh build-chat-bot-image.sh`

## Build the mcp server image

You need podman installed.

`sh build-mcp-server-image.sh`

## Examples of cloud deployments

The directory [examples](examples) contains deployment of pre-built WildFly chat bot image.

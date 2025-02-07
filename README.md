# WildFly MCP

This project aims to define tooling allowing WildFly users to benefenit from the Generative AI capabilities when monitoring and managing WildFly servers.

* [WildFly MCP Server](wildfly-mcp-server/README.md): A WildFly [MCP server](https://github.com/modelcontextprotocol/servers) to integrate with your AI chatbot in order to interact with WildFly server using natural language.

* [WildFly Chat Bot](wildfly-chat-bot/README.md): A WildFly Chat Bot to interact with WildFly servers. This AI chatbot allows to also integrate MCP servers (STDIO and SSE protocol).

* [Container Image](container-image/README.md): A container image that contains both the chat bot and the mcp server. Ready to interact with your WildFly servers on the cloud. Example of OpenShift deployment is provided.

* [MCP STDIO to SEE protocol gateway](mcp-stdio-sse-gateway/README.md): A Java gateway allowing to integrate SSE MCP servers in chat applications that only support STDIO protocol.

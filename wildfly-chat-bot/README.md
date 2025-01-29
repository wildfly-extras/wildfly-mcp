# WildFly Chat Bot

A WildFly Bootable JAR Chat Bot.
By default the file ./mcp.json is read. Yo ucan configure it with `-Dorg.wildfly.ai.chatbot.mcp.config=<path to file>`

1) Build the WildFly MCP server located in ../wildfly-mcp-server (The chat bot will use it in its mcp.json configuration.

2) Build the WildFly chat bot:

```
mvn clean install
```

3) Start the chat bot using ollama, once started it listens on port 8090:

```
GROQ_API_KEY=foo java -jar target/wildfly-chat-bot-bootable.jar -Djboss.socket.binding.port-offset=10

```

3) Start the chat bot using openai, once started it listens on port 8090:

```
GROQ_API_KEY=<Your openai key> java -jar target/wildfly-chat-bot-bootable.jar -Dorg.wildfly.ai.chatbot.llm.name=openai -Djboss.socket.binding.port-offset=10

```
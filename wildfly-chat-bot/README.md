# WildFly Chat Bot

A WildFly Chat Bot. This WildFly Bootable jar application is a web based UI allowing you to interact with your WildFly servers using natural language.

![](img/chatbot-demo.png)

By default the file `./mcp.json` is read. Yo ucan configure it with `-Dorg.wildfly.ai.chatbot.mcp.config=<path to file>`

1) Build the WildFly MCP server located in `../wildfly-mcp-server` (The chat bot will use it in its mcp.json configuration.

2) Build the WildFly chat bot:

```
mvn clean install
```

3) Start the chat bot using local `ollama`, once started it listens on port `8090`:

```
OLLAMA_CHAT_MODEL_NAME=llama3.3:8b GROQ_API_KEY=foo java -jar target/wildfly-chat-bot-bootable.jar -Djboss.socket.binding.port-offset=10

```

3) Start the chat bot using [groq](https://console.groq.com/docs/openai), once started it listens on port `8090`:

```
OPENAI_CHAT_MODEL_NAME=llama3-70b-8192 GROQ_API_KEY=<Your groq key> java -jar target/wildfly-chat-bot-bootable.jar -Dorg.wildfly.ai.chatbot.llm.name=openai -Djboss.socket.binding.port-offset=10
```

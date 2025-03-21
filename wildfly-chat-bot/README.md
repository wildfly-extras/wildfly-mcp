# WildFly Chat Bot

A WildFly Chat Bot. This WildFly Bootable jar application is a web based UI allowing you to interact with your WildFly servers using natural language.

![](img/chatbot-demo.png)

## WildFly Chat Bot features

* Interact in natural language with WildFly servers.
* Pre-configured with the [WildFly MCP server](../wildfly-mcp-server/README.md).
* STDIO and SSE mcp server can be added thanks to the [mcp.json](mcp.json) file.
* Ability to select and call mcp tools (allow to debug mcp servers) .
* Ability to select and use mcp prompts.
* Acceptance workflow of LLM called tools. You can reject a tool invocation done by the LLM.
* Configurable system prompt and welcome message.
* Report generation inmarkdown and json format to archive the interaction with the chatbot.

## Using the WildFly chatbot container image

You can start the chatbot thanks to its container image. You can check [this documentation](../container-image/examples/podman/README.md) that uses `podman`.

## Build the chatbot

1) Build the WildFly MCP server located in `../wildfly-mcp-server` (The chat bot will use it in its [default mcp.json](mcp.json) configuration).

2) Build the WildFly wait server located in `../wait-mcp-server` (The chat bot will use it in its [default mcp.json](mcp.json) configuration).

3) Build the WildFly chat bot:

```
mvn clean install
```

4) Start the chat bot using local `ollama` (by default uses the `qwen2.5:3b`) model, once started it listens on port `8090`:

By default the file `./mcp.json` is read. You can configure it with `-Dwildfly.chatbot.mcp.config.file=<path to file>`

NOTE: You must use JDK21+ to run the chatbot.

```
java -jar target/wildfly-chat-bot-bootable.jar

```

This chatbot has also been tried with the `llama3.2:3b` and provided good results.

4) Start the chat bot using [groq](https://console.groq.com/docs/openai), once started it listens on port `8090`:

```
GROQ_CHAT_MODEL_NAME=llama3-70b-8192 GROQ_API_KEY=<Your groq key> java -jar target/wildfly-chat-bot-bootable.jar -Dwildfly.chatbot.llm.name=groq
```

4) Start the chat bot using [mistral](https://mistral.ai/), once started it listens on port `8090`:

```
MISTRAL_API_KEY=<Your mistral key> java -jar target/wildfly-chat-bot-bootable.jar -Dwildfly.chatbot.llm.name=mistral
```

## Configuring the WildFly chatbot

| Env Variable    | System property |  Description |
| -------- | ------- | ------- |
| WILDFLY_CHATBOT_MCP_CONFIG_FILE | wildfly.chatbot.mcp.config.file |Absolute path to the mcp.json file    |
| WILDFLY_CHATBOT_LLM_NAME  | wildfly.chatbot.llm.name |The active LLM model (`ollama`, `groq`, `mistral` or `github`)    |
| WILDFLY_CHATBOT_SYSTEM_PROMPT |wildfly.chatbot.system.prompt |You can extend the system prompt with some content |
| WILDFLY_CHATBOT_WELCOME_MESSAGE | wildfly.chatbot.welcome.message |You can replace the welcome message with another message |
| WILDFLY_MCP_SERVER_USER_NAME  | org.wildfly.user.name |The default user name to use when connecting to WildFly server |
| WILDFLY_MCP_SERVER_USER_PASSWORD | org.wildfly.user.password | The default user password to use when connecting to WildFly server |


## Configuring the WildFly chatbot for ollama

| Env Variable    | Description |
| -------- | ------- |
| OLLAMA_CHAT_URL  | URL, default value `http://127.0.0.1:11434`    |
| OLLAMA_CHAT_MODEL_NAME | ollama model, default value `qwen2.5:3b`   |
| OLLAMA_CHAT_TEMPERATURE    | model temperature, by default `0.9`    |
| OLLAMA_CHAT_LOG_REQUEST    | log requests, by default `true`    |
| OLLAMA_CHAT_LOG_RESPONSE    | log responses, by default `true`    |

## Configuring the WildFly chatbot for groq

| Env Variable    | Description |
| -------- | ------- |
| GROQ_API_KEY   | Your API key |
| GROQ_CHAT_URL  | URL, default value `https://api.groq.com/openai/v1`    |
| GROQ_CHAT_MODEL_NAME | model, default value `llama3-8b-8192`   |
| GROQ_CHAT_LOG_REQUEST    | log requests, by default `true`    |
| GROQ_CHAT_LOG_RESPONSE    | log responses, by default `true`    |

## Configuring the WildFly chatbot for mistral

| Env Variable    | Description |
| -------- | ------- |
| MISTRAL_API_KEY   | Your API key |
| MISTRAL_CHAT_URL  | URL, default value `https://api.mistral.ai/v1`    |
| MISTRAL_CHAT_MODEL_NAME | model, default value `mistral-small-latest`   |
| MISTRAL_CHAT_LOG_REQUEST    | log requests, by default `true`    |
| MISTRAL_CHAT_LOG_RESPONSE    | log responses, by default `true`    |

## Configuring the WildFly chatbot for github

| Env Variable    | Description |
| -------- | ------- |
| GITHUB_API_KEY   | Your API key |
| GITHUB_CHAT_URL  | URL, default value `https://models.inference.ai.azure.com`    |
| GITHUB_CHAT_MODEL_NAME | model, default value `gpt-4o-mini`   |
| GITHUB_CHAT_LOG    | log requests and responses, by default `true`    |

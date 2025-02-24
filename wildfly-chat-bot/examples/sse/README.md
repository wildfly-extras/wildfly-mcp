# WildFly Chat Bot connected to WildFly MCP SSE server 

In this example,we are configuring the chatbot to access to the WildFly MCP SSE server.

## Start and configure keycloak

### Start keycloak

```
podman run --name keycloak --rm -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
-e KC_BOOTSTRAP_ADMIN_PASSWORD=admin  -p 8180:8080 quay.io/keycloak/keycloak:26.1.2 start-dev
```

### Import the keycloak realm used by this example

* Log into the keycloak console that runs on: [http://localhost:8180](http://localhost:8180)
* Create a new realm by importing this [realm file](wildfly-mcp-server-realm.json)

This realm will create a client `token-service`, a user named `alice`, password `alice` with the `admin` role (role required by the WildFly MCP server protected tools).

### Start the WildFly MCP SSE server

Make sure that you first built it. 

```
java -Dorg.wildfly.user.name=chatbot-user -Dorg.wildfly.user.password=chatbot-user -jar ../../../wildfly-mcp-server/sse/target/wildfly-mcp-server-sse-runner.jar 
```

### Start your WildFly server

* Make sure to first add a user `chatbot-user`, password `chatbot-user`. That is the user used by the WildFly MCP SSE server when interacting with the WildFly server.

### Start the WildFly chatbot

Make sure that you first built it. 

```
java -jar ../../../wildfly-chat-bot/target/wildfly-chat-bot-bootable.jar -Dwildfly.chatbot.mcp.config.file=mcp-sse.json
```

### Access the chatbot

* It runs on: [http://localhost:8090](http://localhost:8090)
* When asked to authenticate for the `WildFly` MCP server, user `alice`, password `alice`
* You can now interact with the chatbot.
#  Running the image locally using podman and the host network

* Starts the WildFly server you want to interact with. Management interface should be secured with a user and password.

* Starts the 'ollama' container with `qwen2.5:3b` loaded.

```shell
podman run -d --rm --name ollama --replace --pull=always -p 11434:11434 -v ollama:/root/.ollama --stop-signal=SIGKILL mirror.gcr.io/ollama/ollama

podman exec -it ollama ollama run qwen2.5:3b
```

To quit the Ollama prompt, type **/bye**.

* Starts the image (replace the user name and password with yours):

```
podman run -e OLLAMA_CHAT_URL=http://localhost:11434 \
--network=host -e PORT_OFFSET=10 \
-e WILDFLY_MCP_SERVER_USER_NAME=<your admin user> -e WILDFLY_MCP_SERVER_USER_PASSWORD=<your admin password> \
quay.io/wildfly-snapshots/wildfly-chat-bot:latest
```

The WildFly chat bot is then running on : `http://localhost:8090`

## For IPV6 platform

Add the following env variables to the command line:

```
-e JBOSS_HA_IP=localhost -e JBOSS_MESSAGING_HOST=localhost -e SERVER_PUBLIC_BIND_ADDRESS=localhost \
```

## Configuring the WildFly chat bot

The env variables defined in the [WildFly chat bot README](../../../wildfly-chat-bot/README.md) can be used.

In addition you can use the `PORT_OFFSET=<port offset>` env variable to change the default `8080` and `9990` ports that could conflict with 
the locally running WildFly servers.


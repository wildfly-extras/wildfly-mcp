#!/bin/bash
set -e

rm -rf chat-bot-image/wildfly-mcp-server-runner.jar
rm -rf chat-bot-image/server
rm -rf chat-bot-image/wait-mcp-server-runner.jar

cd ../wildfly-chat-bot
mvn clean install -Pcloud
cp -r target/server ../container-images/chat-bot-image

cd ../wildfly-mcp-server
mvn clean install
cp stdio/target/wildfly-mcp-server-stdio-runner.jar ../container-images/chat-bot-image

cd ../wait-mcp-server
mvn clean install
cp target/wait-mcp-server-runner.jar ../container-images/chat-bot-image

cd ../container-images/chat-bot-image
podman build -t quay.io/wildfly-snapshots/wildfly-chat-bot:latest .
rm -rf wildfly-mcp-server-runner.jar
rm -rf wait-mcp-server-runner.jar
rm -rf server

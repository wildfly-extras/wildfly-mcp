#!/bin/bash
set -e

rm -rf image/wildfly-mcp-server-runner.jar
rm -rf image/server

cd ../wildfly-chat-bot
mvn clean install -Pcloud
cp -r target/server ../container-image/image

cd ../wildfly-mcp-server
mvn clean install
cp stdio/target/wildfly-mcp-server-stdio-runner.jar ../container-image/image

cd ../wait-mcp-server
mvn clean install
cp target/wait-mcp-server-runner.jar ../container-image/image

cd ../container-image/image
podman build -t quay.io/wildfly-snapshots/wildfly-chat-bot:latest .
rm -rf wildfly-mcp-server-runner.jar
rm -rf server

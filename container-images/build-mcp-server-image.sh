#!/bin/bash
set -e

rm -rf mcp-server-image/wildfly-mcp-server-runner.jar

cd ../wildfly-mcp-server
mvn clean install
cp stdio/target/wildfly-mcp-server-stdio-runner.jar ../container-images/mcp-server-image

cd ../container-images/mcp-server-image
podman build -t quay.io/wildfly-snapshots/wildfly-mcp-server:latest .
rm -rf wildfly-mcp-server-runner.jar

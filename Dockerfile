# Generated by https://smithery.ai. See: https://smithery.ai/docs/config#dockerfile
# Use a Maven base image to build the WildFly MCP Server
FROM maven:3.8.5-openjdk-21 AS builder

# Set the working directory
WORKDIR /app

# Copy the necessary files to the container
COPY wildfly-mcp-server /app/wildfly-mcp-server

# Build the application
RUN mvn -f /app/wildfly-mcp-server/pom.xml clean install

# Use an OpenJDK base image to run the WildFly MCP Server
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the jar file from the builder stage
COPY --from=builder /app/wildfly-mcp-server/target/wildfly-mcp-server-1.0.0.Final-SNAPSHOT-runner.jar /app/wildfly-mcp-server.jar

# Specify the entry point for the container
ENTRYPOINT ["java", "-jar", "/app/wildfly-mcp-server.jar"]

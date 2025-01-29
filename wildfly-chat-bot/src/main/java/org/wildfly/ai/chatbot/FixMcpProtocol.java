/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.ClientMethod;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixMcpProtocol implements McpTransport {

    public static class InitializationNotification extends McpClientMessage {

        @JsonInclude
        public final String method = "notifications/initialized";

        public InitializationNotification() {
            super(null);
        }
    }

    static class ProcessStderrHandler implements Runnable {

        private final Process process;
        private static final Logger log = LoggerFactory.getLogger(ProcessStderrHandler.class);

        public ProcessStderrHandler(final Process process) {
            this.process = process;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[ERROR] {}", line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.debug("ProcessErrorPrinter has finished reading error output from process with PID = " + process.pid());
        }
    }

    static class ProcessIOHandler implements Runnable {

        private final Process process;
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final Logger log = LoggerFactory.getLogger(ProcessIOHandler.class);
        private final boolean logEvents;
        private final McpOperationHandler messageHandler;

        public ProcessIOHandler(Process process, McpOperationHandler messageHandler, boolean logEvents) {
            this.process = process;
            this.logEvents = logEvents;
            this.messageHandler = messageHandler;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logEvents) {
                        log.debug("< {}", line);
                    }
                    messageHandler.handle(OBJECT_MAPPER.readTree(line));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.debug("ProcessIOHandler has finished reading output from process with PID = {}", process.pid());
        }

        public void submit(String message) throws IOException {
            if (logEvents) {
                log.debug("> {}", message);
            }
            process.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        }
    }
    private final List<String> command;
    private final Map<String, String> environment;
    private Process process;
    private ProcessIOHandler processIOHandler;
    private final boolean logEvents;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(FixMcpProtocol.class);
    private volatile McpOperationHandler messageHandler;
    boolean init;

    public FixMcpProtocol(Builder builder) {
        this.command = builder.command;
        this.environment = builder.environment;
        this.logEvents = builder.logEvents;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        log.debug("Starting process: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().putAll(environment);
        try {
            process = processBuilder.start();
            log.debug("PID of the started process: {}", process.pid());
            process.onExit().thenRun(() -> {
                log.debug("Subprocess has exited with code: {}", process.exitValue());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        processIOHandler = new ProcessIOHandler(process, messageHandler, logEvents);
        // FIXME: where should we obtain the thread?
        new Thread(processIOHandler).start();
        new Thread(new ProcessStderrHandler(process)).start();
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> listTools(McpListToolsRequest operation) {
        try {
            if (!init) {
                // First send the missing operation
                String requestString = OBJECT_MAPPER.writeValueAsString(new InitializationNotification());
                CompletableFuture<JsonNode> node = execute(requestString, null);
                init = true;
            }
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> executeTool(McpCallToolRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cancelOperation(long operationId) {
        try {
            String requestString
                    = OBJECT_MAPPER.writeValueAsString(new CancellationNotification(operationId, "Timeout"));
            // Note: we're passing a null operationId here because this
            // argument refers to the 'cancellation' notification, not the
            // operation being cancelled. The cancellation is a notification
            // so it does not have any ID and does not expect any response.
            execute(requestString, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        process.destroy();
    }

    private CompletableFuture<JsonNode> execute(String request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.startOperation(id, future);
        }
        try {
            processIOHandler.submit(request);
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static class Builder {

        private List<String> command;
        private Map<String, String> environment;
        private boolean logEvents;

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder logEvents(boolean logEvents) {
            this.logEvents = logEvents;
            return this;
        }

        public FixMcpProtocol build() {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Missing command");
            }
            if (environment == null) {
                environment = Map.of();
            }
            return new FixMcpProtocol(this);
        }
    }
}

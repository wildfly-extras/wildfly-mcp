/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class McpClientInterceptor implements McpClient {

    private final McpClient delegate;
    private final ChatBotWebSocketEndpoint endpoint;
    McpClientInterceptor(McpClient delegate, ChatBotWebSocketEndpoint endpoint) {
        this.delegate = delegate;
        this.endpoint = endpoint;
    }
    @Override
    public List<ToolSpecification> listTools() {
        return delegate.listTools();
    }

    @Override
    public String executeTool(ToolExecutionRequest ter) {
        String ret = delegate.executeTool(ter);
        endpoint.traceToolUsage(ter.name(), ter.arguments(), ret);
        return ret;
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
    
}

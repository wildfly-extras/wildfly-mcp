/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import dev.langchain4j.mcp.client.protocol.McpClientMessage;

public class ListPrompts extends McpClientMessage {

    public final String method = "prompts/list";

    public ListPrompts(Long id) {
        super(id);
    }
    
}

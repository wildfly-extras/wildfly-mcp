/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import java.util.HashMap;
import java.util.Map;

public class GetPrompt extends McpClientMessage {

    public static class Params {

        public String name;
        public Map<String, String> arguments = new HashMap<>();
    }

    public final String method = "prompts/get";
    public Params params = new Params();
    
    public GetPrompt(Long id) {
        super(id);
    }

}

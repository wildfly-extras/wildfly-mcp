/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.mcp.client.transport.McpTransport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PromptHandler {
    private static final String SYSTEM_PROMPT="""
                                              You are a smart AI agent that can answer any kind of questions. In addition, 
            you have tools to interact with running WildFly servers and the users
            will ask you to perform operations like getting status, readling log files, retrieving prometheus metrics.
            """;
    Map<String, String> prompts = new HashMap<>();
    private final List<McpTransport> transports;
    Map<String, McpTransport> promptsTransport = new HashMap<>();
    public PromptHandler(List<McpTransport> transports) {
        this.transports = transports;
        prompts.put("default", SYSTEM_PROMPT);
    }

    public Set<Entry<String, String>> getPrompts() throws Exception {
        for(McpTransport t : transports) {
            CompletableFuture<JsonNode> resp = null; //t.executeOperationWithResponse(new ListPrompts(1l));
            System.out.println(resp.get().toPrettyString());
            ArrayNode promptsArray = (ArrayNode) resp.get().get("result").get("prompts");
            for (JsonNode p : promptsArray) {
                StringBuilder b = new StringBuilder();
                b.append(p.get("description").asText());
                if(p.has("arguments")) {
                    ArrayNode args = (ArrayNode) p.get("arguments");
                    for (JsonNode a : args) {
                        b.append("<p><b>" + a.get("name") + "(required="+a.get("required").asText()+ ")</b>: " + a.get("description"));
                    }
                }
                prompts.put(p.get("name").asText(), b.toString());
            }
        }
        return prompts.entrySet();
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String getPrompt(String prompt) {
        return prompts.get(prompt);
    }
}

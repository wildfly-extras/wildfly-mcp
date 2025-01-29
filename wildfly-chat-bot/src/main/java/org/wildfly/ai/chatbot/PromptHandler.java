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

/**
 *
 * @author jdenise
 */
public class PromptHandler {

    Map<String, String> prompts = new HashMap<>();
    private String prompt = "default";
    private final List<McpTransport> transports;
    Map<String, McpTransport> promptsTransport = new HashMap<>();
    public PromptHandler(List<McpTransport> transports) {
        this.transports = transports;
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

    public String getPrompt() {
        return prompts.get("default");
    }

    public String getPrompt(String prompt) {
        return prompts.get(prompt);
    }
}

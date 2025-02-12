/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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

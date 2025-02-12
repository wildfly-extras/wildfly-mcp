/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.ai.chatbot.prompt;

import dev.langchain4j.mcp.client.protocol.McpClientMessage;

public class ListPrompts extends McpClientMessage {

    public final String method = "prompts/list";

    public ListPrompts(Long id) {
        super(id);
    }
    
}

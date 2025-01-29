/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.mcp.client.protocol.ClientMethod;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;

/**
 *
 * @author jdenise
 */
public class ListPrompts extends McpClientMessage {
    @JsonInclude
    public final String method = "prompts/list";
    public ListPrompts(Long id) {
        super(id);
    }
    
}

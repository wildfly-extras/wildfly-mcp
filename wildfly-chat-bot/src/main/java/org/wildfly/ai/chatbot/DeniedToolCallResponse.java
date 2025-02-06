/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class DeniedToolCallResponse extends McpClientMessage {

    public Map<String,Object> result = new HashMap<>();
    public DeniedToolCallResponse(Long id) {
        super(id);
        List<Map<String,String>> lst = new ArrayList<>();
        Map<String, String> content = new HashMap<>();
        lst.add(content);
        content.put("type", "text");
        content.put("text", "The call to the tool has been denied by the user.");
        result.put("content", lst);
        result.put("isError", true);
    }
    
}

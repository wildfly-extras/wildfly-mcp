/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MCPConfig {

    public static class MCPServerStdioConfig {

        public String command;
        public List<String> args;
    }
    
    public static class MCPServerSSEConfig {

        public String url;
        public String providerUrl;
        public String clientId;
        public String secret;
        
    }

    public Map<String, MCPServerStdioConfig> mcpServers;
    public Map<String, MCPServerSSEConfig> mcpSSEServers;

    public static MCPConfig parseConfig(Path configFile) throws Exception {
        String content = new String(Files.readAllBytes(configFile));
        return new ObjectMapper().readValue(content, MCPConfig.class);
    }
}

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.ai.chatbot;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author jdenise
 */
public class ChatBotConfig {

    public static Path getMCPConfigPath() {
        String path = System.getenv("WILDFLY_CHAT_BOT_MCP_FILE");
        if (path == null) {
            path = System.getProperty("org.wildfly.ai.chatbot.mcp.config", "./mcp.json");
        }
        return Paths.get(path).toAbsolutePath();
    }

    public static String getActiveLLMModel() {
        String model = System.getenv("WILDFLY_CHAT_BOT_LLM_MODEL");
        if (model == null) {
            model = System.getProperty("org.wildfly.ai.chatbot.llm.name", "ollama");
        }
        return model;
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import java.io.IOException;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerSSEConfig;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerStdioConfig;

/**
 * WebSocket server endpoint example
 * <p/>
 *
 * This class produces a Websocket endpoint to receive messages from clients.
 *
 * @author <a href="mailto:benevides@redhat.com">Rafael Benevides</a>
 *
 */
@ServerEndpoint(value = "/chatbot",
        configurator = CustomConfigurator.class)
public class ChatBotWebSocketEndpoint {

    private Logger logger = Logger.getLogger(getClass().getName());

    @Inject
    @Named(value = "ollama")
    ChatLanguageModel ollama;
    @Inject
    @Named(value = "openai")
    ChatLanguageModel openai;
    //@Inject Instance<ChatLanguageModel> instance;
    private PromptHandler promptHandler;
    private Bot bot;
    private List<McpClient> clients = new ArrayList<>();
    private List<McpTransport> transports = new ArrayList<>();

    // It starts a Thread that notifies all sessions each second
    @PostConstruct
    public void init() {
        try {
            logger.info("Initialize");
            Path p = Paths.get(System.getProperty("org.wildfly.ai.chatbot.mcp.config", "./mcp.json"));
            MCPConfig mcpConfig = MCPConfig.parseConfig(p);
            clients = new ArrayList<>();
            if (mcpConfig.mcpServers != null) {
                for (Map.Entry<String, MCPServerStdioConfig> entry : mcpConfig.mcpServers.entrySet()) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add(entry.getValue().command);
                    cmd.addAll(entry.getValue().args);
                    McpTransport transport = new FixMcpProtocol.Builder()
                            .command(cmd)
                            .logEvents(true)
                            .build();
                    transports.add(transport);
                    McpClient mcpClient = new DefaultMcpClient.Builder()
                            .transport(transport)
                            .clientName(entry.getKey())
                            .build();
                    clients.add(mcpClient);
                }
            }
            if (mcpConfig.mcpSSEServers != null) {
                for (Map.Entry<String, MCPServerSSEConfig> entry : mcpConfig.mcpSSEServers.entrySet()) {
                    McpTransport transport = new HttpMcpTransport.Builder()
                            .sseUrl(entry.getValue().url)
                            .build();
                    transports.add(transport);
                    McpClient mcpClient = new DefaultMcpClient.Builder()
                            .transport(transport)
                            .clientName(entry.getKey())
                            .build();
                    clients.add(mcpClient);
                }
            }
            ToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(clients)
                    .build();
            promptHandler = new PromptHandler(transports);
            //Instance<ChatLanguageModel> model = CDI.current().select(ChatLanguageModel.class, NamedLiteral.of(System.getProperty("org.wildfly.ai.chatbot.llm.name", "ollama")));
            //ChatLanguageModel model = instance.select(NamedLiteral.of("openai")).get();
            String activellm = System.getProperty("org.wildfly.ai.chatbot.llm.name", "ollama");
            ChatLanguageModel model = null;
            if (activellm != null) {
                if (activellm.equals("ollama")) {
                    model = ollama;
                } else {
                    if (activellm.equals("openai")) {
                        model = ollama;
                    } else {
                        throw new RuntimeException("Unknown llm model " + activellm);
                    }
                }
            } else {
                model = ollama;
            }
            bot = AiServices.builder(Bot.class)
                    .chatLanguageModel(model)
                    .toolProvider(toolProvider)
                    .systemMessageProvider(chatMemoryId -> {
                        return promptHandler.getPrompt();
                    })
                    .build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // store the session once that it's opened
    @OnOpen
    public void onOpen(Session session) throws IOException {
        logger.info("New websocket session opened: " + session.getId());
        session.getBasicRemote().sendText("Hello, I am a WildFly chatbot that can interact with your WildFly servers, how can I help?");
    }

    // remove the session after it's closed
    @OnClose
    public void onClose(Session session) throws Exception {
        logger.info("Websoket session closed: " + session.getId());
        for (McpClient client : clients) {
            client.close();
        }
        session.getBasicRemote().sendText("The session has been closed...");
    }

    // This method receives a Message that contains a command
    // The Message object is "decoded" by the MessageDecoder class
    @OnMessage
    public String onMessage(String question, Session session) throws IOException {
        try {
            if ("___###___help".equals(question)) {
                StringBuilder help = new StringBuilder();
                help.append("<p><b>/tools</b>: List tools" + "<br></p>");
                help.append("<p><b>/prompt</b>: Get system prompt" + "<br></p>");
                help.append("<p><b>/prompt-list</b>: List prompts" + "<br></p>");
                help.append("<p><b>/prompt-run <prompt name></b>: Run the prompt" + "<br></p>");
                return help.toString();
            }
            if ("___###___prompt-list".equals(question)) {
                StringBuilder prompts = new StringBuilder();
                for (Map.Entry<String, String> entry : promptHandler.getPrompts()) {
                    prompts.append("<p><b>" + entry.getKey() + "</b>:" + entry.getValue() + "<br></p>");
                }
                return prompts.toString();
            }
            if (question.startsWith("___###___prompt-run")) {
                String name = question.substring("/prompt-run".length());
                String prompt = promptHandler.getPrompt(name.trim());
                if (prompt == null) {
                    return "Hoops...prompt " + name.trim() + " doesn't exist...";
                } else {
                    return bot.chat(prompt);
                }
            }
            if ("___###___tools".equals(question)) {
                StringBuilder tools = new StringBuilder();
                for (McpClient client : clients) {
                    List<ToolSpecification> specs = client.listTools();
                    for (ToolSpecification s : specs) {
                        tools.append("<p><b>" + s.name() + "</b>: " + s.description() + "<br></p>");
                    }
                }
                return tools.toString();
            }
            if (question.startsWith("___###___")) {
                return "Invalid help command " + question;
            }
            return bot.chat(question);
        } catch (Exception ex) {
            ex.printStackTrace();
            return "Arghhh...An internal error occured " + ex.toString();
        }
    }

    // Exception handling
    @OnError
    public void error(Session session, Throwable t) {
        t.printStackTrace();
    }
}

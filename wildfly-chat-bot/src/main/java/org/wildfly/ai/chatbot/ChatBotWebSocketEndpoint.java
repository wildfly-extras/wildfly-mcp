/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerSSEConfig;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerStdioConfig;

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
    @Inject
    @Named(value = "groq")
    ChatLanguageModel groq;
    //@Inject Instance<ChatLanguageModel> instance;
    private PromptHandler promptHandler;
    private Bot bot;
    private List<McpClient> clients = new ArrayList<>();
    private List<McpTransport> transports = new ArrayList<>();
    private Session session;

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
                    clients.add(new McpClientInterceptor(mcpClient, this));
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
                    clients.add(new McpClientInterceptor(mcpClient, this));
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
                        model = openai;
                    } else {
                        if (activellm.equals("groq")) {
                            model = groq;
                        } else {
                            throw new RuntimeException("Unknown llm model " + activellm);
                        }
                    }
                }
            } else {
                model = ollama;
            }
            bot = AiServices.builder(Bot.class)
                    .chatLanguageModel(model)
                    .toolProvider(toolProvider)
                    .systemMessageProvider(chatMemoryId -> {
                        return promptHandler.getSystemPrompt();
                    })
                    .build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // store the session once that it's opened
    @OnOpen
    public void onOpen(Session session) throws IOException {
        this.session = session;
        logger.info("New websocket session opened: " + session.getId());
        Map<String, String> args = new HashMap<>();
        args.put("kind", "simple_text");
        args.put("value", "Hello, I am a WildFly chatbot that can interact with your WildFly servers, how can I help?");
        session.getBasicRemote().sendText(toJson(args));
    }

    String toJson(Object msg) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(msg);
    }

    Map<String, String> toMap(String msg) throws Exception {
        ObjectMapper om = new ObjectMapper();
        return om.readValue(msg, new TypeReference<>() {
        });
    }

    void traceToolUsage(String tool, String args, String reply) {
        try {
            logger.info("Tool calling: " + tool + args + " reply :" + reply);
            Map<String, String> map = new HashMap<>();
            map.put("kind", "tool_call");
            map.put("tool", tool);
            map.put("args", args);
            //map.put("reply", reply);
            map.put("loadingRequired", "true");
            session.getBasicRemote().sendText(toJson(map));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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
            Map<String, String> msg = toMap(question);
            String kind = msg.get("kind");
            if ("list_tools".equals(kind)) {
                StringBuilder tools = new StringBuilder("Available tools (Thoses tools are callable by your LLM when it is computing replies):\n");
                for (McpClient client : clients) {
                    List<ToolSpecification> specs = client.listTools();
                    for (ToolSpecification s : specs) {
                        tools.append("<p><b>" + s.name() + "</b>: " + s.description() + "<br></p>");
                    }
                }
                Map<String, String> map = new HashMap<>();
                map.put("kind", "simple_text");
                map.put("value", tools.toString());
                return toJson(map);
            }
            if ("user_question".equals(kind)) {
                String reply = bot.chat(msg.get("value"));
                if (reply == null || reply.isEmpty()) {
                    reply = "I have not been able to answer your question.";
                }
                Map<String, String> map = new HashMap<>();
                map.put("kind", "simple_text");
                map.put("value", reply);
                return toJson(map);
            }
            throw new Exception("Unknown message " + kind);
        } catch (Exception ex) {
            ex.printStackTrace();
            Map<String, String> map = new HashMap<>();
            map.put("kind", "simple_text");
            map.put("value", "Arghhh...An internal error occured " + ex.toString());
            return toJson(map);
        }
    }

    // Exception handling
    @OnError
    public void error(Session session, Throwable t) {
        t.printStackTrace();
    }
}

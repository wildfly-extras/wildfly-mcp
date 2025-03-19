/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import org.wildfly.ai.chatbot.tool.ToolHandler;
import org.wildfly.ai.chatbot.tool.SelectedTool;
import org.wildfly.ai.chatbot.tool.ToolDescription;
import org.wildfly.ai.chatbot.prompt.PromptHandler;
import org.wildfly.ai.chatbot.prompt.SelectedPrompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerSSEConfig;
import org.wildfly.ai.chatbot.MCPConfig.MCPServerStdioConfig;
import org.wildfly.ai.chatbot.http.HttpMcpTransport;

@ServerEndpoint(value = "/chatbot")
public class ChatBotWebSocketEndpoint {

    private static final Logger logger = Logger.getLogger(ChatBotWebSocketEndpoint.class.getName());

    @Inject
    @Named(value = "ollama")
    ChatLanguageModel ollama;
    @Inject
    @Named(value = "mistral")
    ChatLanguageModel mistral;
    @Inject
    @Named(value = "openai")
    ChatLanguageModel openai;
    @Inject
    @Named(value = "github")
    ChatLanguageModel github;
    @Inject
    @Named(value = "groq")
    ChatLanguageModel groq;
    @Inject
    @ConfigProperty(name = "wildfly.chatbot.mcp.config.file")
    private Path mcpConfigFile;
    @Inject
    @ConfigProperty(name = "wildfly.chatbot.llm.name")
    private String llmName;
    @Inject
    @ConfigProperty(name = "wildfly.chatbot.system.prompt")
    private Optional<String> systemPrompt;
    @Inject
    @ConfigProperty(name = "wildfly.chatbot.welcome.message")
    private String welcomeMessage;
    private boolean disabledAcceptance;
    private PromptHandler promptHandler;
    private ToolHandler toolHandler;
    private Bot bot;
    private List<McpClient> clients = new ArrayList<>();
    private final List<McpTransport> transports = new ArrayList<>();
    private Session session;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final BlockingQueue<String> workQueue = new ArrayBlockingQueue<>(1);
    private final Map<String, DefaultTokenProvider> tokenProviders = new HashMap<>();
    private final List<DefaultTokenProvider> initProviders = new ArrayList<>();
    private final List<DefaultMcpClient.Builder> clientBuilders = new ArrayList<>();
    private ChatLanguageModel activeModel;
    private String initExceptionMessage;
    private String initExceptionContext;

    @PostConstruct
    public void init() {
        try {
            logger.info("Initialize");
            MCPConfig mcpConfig = MCPConfig.parseConfig(mcpConfigFile);
            clients = new ArrayList<>();
            if (mcpConfig.mcpServers != null) {
                for (Map.Entry<String, MCPServerStdioConfig> entry : mcpConfig.mcpServers.entrySet()) {
                    List<String> cmd = new ArrayList<>();
                    try {
                        cmd.add(entry.getValue().command);
                        cmd.addAll(entry.getValue().args);
                        McpTransport transport = new StdioMcpTransport.Builder()
                                .command(cmd)
                                .logEvents(true)
                                .build();
                        transports.add(transport);
                        McpClient mcpClient = new DefaultMcpClient.Builder()
                                .transport(transport)
                                .clientName(entry.getKey())
                                .build();
                        clients.add(new McpClientInterceptor(mcpClient, this));
                    } catch (Exception ex) {
                        initExceptionContext = "Error initializing MCP server " + entry.getKey() + ". " + cmd;
                        throw ex;
                    }
                }
            }
            if (mcpConfig.mcpSSEServers != null) {
                for (Map.Entry<String, MCPServerSSEConfig> entry : mcpConfig.mcpSSEServers.entrySet()) {
                    try {
                        McpTransport transport;
                        if (entry.getValue().providerUrl != null) {
                            DefaultTokenProvider tokenProvider = new DefaultTokenProvider();
                            tokenProvider.name = entry.getKey();
                            tokenProvider.providerUrl = entry.getValue().providerUrl;
                            tokenProvider.secret = entry.getValue().secret;
                            tokenProvider.clientId = entry.getValue().clientId;
                            tokenProviders.put(entry.getKey(), tokenProvider);
                            transport = new HttpMcpTransport.Builder()
                                    .timeout(Duration.ZERO)
                                    .tokenProvider(tokenProvider)
                                    .sseUrl(entry.getValue().url)
                                    .build();
                        } else {
                            transport = new HttpMcpTransport.Builder()
                                    .timeout(Duration.ZERO)
                                    .sseUrl(entry.getValue().url)
                                    .build();
                        }
                        transports.add(transport);
                        DefaultMcpClient.Builder builder = new DefaultMcpClient.Builder()
                                .transport(transport)
                                .clientName(entry.getKey());
                        clientBuilders.add(builder);
                    } catch (Exception ex) {
                        initExceptionContext = "Error initializing MCP server " + entry.getKey();
                        throw ex;
                    }
                }
            }
            initProviders.addAll(tokenProviders.values());

            promptHandler = new PromptHandler(transports);
            toolHandler = new ToolHandler(clients);
            if (llmName != null) {
                if (llmName.equals("ollama")) {
                    activeModel = ollama;
                } else {
                    if (llmName.equals("openai")) {
                        activeModel = openai;
                    } else {
                        if (llmName.equals("groq")) {
                            activeModel = groq;
                        } else {
                            if (llmName.equals("mistral")) {
                                activeModel = mistral;
                            } else {
                                if (llmName.equals("github")) {
                                    activeModel = github;
                                } else {
                                    throw new RuntimeException("Unknown llm model name " + llmName);
                                }
                            }
                        }
                    }
                }
            } else {
                activeModel = ollama;
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            handleException(ex);
        }
    }

    private void handleException(Exception ex) {
        initExceptionMessage = ex.getMessage();
    }

    @OnOpen
    public void onOpen(Session session) throws Exception {
        this.session = session;
        logger.info("New websocket session opened: " + session.getId());
        if (initExceptionMessage == null) {
            try {
                login();
            } catch (Exception ex) {
                handleException(ex);
                reportException();
            }
        } else {
            reportException();
        }
    }

    private boolean reportException() throws Exception {
        if (initExceptionMessage != null) {
            Map<String, String> args = new HashMap<>();
            initExceptionContext = initExceptionContext == null ? "An exception occured" : initExceptionContext;
            args.put("kind", "simple_text");
            args.put("value", "<b>Error, chat bot initialization failed.</b>\n * " 
                    + initExceptionContext + "\n * Exception message: \n" 
                    + initExceptionMessage + "\n <p>Please check the chat bot traces to fix the problem.</p>");
            session.getBasicRemote().sendText(toJson(args));
            return true;
        }
        return false;
    }

    private void login() throws IOException {
        if (initProviders.isEmpty()) {
            //Terminate client init
            for (DefaultMcpClient.Builder builder : clientBuilders) {
                clients.add(new McpClientInterceptor(builder.build(), this));
            }
            ToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(clients)
                    .build();
            bot = AiServices.builder(Bot.class)
                    .chatLanguageModel(activeModel)
                    .toolProvider(toolProvider)
                    .systemMessageProvider(chatMemoryId -> {
                        return promptHandler.getSystemPrompt() + (systemPrompt.isPresent() ? systemPrompt.get() : "");
                    })
                    .build();
            Map<String, String> args = new HashMap<>();
            args.put("kind", "simple_text");
            args.put("value", welcomeMessage);
            session.getBasicRemote().sendText(toJson(args));
        } else {
            DefaultTokenProvider dtp = initProviders.remove(0);
            Map<String, String> args = new HashMap<>();
            args.put("kind", "login");
            args.put("name", dtp.name);
            session.getBasicRemote().sendText(toJson(args));
        }
    }

    public static String toJson(Object msg) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer();
        return ow.writeValueAsString(msg);
    }

    void traceToolCalled(String tool, String args, String reply) {
        try {
            logger.info("Tool called: " + tool + args + " reply :" + reply);
            Map<String, String> map = new HashMap<>();
            map.put("kind", "tool_called");
            map.put("tool", tool);
            map.put("args", args);
            //map.put("reply", reply);
            map.put("loadingRequired", "true");
            session.getBasicRemote().sendText(toJson(map));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    boolean canCallTool(String tool, String args) {
        try {
            if (disabledAcceptance) {
                return true;
            }
            logger.info("Can call: " + tool + args);
            Map<String, String> map = new HashMap<>();
            map.put("kind", "tool_calling");
            map.put("tool", tool);
            map.put("args", args);
            session.getBasicRemote().sendText(toJson(map));
            String reply = workQueue.take();
            logger.info("Unlocked in waiting thread: " + reply);
            return Boolean.parseBoolean(reply);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    @OnClose
    public void onClose(Session session) throws Exception {
        logger.info("Websoket session closed: " + session.getId());
        for (McpClient client : clients) {
            client.close();
        }
        session.getBasicRemote().sendText("The session has been closed...");
    }

    @OnMessage
    public void onMessage(String question, Session session) throws IOException {
        try {
            // In case we have an exception during initialization
            if (reportException()) {
                return;
            }
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode msgObj = objectMapper.readTree(question);
            String kind = msgObj.get("kind").asText();
            if ("list_tools".equals(kind)) {
                List<ToolDescription> tools = toolHandler.getTools();
                Map<String, Object> map = new HashMap<>();
                map.put("kind", "tools");
                map.put("value", tools);
                logger.info("Tools sent to UI " + toJson(map));
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("list_prompts".equals(kind)) {
                promptHandler.getPrompts();
                Map<String, Object> map = new HashMap<>();
                map.put("kind", "prompts");
                map.put("value", promptHandler.getPrompts());
                logger.info("Prompts sent to UI " + toJson(map));
                session.getBasicRemote().sendText(toJson(map));
                return;
            }
            if ("use_prompt".equals(kind)) {
                JsonNode promptNode = msgObj.get("value");
                SelectedPrompt prompt = objectMapper.treeToValue(promptNode, SelectedPrompt.class);
                logger.info("Prompt received from UI " + promptNode.toPrettyString());
                // Retrieve the prompt
                String userPrompt = promptHandler.getPrompt(prompt);
                // Ask The UI to emulate a user question (loading, ...)
                Map<String, String> map = new HashMap<>();
                map.put("kind", "emulate_user_question");
                map.put("value", userPrompt);
                session.getBasicRemote().sendText(toJson(map));

                return;
            }
            if ("call_tool".equals(kind)) {
                JsonNode toolNode = msgObj.get("value");
                SelectedTool tool = objectMapper.treeToValue(toolNode, SelectedTool.class);
                try {
                    disabledAcceptance = true;
                    String reply = toolHandler.executeTool(tool);
                    Map<String, String> map = new HashMap<>();
                    map.put("kind", "simple_text");
                    map.put("value", reply);
                    session.getBasicRemote().sendText(toJson(map));
                    return;
                } finally {
                    disabledAcceptance = false;
                }
            }
            if ("user_question".equals(kind)) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String reply = bot.chat(msgObj.get("value").asText());
                            if (reply == null || reply.isEmpty()) {
                                reply = "I have not been able to answer your question.";
                            }
                            Map<String, String> map = new HashMap<>();
                            map.put("kind", "simple_text");
                            map.put("value", reply);
                            session.getBasicRemote().sendText(toJson(map));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Map<String, String> map = new HashMap<>();
                            map.put("kind", "simple_text");
                            map.put("value", "Arghhh...An internal error occured " + ex.toString());
                            try {
                                session.getBasicRemote().sendText(toJson(map));
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.log(Level.SEVERE, "Error: " + e);
                            }
                        }
                    }
                }
                );
                return;
            }
            if ("tool_acceptance_reply".equals(kind)) {
                String reply = msgObj.get("value").asText();
                logger.info("Received tool acceptance message: " + reply);
                workQueue.offer(reply);
                return;
            }
            if ("login_reply".equals(kind)) {
                JsonNode login = msgObj.get("value");
                String tokenProvider = login.get("name").asText();
                String userName = login.get("userName").asText();
                String password = login.get("password").asText();
                try {
                    tokenProviders.get(tokenProvider).setCredentials(userName, password);
                } catch (MCPAuthenticationException ex) {
                    // add back the provider to the list and attempt login again
                    initProviders.add(0, tokenProviders.get(tokenProvider));
                    Map<String, String> map = new HashMap<>();
                    map.put("kind", "simple_text");
                    map.put("value", ex.getMessage());
                    session.getBasicRemote().sendText(toJson(map));
                }
                login();
                return;
            }
            throw new Exception("Unknown message " + kind);
        } catch (Exception ex) {
            ex.printStackTrace();
            Map<String, String> map = new HashMap<>();
            map.put("kind", "simple_text");
            map.put("value", "Arghhh...An internal error occured " + ex.toString());
            session.getBasicRemote().sendText(toJson(map));
        }
    }

    // Exception handling
    @OnError
    public void error(Session session, Throwable t) {
        t.printStackTrace();
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.wildfly.ai.chatbot.tool.SelectedTool.ToolArg;

public class ToolHandler {

    private final List<McpClient> clients;
    private final Map<String, McpClient> toolToClient = new HashMap<>();

    public ToolHandler(List<McpClient> clients) {
        this.clients = clients;
    }

    public List<ToolDescription> getTools() throws Exception {
        List<ToolDescription> tools = new ArrayList<>();
        for (McpClient client : clients) {
            List<ToolSpecification> specs = client.listTools();
            for (ToolSpecification s : specs) {
                toolToClient.put(s.name(), client);
                List<ToolDescription.ToolArg> arguments = new ArrayList<>();
                List<String> required = s.parameters().required();
                for (Map.Entry<String, JsonSchemaElement> entry : s.parameters().properties().entrySet()) {
                    // Support only String tools...
                    String name = entry.getKey();
                    JsonStringSchema schema = (JsonStringSchema) entry.getValue();
                    String description = schema.description();
                    ToolDescription.ToolArg arg = new ToolDescription.ToolArg();
                    arg.description = description;
                    arg.name = name;
                    arg.required = required.contains(name) ? "true" : "false";
                    arguments.add(arg);
                }
                ToolDescription td = new ToolDescription(s.name(), s.description(), arguments);
                tools.add(td);
            }
        }
        return tools;
    }

    public String executeTool(SelectedTool tool) throws Exception {
        McpClient client = toolToClient.get(tool.name);
        StringBuilder jsonArguments = new StringBuilder("{");
        Iterator<ToolArg> it = tool.arguments.iterator();
        while(it.hasNext()) {
            ToolArg arg = it.next();
            jsonArguments.append("\""+ arg.name + "\": " + "\""+arg.value+"\"");
            if(it.hasNext()) {
                jsonArguments.append(",");
            }
        }
        jsonArguments.append("}");
        System.out.println("STIRNG JSON " + jsonArguments);
        ToolExecutionRequest req = ToolExecutionRequest.builder().arguments(jsonArguments.toString()).id("1").name(tool.name).build();
                
        return client.executeTool(req);
    }
}

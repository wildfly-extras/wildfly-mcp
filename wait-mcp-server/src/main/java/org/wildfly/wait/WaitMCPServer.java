/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.wait;

import io.quarkiverse.mcp.server.TextContent;
import java.util.List;


import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import java.util.ArrayList;

public class WaitMCPServer {
    @Tool(description = "Wait a number of seconds.")
    ToolResponse wait(
            @ToolArg(name = "seconds", description = "Required number of seconds to wait.", required = true) String seconds) {
        List<TextContent> lst = new ArrayList<>();
        try {
            Thread.sleep(Integer.parseInt(seconds)*1000);
            lst.add(new TextContent("I have waited " + seconds + "seconds"));
            return new ToolResponse(false, lst);
        } catch (Exception ex) {
            lst.add(new TextContent(ex.toString()));
            return new ToolResponse(true, lst);
        }
    }
}

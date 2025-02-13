/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.tool;

import java.util.List;

public class SelectedTool {

    public static class ToolArg {

        public String name;
        public String value;
    }
    public String name;
    public List<ToolArg> arguments;
}

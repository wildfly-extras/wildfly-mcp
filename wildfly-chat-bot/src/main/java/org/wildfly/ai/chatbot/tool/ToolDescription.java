/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.tool;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class ToolDescription {
    
    public static class ToolArg {

        public String name;
        public String description;
        public String required;
    }
    public final String name;
    public final String description;
    public final List<ToolArg> arguments;

    public ToolDescription(String name, String description, List<ToolArg> args) {
        this.name = name;
        this.description = description;
        this.arguments = Collections.unmodifiableList(args);
    }
}

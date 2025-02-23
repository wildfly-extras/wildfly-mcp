/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot.prompt;

import java.util.List;

public class SelectedPrompt {

    public static class PromptArg {

        public String name;
        public String value;
    }
    public String name;
    public List<PromptArg> arguments;
}

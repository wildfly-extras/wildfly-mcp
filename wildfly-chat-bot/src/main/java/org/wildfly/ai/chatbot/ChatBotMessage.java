/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class ChatBotMessage {
    public String kind;
    public Map<String, String> args = new HashMap<>();
}

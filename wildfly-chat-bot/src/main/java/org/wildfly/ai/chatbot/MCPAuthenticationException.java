/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

/**
 *
 * @author jdenise
 */
public class MCPAuthenticationException extends Exception {
    MCPAuthenticationException(String msg) {
        super(msg);
    }
}

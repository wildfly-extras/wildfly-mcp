/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
/**
 *
 * @author Emmanuel Hugonnet (c) 2024 Red Hat, Inc.
 */
public class CustomConfigurator extends ServerEndpointConfig.Configurator {

    @Override
    public void modifyHandshake(ServerEndpointConfig conf,
                                HandshakeRequest req,
                                HandshakeResponse resp) {
        if( req.getHttpSession() != null) {
            System.out.println("Session : "  + ((HttpSession)req.getHttpSession()).getId());
            conf.getUserProperties().put("httpSessionId", ((HttpSession)req.getHttpSession()).getId());
        }
    }

}
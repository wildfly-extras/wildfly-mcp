/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

/**
 *
 * @author jdenise
 */
public class Server {

    public final String host;
    public final String port;

    public Server(String host, String port) {
        if (host == null || host.trim().isEmpty()) {
            host = System.getProperty("org.wildfly.host.name");
            if(host == null) {
                host = "localhost";
            }
        }
        if (port == null || port.trim().isEmpty()) {
            port = System.getProperty("org.wildfly.port");
            if(port == null) {
                port = "9990";
            }
        }
        this.host = host;
        this.port = port;
    }
}

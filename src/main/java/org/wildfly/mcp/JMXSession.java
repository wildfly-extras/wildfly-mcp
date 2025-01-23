/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author jdenise
 */
public class JMXSession implements AutoCloseable {

    private final JMXConnector jmxConnector;
    public final MBeanServerConnection connection;

    public JMXSession(Server server) throws Exception {
        String urlString = "service:jmx:remote+http://" + server.host + ":" + server.port;
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        jmxConnector = JMXConnectorFactory.connect(serviceURL, null);
        connection = jmxConnector.getMBeanServerConnection();
    }

    @Override
    public void close() throws Exception {
        jmxConnector.close();
    }
}

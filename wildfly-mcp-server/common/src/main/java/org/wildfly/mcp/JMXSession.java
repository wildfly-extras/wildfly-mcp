/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import java.util.HashMap;
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

    public JMXSession(Server server, User user) throws Exception {
        String urlString = "service:jmx:remote+http://" + server.host + ":" + server.port;
        JMXServiceURL serviceURL = new JMXServiceURL(urlString);
        HashMap<String, Object> environment = new HashMap<>();
        if (user.userName != null && user.userPassword != null) {
            environment.put(JMXConnector.CREDENTIALS, new String[] { user.userName, user.userPassword });
        }
        jmxConnector = JMXConnectorFactory.connect(serviceURL, environment);
        connection = jmxConnector.getMBeanServerConnection();
    }

    @Override
    public void close() throws Exception {
        jmxConnector.close();
    }
}

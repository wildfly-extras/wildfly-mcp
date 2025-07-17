/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.TextContent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.jboss.galleon.util.IoUtils;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class WildFlyMCPServerTest {

    @Test
    public void testListAddOns() throws Exception {
        WildFlyMCPServer server = new WildFlyMCPServer();
        // Call the method
        ToolResponse toolResponse = server.getWildFlyProvisioningAddOns(getResourceAsFile("kitchensink.war").toAbsolutePath().toString());

        // Assertions
        assertFalse(toolResponse.isError(), toolResponse.content().toString());
        String xmlResponse = ((TextContent) toolResponse.content().get(0)).text();
        assertTrue(xmlResponse.contains("hal-web-console"));
        assertTrue(xmlResponse.contains("wildfly-cli"));
    }

    @Test
    public void testProvisionServer() throws Exception {
        WildFlyMCPServer server = new WildFlyMCPServer();
        // Call the method
        String deployment = getResourceAsFile("kitchensink.war").toAbsolutePath().toString();
        Path target = Files.createTempDirectory("wildfly-mcp-tests");
        try {
            Path serverDir = target.resolve("my-wildfly-server");
            ToolResponse toolResponse = server.provisionWildFlyServerForDeployment(deployment, serverDir.toString(), null, null, null, false);
            // Assertions
            assertFalse(toolResponse.isError(), toolResponse.content().toString());
            assertTrue(Files.exists(serverDir));
        } finally {
            FileUtils.deleteDirectory(target.toFile());
        }
    }

    public static Path getResourceAsFile(String war) throws Exception {
        InputStream in = WildFlyMCPServerTest.class.getResourceAsStream("/" + war);
        File tempFile = File.createTempFile("test", ".war");
        tempFile.deleteOnExit();

        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile.toPath();
    }
}

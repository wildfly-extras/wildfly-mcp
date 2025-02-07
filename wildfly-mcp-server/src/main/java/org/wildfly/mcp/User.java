/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

/**
 *
 * @author jdenise
 */
public class User {
    public final String userName;
    public final String userPassword;

    public User(String userName, String userPassword) {
        if (userName == null || userName.trim().isEmpty()) {
            userName = System.getProperty("org.wildfly.user.name");
        }
        if (userPassword == null || userPassword.trim().isEmpty()) {
            userPassword = System.getProperty("org.wildfly.user.password");
        }
        this.userName = userName;
        this.userPassword = userPassword;
    }
}

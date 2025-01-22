/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp;

import java.util.Objects;

/**
 *
 * @author jdenise
 */
public class User {

    public static class NullUserException extends Exception {
        NullUserException(String message) {
            super(message);
        }
    }
    public final String userName;
    public final String userPassword;

    public User(String userName, String userPassword) throws NullUserException {
        if (userName == null || userName.trim().isEmpty()) {
            userName = System.getProperty("org.wildfly.user.name");
        }
        if (userPassword == null || userPassword.trim().isEmpty()) {
            userPassword = System.getProperty("org.wildfly.user.password");
        }
        this.userName = userName;
        this.userPassword = userPassword;
        if(this.userName == null || this.userName.isEmpty()) {
            throw new NullUserException("User Name is required.");
        }
        if(this.userPassword == null || this.userPassword.isEmpty()) {
            throw new NullUserException("User Password is required.");
        }
    }
}

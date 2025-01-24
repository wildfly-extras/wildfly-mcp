/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.mcp;

import java.io.Console;
import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;

/**
 *
 * @author jdenise
 */
public class ClientCallbackHandler implements CallbackHandler {

    private final Console console;
    private boolean promptShown = false;
    private String username;
    private char[] password;

    public ClientCallbackHandler(final String username, final String password) {
        console = System.console();
        this.username = username;
        if (password != null) {
            this.password = password.toCharArray();
        }
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        // Special case for anonymous authentication to avoid prompting user for their name.
        if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
            ((NameCallback) callbacks[0]).setName("anonymous demo user");
            return;
        }

        for (Callback current : callbacks) {
            if (current instanceof RealmCallback) {
                final RealmCallback rcb = (RealmCallback) current;
                final String defaultText = rcb.getDefaultText();
                rcb.setText(defaultText); // For now just use the realm suggested.

                prompt(defaultText);
            } else if (current instanceof RealmChoiceCallback) {
                throw new UnsupportedCallbackException(current, "Realm choice not currently supported.");
            } else if (current instanceof NameCallback) {
                final NameCallback ncb = (NameCallback) current;
                final String userName = obtainUsername("Username:");

                ncb.setName(userName);
            } else if (current instanceof PasswordCallback) {
                PasswordCallback pcb = (PasswordCallback) current;
                char[] password = obtainPassword("Password:");

                pcb.setPassword(password);
            } else {
                throw new UnsupportedCallbackException(current);
            }
        }
    }

    private void prompt(final String realm) {
        if (!promptShown) {
            promptShown = true;
        }
    }

    private String obtainUsername(final String prompt) {
        if (username == null) {
            checkConsole();
            username = console.readLine(prompt);
        }
        return username;
    }

    private char[] obtainPassword(final String prompt) {
        if (password == null) {
            checkConsole();
            password = console.readPassword(prompt);
        }

        return password;
    }

    private void checkConsole() {
        if (console == null) {
            throw new IllegalStateException(
                    "The environment does not have a usable console. Cannot prompt for user name and password");
        }
    }

}

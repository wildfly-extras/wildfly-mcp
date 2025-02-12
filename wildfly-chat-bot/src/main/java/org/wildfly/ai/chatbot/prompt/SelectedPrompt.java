/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.ai.chatbot.prompt;

import java.util.List;

public class SelectedPrompt {

    public static class PromptArg {

        public String name;
        public String value;
    }
    public String name;
    public List<PromptArg> arguments;
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class Recorder {

    public List<UserQuestionWithTools> messagesComplete = new ArrayList<>();
    public List<UserQuestion> messages = new ArrayList<>();
    private UserQuestionWithTools currentComplete;
    private UserQuestion current;

    public static class ExecutedTool {

        public final String name;
        public final String args;
        public final String reply;

        ExecutedTool(String name, String args, String reply) {
            this.name = name;
            this.args = args;
            this.reply = reply;
        }
    }

    public static class UserQuestion {
        public final String date;
        public final String user;
        public String agent;
        public UserQuestion(String user) {
            this.user = user;
            date = new Date().toString();
        }
    }

    public static class UserQuestionWithTools extends UserQuestion {
        UserQuestionWithTools(String user) {
            super(user);
        }
        public List<ExecutedTool> calledTools = new ArrayList<>();
    }

    public void newInteraction(String question) {
        current = new UserQuestion(question);
        currentComplete = new UserQuestionWithTools(question);
    }

    public void toolCalled(ToolExecutionRequest req, String reply) {
        currentComplete.calledTools.add(new ExecutedTool(req.name(), req.arguments(), reply));
    }

    public void interactionDone(String reply) {
        current.agent = reply;
        currentComplete.agent = reply;
        messages.add(current);
        messagesComplete.add(currentComplete);
    }
    
    public List<? extends UserQuestion> getCompleteRecord() {
        return messagesComplete;
    }
    public List<? extends UserQuestion> getSmallRecord() {
        return messages;
    }
    public boolean isEmpty() {
        return messages.isEmpty();
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.wildfly.ai.chatbot.http.HttpMcpTransport.TokenProvider;

/**
 *
 * @author jdenise
 */
public class DefaultTokenProvider implements TokenProvider {
    public String name;
    public String providerUrl;
    public String clientId;
    public String secret = "";
    private String userName;
    private String password;
    public void setCredentials(String userName, String password) throws Exception {
        this.userName = userName;
        this.password = password;
        getToken();
    }
    @Override
    public String getToken() throws Exception {
        String valueToEncode = clientId + ":" + secret;
        String basic = Base64.getEncoder().encodeToString(valueToEncode.getBytes());
        return call(basic);
    }

    public String call(String basic) throws Exception {

        HttpClientBuilder builder = HttpClients.custom();

        try (CloseableHttpClient httpclient = builder.build()) {
            HttpPost httppost = new HttpPost(providerUrl + "/protocol/openid-connect/token");
            httppost.addHeader("Authorization", "Basic " + basic);
            httppost.addHeader("content-type", "application/x-www-form-urlencoded");
            List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("username", userName));
            nvps.add(new BasicNameValuePair("password", password));
            nvps.add(new BasicNameValuePair("grant_type", "password"));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(nvps);
            httppost.setEntity(entity);
            try (CloseableHttpResponse response = httpclient.execute(httppost)) {
                int code = response.getStatusLine().getStatusCode();
                if(code != 200) {
                    if (code == 401) {
                        throw new MCPAuthenticationException("Authentication failed.");
                    } else {
                        throw new Exception("Invalid reply " + code);
                    }
                }
                String reply = EntityUtils.toString(response.getEntity());
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode msgObj = objectMapper.readTree(reply);
                return msgObj.get("access_token").asText();
            }
        }
    }
}

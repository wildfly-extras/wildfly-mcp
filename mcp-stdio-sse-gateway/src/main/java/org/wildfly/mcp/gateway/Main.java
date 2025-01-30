/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.mcp.gateway;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

public class Main {

    private static final String SSE = "/sse";
    private static File f = new File("./mcp-gateway.log");

    private static void log(String str) {
        try {
            Files.write(f.toPath(), str.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ex) {
        }
    }

    public static void main(String[] args) throws Exception {
        String sseURIStr = args[0];
        String serviceURI = sseURIStr;
        if (sseURIStr.endsWith(SSE)) {
            int end = sseURIStr.indexOf(SSE);
            serviceURI = sseURIStr.substring(0, end);
        }
        final String finalServiceURI = serviceURI;
        URI sseURI = new URI(sseURIStr);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        BlockingQueue<String> workQueue = new ArrayBlockingQueue(1);
        Files.deleteIfExists(f.toPath());
        Files.write(f.toPath(), "Starting gateway\n".getBytes(), StandardOpenOption.CREATE_NEW);

        HttpClient httpClient = HttpClient.newBuilder().build();
        executor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET()
                            .uri(sseURI)
                            .setHeader("Accept", "text/event-stream")
                            .build();
                    Flow.Subscriber<String> sseHandler = new Flow.Subscriber<String>() {
                        Flow.Subscription s;
                        boolean initialized;

                        @Override
                        public void onSubscribe(Flow.Subscription s) {
                            this.s = s;
                            s.request(1);
                        }

                        @Override
                        public void onNext(String t) {
                            log("SSE received: " + t + "\n");
                            if (t.startsWith("data:")) {
                                String val = t.substring("data:".length());
                                if (!initialized) {
                                    String endpoint = finalServiceURI + val;
                                    workQueue.offer(endpoint);
                                    initialized = true;
                                } else {
                                    // Forward to stdout
                                    System.out.println(val);
                                }
                            }
                            s.request(1);
                        }

                        @Override
                        public void onError(Throwable thrwbl) {
                            log("SSE onERROR: " + thrwbl + "\n");
                        }

                        @Override
                        public void onComplete() {
                            log("SSE onComplete\n");
                        }

                    };
                    httpClient.send(request, HttpResponse.BodyHandlers.fromLineSubscriber(sseHandler));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        String endpoint = workQueue.take();
        InputStream in = System.in;

        log("Remote is " + endpoint + "\n");
        executor.submit(new Runnable() {

            @Override
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                try {
                    String line;
                    Files.write(f.toPath(), "Reading on System.in\n".getBytes(), StandardOpenOption.APPEND);
                    while ((line = reader.readLine()) != null) {
                        Files.write(f.toPath(), ("Received " + line + "\n").getBytes(), StandardOpenOption.APPEND);
                        HttpRequest request = HttpRequest.newBuilder()
                                .setHeader("Content-Type", "application/json-rpc")
                                .POST(HttpRequest.BodyPublishers.ofString(line))
                                .uri(new URI(endpoint))
                                .build();
                        HttpResponse<Void> response = httpClient.send(request, BodyHandlers.discarding());
                        log("Sent line, response is " + response.statusCode() + "\n");
                    }
                } catch (Exception ex) {
                    log("Exception: " + ex + "\n");
                }
            }
        });
    }

}

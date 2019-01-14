/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.examples.conference.se;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.config.Config;
import io.helidon.metrics.RegistryFactory;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * A simple service to greet you. Examples:
 *
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 *
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 *
 * Change greeting
 * curl -X PUT http://localhost:8080/greet/greeting/Hola
 *
 * The message is returned as a JSON object
 */

public class GreetService implements Service {
    private static final String CONFIG_KEY_GREETING = "app.greeting";
    private static final String DEFAULT_GREETING = "Ciao";
    /**
     * The config value for the key {@code greeting}.
     */
    private String greeting;
    private final Counter defaultMessageCounter;
    private final Counter messageCounter;
    private final Counter updateMessageCounter;

    public GreetService(Config config) {
        Config greetingConf = config.get(CONFIG_KEY_GREETING);
        this.greeting = greetingConf.asString().orElse(DEFAULT_GREETING);
        RegistryFactory metricsRegistry = RegistryFactory.getRegistryFactory().get();
        MetricRegistry appRegistry = metricsRegistry.getRegistry(MetricRegistry.Type.APPLICATION);

        this.defaultMessageCounter = appRegistry.counter("greet.default.counter");
        this.messageCounter = appRegistry.counter("greet.message.counter");
        this.updateMessageCounter = appRegistry.counter("greet.message.update.counter");

        greetingConf.onChange(newConfig -> {
            greeting = newConfig.asString().orElse(greeting);
        });
    }

    /**
     * A service registers itself by updating the routine rules.
     *
     * @param rules the routing rules.
     */
    @Override
    public final void update(final Routing.Rules rules) {
        rules
                .get("/", this::getDefaultMessage)
                .get("/{name}", this::getMessage)
                .put("/greeting/{greeting}", this::updateGreeting);
    }

    /**
     * Return a wordly greeting message.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getDefaultMessage(final ServerRequest request,
                                   final ServerResponse response) {

        String user = request.context()
                .get(SecurityContext.class)
                .map(SecurityContext::userName)
                .orElse("World");

        sendResponse(response, user);

        defaultMessageCounter.inc();
    }


    /**
     * Return a greeting message using the name that was provided.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void getMessage(final ServerRequest request,
                            final ServerResponse response) {
        String user = request.context()
                .get(SecurityContext.class)
                .map(SecurityContext::userName)
                .orElse("Anonymous");

        String name = request.path().param("name");

        sendResponse(response, name + " (security: " + user + ")");
        messageCounter.inc();
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param request  the server request
     * @param response the server response
     */
    private void updateGreeting(final ServerRequest request,
                                final ServerResponse response) {
        greeting = request.path().param("greeting");

        JsonObject returnObject = Json.createObjectBuilder()
                .add("greeting", greeting)
                .build();
        response.send(returnObject);
        updateMessageCounter.inc();
    }

    private void sendResponse(ServerResponse response, String user) {
        String msg = String.format("%s %s!", greeting, user);

        JsonObject returnObject = Json.createObjectBuilder()
                .add("message", msg)
                .build();
        response.send(returnObject);
    }
}

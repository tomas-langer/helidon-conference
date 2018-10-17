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

package io.helidon.examples.conference.mp;

import java.security.Principal;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.helidon.security.SecurityContext;
import io.helidon.security.annot.Authenticated;
import io.helidon.security.jersey.ClientSecurityFeature;
import io.helidon.webserver.opentracing.OpentracingClientFilter;

import io.opentracing.SpanContext;
import io.opentracing.util.GlobalTracer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.glassfish.jersey.server.Uri;

/**
 * A simple JAX-RS resource to greet you. Examples:
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
 * The message is returned as a JSON object.
 */
@Path("/greet")
@RequestScoped
@Authenticated
public class GreetResource {
    @SecureTracingClient
    @Uri("http://localhost:8080/greet")
    private WebTarget target;

    /**
     * The greeting message.
     */
    private String greeting = null;

    /**
     * Using constructor injection to get a configuration property.
     * By default this gets the value from META-INF/microprofile-config
     *
     * @param greetingConfig the configured greeting message
     */
    @Inject
    public GreetResource(@ConfigProperty(name = "app.greeting") final String greetingConfig) {

        if (this.greeting == null) {
            this.greeting = greetingConfig;
        }
    }

    /**
     * Return a wordly greeting message.
     *
     * @return {@link JsonObject}
     */
    @SuppressWarnings("checkstyle:designforextension")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    @Counted(name = "greet.default.counter", monotonic = true, absolute = true)
    public JsonObject getDefaultMessage(@Context SecurityContext context) {
        String user = context.getUserPrincipal()
                .map(Principal::getName)
                .orElse("World");

        return toResponse(user);
    }

    @GET
    @Path("/outbound/{name}")
    @Timed
    public JsonObject outbound(@PathParam("name") String name,
                               @Context SecurityContext context,
                               @Context SpanContext spanContext) {
        return target.path(name)
                .request()
                .header("Host", "localhost:8080")
                .property(ClientSecurityFeature.PROPERTY_CONTEXT, context)
                .property(OpentracingClientFilter.TRACER_PROPERTY_NAME, GlobalTracer.get())
                .property(OpentracingClientFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, spanContext)
                .get(JsonObject.class);
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet
     * @return {@link JsonObject}
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/{name}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    @Counted(name = "greet.message.counter", monotonic = true, absolute = true)
    public JsonObject getMessage(@PathParam("name") String name, @Context SecurityContext context) {
        String user = context.getUserPrincipal()
                .map(Principal::getName)
                .orElse("Anonymous");

        return toResponse(name + " (security: " + user + ")");
    }

    /**
     * Set the greeting to use in future messages.
     *
     * @param newGreeting the new greeting message
     * @return {@link JsonObject}
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/greeting/{greeting}")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Timed
    @Counted(name = "greet.message.update.counter", monotonic = true, absolute = true)
    public JsonObject updateGreeting(@PathParam("greeting") final String newGreeting) {
        this.greeting = newGreeting;

        JsonObject returnObject = Json.createObjectBuilder()
                .add("greeting", this.greeting)
                .build();
        return returnObject;
    }

    private JsonObject toResponse(String message) {
        String msg = String.format("%s %s!", greeting, message);

        return Json.createObjectBuilder()
                .add("message", msg)
                .build();
    }
}

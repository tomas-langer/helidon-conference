/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.examples.conference.mp;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.security.jersey.ClientSecurityFeature;
import io.helidon.webserver.opentracing.OpentracingClientFilter;

import org.glassfish.jersey.server.ClientBinding;

/**
 * A client that supports security propagation and tracing.
 */
@ClientBinding(configClass = SecureTracingClient.ClientConfig.class, inheritServerProviders = false)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface SecureTracingClient {
    class ClientConfig extends org.glassfish.jersey.client.ClientConfig {
        public ClientConfig() {
            this.register(new ClientSecurityFeature());
            this.register(new OpentracingClientFilter());
        }
    }
}

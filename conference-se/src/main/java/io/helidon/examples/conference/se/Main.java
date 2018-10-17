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

import java.io.IOException;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.Security;
import io.helidon.security.webserver.WebSecurity;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.json.JsonSupport;
import io.helidon.webserver.zipkin.ZipkinTracerBuilder;

import io.opentracing.util.GlobalTracer;

import static java.time.Duration.ofSeconds;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;

/**
 * Simple Hello World rest application.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments.
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        setupLogging();

        Config config = buildConfig();

        ServerConfiguration.Builder serverConfig = ServerConfiguration.builder(config.get("server"));
        Routing.Builder routing = Routing.builder();

        setupTracing(config, serverConfig, routing);
        setupMetrics(config, routing);
        setupSecurity(config, routing);

        addRouting(config, routing);
        startServer(config, routing);
    }

    private static void setupSecurity(Config config, Routing.Builder routing) {
        Security security = Security.fromConfig(config);
        routing.register(WebSecurity.from(security, config));
    }

    private static void setupMetrics(Config config, Routing.Builder routing) {
        routing.register(MetricsSupport.create());
    }

    private static void setupTracing(Config config,
                                     ServerConfiguration.Builder serverConfig,
                                     Routing.Builder routing) {
        config.get("services.zipkin.host")
                .asOptional(String.class)
                .map(host -> ZipkinTracerBuilder.forService("helidon-se")
                        .zipkinHost(host)
                        .build())
                .ifPresent(GlobalTracer::register);

        serverConfig.tracer(GlobalTracer.get());
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        // expected on development machine
                        // to override props for dev
                        file("conf/dev-conference-se.yaml")
                                .pollingStrategy(PollingStrategies::watch)
                                .optional(),
                        // expected in k8s runtime
                        // to configure testing/production values
                        file("conf/conference-se.yaml")
                                .pollingStrategy(regular(
                                        ofSeconds(60)))
                                .optional(),
                        // in jar file
                        // (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                .build();
    }

    private static void setupLogging() throws IOException {
        // load logging configuration
        LogManager.getLogManager().readConfiguration(
                Main.class.getResourceAsStream("/logging.properties"));
    }

    /**
     * Start the server.
     *
     * @return the created {@link WebServer} instance
     * @throws IOException if there are problems reading logging properties
     */
    protected static WebServer startServer(Config config, Routing.Builder routing) throws IOException {

        // Get webserver config from the "server" section of application.yaml
        ServerConfiguration serverConfig =
                ServerConfiguration.fromConfig(config.get("server"));

        WebServer server = WebServer.create(serverConfig, routing);

        // Start the server and print some info.
        server.start().thenAccept(ws -> {
            System.out.println(
                    "WEB server is up! http://localhost:" + ws.port());
        });

        // Server threads are not demon. NO need to block. Just react.
        server.whenShutdown().thenRun(()
                                              -> System.out.println("WEB server is DOWN. Good bye!"));

        return server;
    }

    /**
     * Creates new {@link Routing}.
     *
     * @return the new instance
     */
    private static void addRouting(Config config, Routing.Builder routing) {
        routing
                .register(JsonSupport.get())
                .register("/greet", new GreetService(config));
    }
}

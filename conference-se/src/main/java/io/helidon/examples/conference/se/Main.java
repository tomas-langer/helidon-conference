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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.LogManager;

import io.helidon.config.Config;
import io.helidon.config.PollingStrategies;
import io.helidon.health.HealthSupport;
import io.helidon.health.checks.HealthChecks;
import io.helidon.metrics.MetricsSupport;
import io.helidon.security.CompositeProviderFlag;
import io.helidon.security.CompositeProviderSelectionPolicy;
import io.helidon.security.Security;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.providers.abac.AbacProvider;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.security.providers.httpauth.UserStore;
import io.helidon.security.providers.httpsign.HttpSignProvider;
import io.helidon.security.providers.httpsign.InboundClientDefinition;
import io.helidon.security.providers.httpsign.SignedHeadersConfig;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.tracing.TracerBuilder;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.json.JsonSupport;

import org.eclipse.microprofile.health.HealthCheckResponse;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;
import static io.helidon.config.PollingStrategies.regular;
import static java.time.Duration.ofSeconds;

/**
 * Simple Hello World rest application.
 */
public final class Main {
    private static final Map<String, UserStore.User> USERS = new HashMap<>();

    static {
        USERS.put("jack", new MyUser("jack", "jackIsGreat", "admin"));
        USERS.put("jill", new MyUser("jill", "jillToo", "user"));
    }

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

        ServerConfiguration.Builder serverConfig = setupServerConfig(config);
        Routing.Builder routing = Routing.builder();

        setupTracing(config, serverConfig);
        setupMetrics(config, routing);
        setupSecurity(config, routing);
        setupHealth(config, routing);

        addRouting(config, routing);
        startServer(config, routing);
    }

    private static ServerConfiguration.Builder setupServerConfig(Config config) throws UnknownHostException {
        //return ServerConfiguration.builder(config.get("server"));

        return ServerConfiguration.builder()
                .bindAddress(InetAddress.getLocalHost())
                .port(8080);
    }

    private static void setupSecurity(Config config, Routing.Builder routing) {
        //Security security = Security.create(config);
        //routing.register(WebSecurity.create(security, config));

        Security security = Security.builder()
                .providerSelectionPolicy(selectionPolicy())
                .addProvider(AbacProvider.create())
                .addProvider(basicAuthentication(), "http-basic-auth")
                .addProvider(httpSignatures(), "http-signatures")
                .build();

        routing.register(WebSecurity.create(security));
        routing.any("/greet/greeting[/{*}]", WebSecurity.authenticate()
                .rolesAllowed("admin"));
        routing.any("/greet/jack", WebSecurity.authenticate());
    }

    private static Function<ProviderSelectionPolicy.Providers, ProviderSelectionPolicy> selectionPolicy() {
        return CompositeProviderSelectionPolicy.builder()
                .addAuthenticationProvider("http-signatures", CompositeProviderFlag.OPTIONAL)
                .addAuthenticationProvider("http-basic-auth")
                .build();
    }

    private static SecurityProvider basicAuthentication() {
        return HttpBasicAuthProvider.builder()
                .userStore(Main::getUser)
                .build();
    }

    private static SecurityProvider httpSignatures() {
        return HttpSignProvider.builder()
                .optional(true)
                .inboundRequiredHeaders(SignedHeadersConfig.builder()
                                                .config("get",
                                                        SignedHeadersConfig.HeadersConfig.create(List.of(
                                                                "date",
                                                                "(request-target)",
                                                                "host")))
                                                .build())
                .addInbound(InboundClientDefinition.builder("helidon-mp")
                                    .principalName("MP Service")
                                    .hmacSecret("badIdeaClearTextPassword!")
                                    .build())
                .build();
    }

    private static Optional<UserStore.User> getUser(String login) {
        return Optional.ofNullable(USERS.get(login));
    }

    static void setupHealth(Config config, Routing.Builder routing) {
        routing.register(HealthSupport.builder()
                                 .config(config.get("health"))
                                 .add(HealthChecks.healthChecks())
                                 .add(() -> HealthCheckResponse.named("custom")
                                         .up()
                                         .withData("timestamp", System.currentTimeMillis())
                                         .build())
                                 .build());
    }

    static void setupMetrics(Config config, Routing.Builder routing) {
        routing.register(MetricsSupport.create());
    }

    private static void setupTracing(Config config,
                                     ServerConfiguration.Builder serverConfig) {
        serverConfig.tracer(
                TracerBuilder.create(config.get("tracing"))
                        .buildAndRegister());
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
                ServerConfiguration.create(config.get("server"));

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
    static void addRouting(Config config, Routing.Builder routing) {
        routing
                .register(JsonSupport.create())
                .register("/greet", new GreetService(config));
    }

    // simplistic user implementation for the purpose of presentation
    private static final class MyUser implements UserStore.User {
        private final String login;
        private final String password;
        private final String role;

        private MyUser(String login, String password, String role) {
            this.login = login;
            this.password = password;
            this.role = role;
        }

        @Override
        public Collection<String> roles() {
            return Set.of(role);
        }

        @Override
        public String login() {
            return login;
        }

        @Override
        public char[] password() {
            return password.toCharArray();
        }
    }
}

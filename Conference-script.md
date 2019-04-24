Demo - creating an SE and MP service
---

# Prerequisites

1. Maven 3.5+
2. Java 8+
3. Optional - docker

# Script

## Step 1: Create projects
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=1.0.3 \
    -DgroupId=io.helidon.examples \
    -DartifactId=conference-se \
    -Dpackage=io.helidon.examples.conference.se
```
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=1.0.3 \
    -DgroupId=io.helidon.examples \
    -DartifactId=conference-mp \
    -Dpackage=io.helidon.examples.conference.mp
```

## Step 2: Sanity check
 - both projects:
  - `mvn clean package`
  - `java -jar target/conference-se.jar`
  - try http://localhost:8080/greet
  - stop process
  - open in IDE
 
## Step 3: Source code, running in parallel
 - Narrator: explanation of sources for both MP and SE
   - SE:
    - Reactive routing
    - Config
   - MP:
    - CDI
    - JAX-RS
    - Config vs. MP Config
   - Metrics
   - Health Checks
   - Tracing
 - modify configuration of MP project to run on a different port (8081)
    - src/main/resources/META-INF/microprofile-config.properties
 
## Step 4: Configuration sources, change support
 - customize configuration (both projects)
  - add "buildConfig" to main classes
  - add "conf/dev-conference-se.yaml" with customized configuration
  - MP: 
    - buildConfig:
    ```java
    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        file("conf/dev-conference-mp.yaml")
                                .pollingStrategy(PollingStrategies::watch)
                                .optional(),
                        classpath("application.yaml")
                                .optional(),
                        classpath("META-INF/microprofile-config.properties"))
                .build();
    }
    ```
    - change server startup to:
    ```java
        return Server.builder()
                        .config(buildConfig())
                        .build()
                        .start();
    ```
  - SE:
    - buildConfig:
    ```java
        private static Config buildConfig() {
            return Config.builder()
                .sources(
                        file("conf/dev-conference-se.yaml")
                                .pollingStrategy(PollingStrategies::watch)
                                .optional(),
                        classpath("application.yaml")
                                .optional())
                .build();
        }
    ```
    - assign it in startServer() method to config
    - update `GreetService`:
    - add
    `private final Supplier<String> greetingSupplier;`
    - assign value to the field in the new constructor
    `greetingConf.asString().supplier(DEFAULT_GREETING);`
    - modify sendResponse():
      `String msg = String.format("%s (%s) %s!", greetingSupplier.get(), greeting, name);`
  - Modify the configuration and see the result
  
## Step 5: Metrics
 - MP already has metrics enabled (see http://localhost:8081/metrics)
    - add a metric annotation to default message
    ```java
    @Timed
    @Counted(name = "greet.default.counter", monotonic = true, absolute = true)
    ```
    - see http://localhost:8081/metrics/application/greet.default.counter
    - see http://localhost:8081/metrics/application/io.helidon.examples.conference.mp.GreetResource.getDefaultMessage
 - in SE we need to add them - no magic :)
    - add metric support to dependencies (already in the source code):
    ```xml
    <dependency>
        <groupId>io.helidon.metrics</groupId>
        <artifactId>helidon-metrics</artifactId>
    </dependency>
    ```
    - add metric support to routing builder (already in the source code):
    `.register(MetricsSupport.create())`
    - add metric support to GreetService, constructor:
    ```java
    RegistryFactory metricsRegistry = RegistryFactory.getRegistryFactory().get();
    MetricRegistry appRegistry = metricsRegistry.getRegistry(MetricRegistry.Type.APPLICATION);   
    this.defaultMessageCounter = appRegistry.counter("greet.default.counter");
     ```
    - add metric support to GreetService, method getDefaultMessage:
    `defaultMessageCounter.inc();`
    - rerun 
    - see http://localhost:8080/metrics/application/greet.default.counter
 - narrator
    - show other metrics (vendor, base)
    - show json access

## Step 6: Health checks
### MP Health checks
 - works out of the box 
 - see http://localhost:8081/health
 - built-in healtchecks can be disabled (already in source code):
   ```xml
   <dependency>
       <groupId>io.helidon.microprofile.bundles</groupId>
       <artifactId>helidon-microprofile-1.2</artifactId>
       <exclusions>
           <exclusion>
               <groupId>io.helidon.microprofile.health</groupId>
               <artifactId>helidon-microprofile-health-checks</artifactId>
           </exclusion>
       </exclusions>
   </dependency>
   ```
 - add a custom healthcheck
     ```java
     package io.helidon.examples.conference.mp;
     
     import javax.enterprise.context.ApplicationScoped;
     import javax.inject.Inject;
     
     import org.eclipse.microprofile.health.Health;
     import org.eclipse.microprofile.health.HealthCheck;
     import org.eclipse.microprofile.health.HealthCheckResponse;
     
     @Health
     @ApplicationScoped
     public class GreetHealthcheck implements HealthCheck {
         private GreetingProvider provider;
     
         @Inject
         public GreetHealthcheck(GreetingProvider provider) {
             this.provider = provider;
         }
     
         @Override
         public HealthCheckResponse call() {
             String message = provider.getMessage();
             return HealthCheckResponse.named("greeting")
                     .state("Hello".equals(message))
                     .withData("greeting", message)
                     .build();
         }
     }
     ```
 - rerun and refresh healthcheck (it should be DOWN)
 - update message to "Hello":
   `curl -i -X PUT -H "Content-Type: application/json" -d '{}' http://localhost:8081/greet/greeting/Hello`
 - refresh healthchecks (it should be UP)
 
### SE Health checks
Add health check to module (already in source code):
```xml
<dependency>
    <groupId>io.helidon.health</groupId>
    <artifactId>helidon-health</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.health</groupId>
    <artifactId>helidon-health-checks</artifactId>
</dependency>
```

Modify configuration of `HealthSupport`:
```java
HealthSupport health = HealthSupport.builder()
     .add(HealthChecks.healthChecks()) // built-in health checks
     .config(config.get("health")) // support for exclusions and modification of context root
     .add(() -> HealthCheckResponse.named("custom") // a custom health check
             .up()
             .withData("timestamp", System.currentTimeMillis())
             .build())
     .build());
```

## Step 7: Connect the services
 - add a web target to the GreetResource of MP service:
    ```java
    @Uri("http://localhost:8080/greet")
    @SecureClient
    private WebTarget target;
    ```
 - add a new method that calls the SE service:
     ```java
     @GET
         @Path("/outbound/{name}")
         public JsonObject outbound(@PathParam("name") String name) {
             return target.path(name)
                     .request()
                     .accept(MediaType.APPLICATION_JSON_TYPE)
                     .get(JsonObject.class);
         }
     ```
 - now we can try that all this works:
   http://localhost:8081/greet/outbound/jack
   
## Step 8: Tracing

Helidon has an integration with Zipkin tracer. To start the tracer
 you can use docker:
`docker run -d -p 9411:9411 openzipkin/zipkin`

This will start the Zipkin tracer on `http://localhost:9411` - this is
also the default configuration that Helidon expects.

Once the MP service is integrated with Zipkin, it will
automatically propagate the Zipkin headers to connect the traces
of MP and SE service.

You can check the progress on `http://localhost:9411/zipkin/`

### Tracing in MP

Add the following dependencies to your `pom.xml`:

```xml
<dependency>
    <groupId>io.helidon.microprofile.tracing</groupId>
    <artifactId>helidon-microprofile-tracing</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-zipkin</artifactId>
</dependency>
```

Configure the service name in a property file:
`tracing.service=helidon-mp`

### Tracing in SE

Add the following dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-zipkin</artifactId>
</dependency>
```

Register the tracer with webserver:
```java
ServerConfiguration serverConfig =
                ServerConfiguration.builder(config.get("server"))
                        .tracer(TracerBuilder.create("helidon-se")
                                        .buildAndRegister())
                        .build();
```

## Step 9: Fault Tolerance

Shutdown the SE service.

Try invoking the `/greet/outbound` endpoint - you should get an internal server error.

Add annotation to the method `outbound` in `GreetResource.java`:
`@Fallback(fallbackMethod = "onFailureOutbound")`

And create the fallback method:
```java
public JsonObject onFailureOutbound(String name, SecurityContext context) {
    return Json.createObjectBuilder().add("Failed", name).build();
}
```

Validate that application works as expected when both services are running.

Shutdown the SE service.

Try again the `/greet/outbound` endpoint - you should see
 the "Failed" message instead of an internal server error.

## Step 10: Security (If time permits)
- security
  - http basic with identity propagation
  - http signatures


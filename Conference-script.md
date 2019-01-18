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
    -DarchetypeVersion=0.11.0 \
    -DgroupId=io.helidon.examples \
    -DartifactId=conference-se \
    -Dpackage=io.helidon.examples.conference.se
```
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=0.11.0 \
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
    - add parameter to resource (`io.helidon.config.Config`)
    - use a supplier
    - return both the value from supplier and provider (method createResponse):
      `String msg = String.format("%s (%s) %s!", messageSupplier.get(), greetingProvider.getMessage(), who);`
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
    - add
    `private final Supplier<String> greetingSupplier;`
    - assign values to the field in the new constructor
    - modify getDefaultMessage:
      `String msg = String.format("%s (%s) %s!", greetingSupplier.get(), greeting, "World");`
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
    - add metric support to dependencies:
    ```xml
    <dependency>
        <groupId>io.helidon.metrics</groupId>
        <artifactId>helidon-metrics</artifactId>
    </dependency>
    ```
    - add metric support to routing builder:
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
 - works out of the box 
 - see http://localhost:8081/health
 - built-in healtchecks can be disabled:
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
Add health check to module:
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

And configure `HealthSupport`:
```java
routing.register(HealthSupport.builder()
     .config(config.get("health")) // support for exclusions and modification of context root
     .add(HealthChecks.healthChecks()) // built-in health checks
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
   
## Step 8: If time permits
- security
  - http basic with identity propagation
  - http signatures
- tracing
  - docker (zipkin)
  - custom span

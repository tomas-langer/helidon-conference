# helidon-conference
Helidon project that uses both Helidon MP and Helidon SE microservices.

## Prerequisites

1. Maven 3.5+
2. Java 8+
3. Optional - docker

## 1. Create maven projects
Prepare a directory that will hold your projects. Once within this directory, use 
the following commands to generate the SE and MP Helidon projects.

The commands are the same ones as used in our quides: https://helidon.io/docs/latest/#/guides/01_overview


Helidon SE - Linux and MacOS
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=1.2.0 \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-se \
    -Dpackage=io.helidon.examples.quickstart.se
```

Helidon MP - Linux and MacOS
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=1.2.0 \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

_On Windows, please remove the backslashes and end of lines_

### Verification
You can verify the projects are correctly created as follows:

```bash
cd helidon-quickstart-se
mvn clean package
java -jar target/helidon-quickstart-se.jar
# Now you can excercise endpoints from a browser or curl
# Afterwards Ctrl-c to end the program

cd ../helidon-quickstart-mp
mvn clean package
java -jar target/helidon-quickstart-mp.jar
# Now you can excercise endpoints from a browser or curl
# Afterwards Ctrl-c to end the program
```

`curl` commands can be found in `README.md` in each project 

## 2. IDE (Recommended)
Open the project directory in your favorite IDE and add the two `pom.xml`
maven files as modules (depends on IDE used).


## 3. Running in parallel
When the projects are created, both run on port `8080`. As we want to run
both in parallel, let's modify the listen port of the MP service to `8081`.

In our example, we use the MP specific configuration file 
`helidon-quickstart-mp/src/main/resources/META-INF/microprofile-config.properties`.

Modify the `server.port` property to value `8081`

Now we can run both project in parallel without conflicts.

## 4. Configuration sources
As we have seen, MP uses the `microprofile-config.properties`. 
In addition we can use `YAML` configuration files, as we have the module on
classpath (explicitly in our SE application):
```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

This dependency means that our applications will read `application.yaml` files from
the classpath (see SE configuration file `helidon-quickstart-se/src/main/resources/application.yaml`).

We now want to add additional files to be able to override configuration on each environment.
Let's add `conf/se.yaml` and `conf/mp.yaml` files as 
configuration sources.

As we want to read these as files, we either must configure the path as absolute, or
start the application from the correct directory.

### Custom configuration file in Helidon SE
Let's add a `buildConfig` method to the `Main` class of Helidon SE. 
The source code defines:
 - the file (relative path)
 - `pollinStrategy` - watching the file for changes, application can listen on such changes
 - `optional` - the startup sequence will not fail if file is missing
 - we have also added a `se-test.yaml` optional configuration to allow unit tests
    to override configuration (such as security), so our unit tests can run
    on a different port and use different security

```java
import io.helidon.config.PollingStrategies;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

//...

private static Config buildConfig() {
    return Config.builder()
        .sources(
                classpath("se-test.yaml").optional(),
                file("../conf/se.yaml")
                        .pollingStrategy(PollingStrategies::watch)
                        .optional(),
                classpath("application.yaml"))
        .build();
}
```

Now we need to modify the configuration used. The line

`Config config = Config.create();`

must be changed to

`Config config = buildConfig();`

Now if we start our application, nothing is changed.
Let's create the `conf/se.yaml` file with the following content:
```yaml
app:
  greeting: "Hallo"
```
Now after restart, the message should be changed.

If the application is started from the `helidon-quickstart-se` folder, the 
 configuration is correctly located.

### Custom configuration file in Helidon MP
Let's add a `buildConfig` method to the `Main` class of Helidon MP. 
The source code defines:
 - the file (relative path)
 - `pollinStrategy` - watching the file for changes, application can listen on such changes
 - `optional` - the startup sequence will not fail if file is missing
 - the `application.yaml` is also defined as optional, as we do not use it (yet)

```java
import io.helidon.config.PollingStrategies;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

//...

private static Config buildConfig() {
    return Config.builder()
        .sources(
                file("../conf/mp.yaml")
                        .pollingStrategy(PollingStrategies::watch)
                        .optional(),
                classpath("application.yaml").optional(),
                classpath("META-INF/microprofile-config.properties"))
        .build();
}
```

Now we need to modify the configuration used by Server. The line:

`return Server.create().start()`

must be changed to:

```java
return Server.builder()
             .config(buildConfig())
             .build()
             .start();
``` 

Let's create the `conf/mp.yaml` file with the following content:
```yaml
app:
  greeting: "MP Hallo"
```

Validate that the configuration was used by our MP application.
If the application is started from the `helidon-quickstart-mp` folder, the 
 configuration is correctly located.


## 5. Configuration changes (SE)
Let us modify our SE application to react on changed configuration.

Go to constructor of `GreetService` and change its code:

```java
Config greetingConfig = config.get("app.greeting");

// initial value
greeting.set(greetingConfig.asString().orElse("Ciao"));

// on change listener
greetingConfig.onChange((Consumer<Config>) cfg -> greeting.set(cfg.asString().orElse("Ciao")));
```

Now run the application and check the message (it should be "Hallo World!").
If you modify the `se.yaml` file and change the greeting to "SE Hallo", the message
return will change to "SE Hallo World!"

On MacOs, please give it a few seconds.

## 6. Metrics 
Metrics are already enabled in both projects:
 
http://localhost:8080/metrics

http://localhost:8081/metrics

Let's add custom metrics to our applications.

### MP Metrics

To add a new metric in MP, simply annotate the JAX-RS resource with one of the annotations.
Let's modify `GreetResource.getDefaultMessage`:

```java
import org.eclipse.microprofile.metrics.annotation.Timed;

//...
@GET
@Produces(MediaType.APPLICATION_JSON)
@Timed
public JsonObject getDefaultMessage() {
    return createResponse("World");
}
``` 

Restart the application and access the endpoint (http://localhost:8081/greet).
Then validate the metric is present:
`curl -H "Accept: application/json" http://localhost:8081/metrics/application`

Expected result is similar to this:
```json
{
   "io.helidon.examples.quickstart.mp.GreetResource.getDefaultMessage" : {
      "oneMinRate" : 0.00821011325831546,
      "p95" : 24025911,
      "max" : 24025911,
      "fifteenMinRate" : 0.00105986292046305,
      "stddev" : 0,
      "mean" : 24025911,
      "p50" : 24025911,
      "count" : 1,
      "p75" : 24025911,
      "p999" : 24025911,
      "p98" : 24025911,
      "meanRate" : 0.0141106839296138,
      "min" : 24025911,
      "fiveMinRate" : 0.00289306852357793,
      "p99" : 24025911
   }
}
```

In a similar way, we could use most of the metrics from MP Metrics specification:

- `Counted`
- `Metered`
- `Timed`

### SE Metrics
In SE, there is no injection or annotation processing, so to add a metric, we need to 
do so by hand.

We will modify the constructor of our `GreetService` again to create the metric. We will
also need to update the `getDefaultMessageHandler` to use the metric.

```java
// field
private final Timer defaultMessageTimer;

//...

GreetService(Config config) {
    // our configuration code
    // ...
    
    RegistryFactory metricsRegistry = RegistryFactory.getInstance();
    MetricRegistry appRegistry = metricsRegistry.getRegistry(MetricRegistry.Type.APPLICATION);
    this.defaultMessageTimer = appRegistry.timer("greet.default.timer");
}

//...

private void getDefaultMessageHandler(ServerRequest request,
                                   ServerResponse response) {
    Timer.Context timerContext = defaultMessageTimer.time();
    sendResponse(response, "World");
    response.whenSent()
        .thenAccept(res -> timerContext.stop());        
}
``` 

Restart the application and access the endpoint (http://localhost:8080/greet).
Then validate the metric is present:
`curl -H "Accept: application/json" http://localhost:8080/metrics/application`

Expected result is similar to this:
```json
{
   "greet.default.timer" : {
      "count" : 4,
      "max" : 99604487,
      "p98" : 99604487,
      "fiveMinRate" : 0.0127893407850335,
      "p75" : 2421876,
      "p95" : 99604487,
      "min" : 1267507,
      "mean" : 25662839.2921554,
      "oneMinRate" : 0.0541447534553673,
      "stddev" : 41846753.4348158,
      "fifteenMinRate" : 0.0043831483778439,
      "p50" : 2265093,
      "p999" : 99604487,
      "meanRate" : 0.159544963048957,
      "p99" : 99604487
   }
}
```

In a similar way, we could use most of the metrics from MP Metrics specification:

- `Timer`
- `Counter`
- `Meter`


## 7. Health Checks
Health checks are already enabled in both projects. 

Original MP Health endpoints (all healthchecks available): 

http://localhost:8080/health

http://localhost:8081/health

New MP Health endpoints (empty for now):

Readiness checks:
http://localhost:8080/health/ready
http://localhost:8081/health/ready

Liveness checks:
http://localhost:8080/health/live
http://localhost:8081/health/live

Let's add custom health check to our applications.

### MP Health Check
Adding a custom health check in MP utilizes CDI. 
Simply create a new class `GreetHealthcheck` with the following content:

```java
package io.helidon.examples.quickstart.mp;
     
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
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
After restarting the application and checking the health check endpoint, we should
see the application is `DOWN`, as the greeting is "MP Hallo" instead of "Hello".
In MP we do not listen on configuration changes, so to fix the greeting, we can use
the update greeting endpoint (that changes the greeting in memory):

`curl -i -X PUT -H "Content-Type: application/json" -d '{"greeting": "Hello"}' http://localhost:8081/greet/greeting`

The next request to health endpoint should return `UP`.
Our new health check also provides its result in the liveness checks on
http://localhost:8081/health/live

### SE Health Check
Custom health checks may be added when creating the `HealthSupport` instance
in our SE `Main` class in method `createRouting`.

Let's add a health check that is always up and just sends the current time in millis:

```java
HealthSupport health = HealthSupport.builder()
        .addLiveness(HealthChecks.healthChecks())   // Adds a convenient set of checks
        .addLiveness(() -> HealthCheckResponse.named("custom") // a custom (liveness) health check
                .up()
                .withData("timestamp", System.currentTimeMillis())
                .build())
        .build();
```

Restart the application and verify the health endpoint, that it contains the 
new health check.
As we have added all the health checks to liveness, we can also see them in
http://localhost:8080/health/live


## 8. Connect the services

### MP HTTP client
We will use a JAX-RS client to connect from our MP service to the SE service.

Let's modify the `GreetResource`.

Add a web target:

```java
import javax.ws.rs.client.WebTarget;
import org.glassfish.jersey.server.Uri;
import io.helidon.security.integration.jersey.SecureClient;
//...

@Uri("http://localhost:8080/greet")
@SecureClient
private WebTarget target;
```

And a new resource method to handle the outbound call:
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

Now restart the MP application and call the endpoint:
`curl -i http://localhost:8081/greet/outbound/jack`

We should get:
```text
HTTP/1.1 200 OK
Content-Type: application/json
Date: Wed, 10 Jul 2019 16:11:55 +0200
connection: keep-alive
content-length: 28

{"message":"SE Hallo jack!"}%
```

### SE HTTP Client
We have a choice for Helidon SE of using the HTTP client in Java (available since version 11), or any reactive/asynchronous
HTTP client.
For our example we will use JAX-RS reactive client from Jersey.
This adds a few dependencies to our project:

```xml
<dependency>
    <groupId>io.helidon.security.integration</groupId>
    <artifactId>helidon-security-integration-jersey</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-jersey-client</artifactId>
</dependency>
<dependency>
    <groupId>org.glassfish.jersey.core</groupId>
    <artifactId>jersey-client</artifactId>
</dependency>
<dependency>
    <groupId>org.glassfish.jersey.inject</groupId>
    <artifactId>jersey-hk2</artifactId>
</dependency>
```

Add a `WebTarget` to the `GreetService`:

```
private WebTarget webTarget;
```

Update the constuctor to set up the client and configure the `WebTarget`:

```
Client jaxRsClient = ClientBuilder.newBuilder()
        .register(new ClientSecurityFeature())
        .build();

webTarget = jaxRsClient.target("http://localhost:8081/greet");
```

Let's add a new routing method to our `GreetService` in `update(Rules)` method:
```java
.get("/outbound", this::outbound)
``` 

And create the outbound method itself:
```java
private void outbound(ServerRequest request, ServerResponse response) {
    Invocation.Builder requestBuilder = webTarget.request();

    // propagate security if defined
    request.context()
            .get(SecurityContext.class)
            .ifPresent(ctx -> requestBuilder.property(ClientSecurityFeature.PROPERTY_CONTEXT, ctx));

    // propagate tracing
    requestBuilder.property(ClientTracingFilter.CURRENT_SPAN_CONTEXT_PROPERTY_NAME, request.spanContext());

    // and reactive jersey client call
    requestBuilder.rx()
            .get(String.class)
            .thenAccept(response::send)
            .exceptionally(throwable -> {
                // process exception
                response.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                response.send("Failed with: " + throwable);
                return null;
            });
}
```

## 9. Tracing

_In version 1.2.0 we have an unfortunate bug with Helidon MP and Zipkin tracer. Please use Jaeger tracing with MP if you
    want to try this feature. For Helidon SE, you can safely use Zipkin to use GraalVM native-image_
    
Even when using Zipkin integration in Helidon, we can use Jaeger server, as it also accepts spans in Zipkin format on the same
    port as Zipkin.

To use tracing with Helidon, we need to connect the services to a tracer.
Helidon supports "Zipkin" and "Jaeger" tracers.
For our examples, we will use Jaeger server, in MP Jaeger integration and in SE
 Zipkin integration as it works without issues with GraalVM native-image (that we use further down).

To use Jaeger tracer, please start the Jaeger docker image.

If this is the first time you use Jaeger:
`  docker run -d --name jaeger -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 -p 5775:5775/udp -p 6831:6831/udp -p 6832:6832/udp -p 5778:5778 -p 16686:16686 -p 14268:14268 -p 9411:9411 jaegertracing/all-in-one:latest`

If you already have the container ready:
`docker start jaeger`

The Jaeger UI is available on:
http://localhost:16686/search

### Add Jaeger tracer to MP
We need to add the integration library to `pom.xml`:

```xml
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-jaeger</artifactId>
</dependency>
```

and we need to configure the tracing service name (let's add it to `microprofile-config.properties`):
`tracing.service=helidon-mp`

### Add Zipkin tracer to SE
We need to add the Tracer abstraction and Zipkin integration libraries to `pom.xml`:

```xml
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.tracing</groupId>
    <artifactId>helidon-tracing-zipkin</artifactId>
</dependency>
```

and configure the tracing service name (in `application.yaml`):
`tracing.service: "helidon-se"`

As the last step, we need to configure the tracer with Helidon WebServer:
In SE `Main.startServer()`:

```java
ServerConfiguration serverConfig =
        ServerConfiguration.builder(config.get("server"))
                .tracer(TracerBuilder.create(config.get("tracing")).buildAndRegister())
                .build();
``` 

### Trace services
Now we have both SE and MP service connected to Zipkin, we can invoke requests
on each and see the traces.

To see the true power of tracing, invoke the outbound service:
`curl -i http://localhost:8081/greet/outbound/jack`

And see the trace in the tracer.

## 10. Fault Tolerance
Fault tolerance is currently available in MP only, as it heavily depends on 
annotations.

To see the power of fault tolerance, let's shut down the SE service and invoke
our favorite outbound endpoint. You should get:
`HTTP/1.1 500 Internal Server Error`, as the request with the client fails and 
we do not have any error handling in place.

We can now add a `Fallback` annotation to the `GreetResource.outbound` method:

`@Fallback(fallbackMethod = "outboundFailed")`

and create the fallback method:

```java
public JsonObject outboundFailed(String name) {
    return Json.createObjectBuilder().add("Failed", name).build();
}
```

_Note that the signature (parameters and response type) must be exactly the same 
as for the original method_

Restart the MP service and try the call again. This time the response should be:
```text
HTTP/1.1 200 OK
Content-Type: application/json
Date: Wed, 10 Jul 2019 16:35:00 +0200
connection: keep-alive
content-length: 17

{"Failed":"jack"}%
```

This Fault Tolerance annotation is one of many, you can use:

- `Fallback`
- `CircuitBreaker`
- `Bulkhead`
- `Retry`
- `Timeout`

See the MP Fault Tolerance spec for details:
https://github.com/eclipse/microprofile-fault-tolerance/releases/download/2.0/microprofile-fault-tolerance-spec-2.0.html


## 11. GraalVM `native-image`
GraalVM provides a feature of ahead-of-time compilation into native code.
This is supported by Helidon SE (with some restrictions). The quickstart example
is capable of compilation using `native-image`.

There are two options:
1. Compile using local installation of GraalVM
2. Compile using docker image into a docker image

We will use the second approach.
Start in the directory of the SE service:
```bash
docker build -t helidon-quickstart-se-native -f Dockerfile.native . 
```

The first build takes a bit longer, as it downloads necessary libraries from
Maven central into the docker image. Further builds use the downloaded libraries.

The above command creates a docker image `helidon-quickstart-se-native`.
To run it locally, shut down SE service and run:
`docker run --rm -p 8080:8080 helidon-quickstart-se-native:latest`

## 12. Security
Recommended approach is to configure security in a configuration file.
As security requires more complex configuration, using a yaml file
is recommended.

We will secure our services as follows:

_MP Service_
 - Authentication: HTTP Basic authentication (NEVER use this in production)
 - Authorization: Role based access control
 - Identity propagation: 
    - HTTP Basic authentication (user)
    - HTTP Signatures (service)
  
_SE Service_
 - Authentication:
    - HTTP Basic authentication (user)
    - HTTP Signatures (service)
 - Authorization: Role based access control
 
The common configuration (exactly the same in SE and MP) uses the ABAC and Basic authentication providers:
```yaml
security:
  providers:
    # enable the "ABAC" security provider (also handles RBAC)
    - abac:
    # enabled the HTTP Basic authentication provider
    - http-basic-auth:
        realm: "helidon"
        users:
          - login: "jack"
            password: "password"
            roles: ["admin"]    
          - login: "jill"
            password: "password"
            roles: ["user"]
          - login: "joe"
            password: "password"
```

### Helidon MP
Once the above configuration is added to the `mp.yaml`, we can try if security works.
Let's modify our `GreetResource.outbound` method. 
This method will be available to users in role `user` or `admin`

```java
@GET
@Path("/outbound/{name}")
@Fallback(fallbackMethod = "outboundFailed")
@RolesAllowed({"user", "admin"})
public JsonObject outbound(@PathParam("name") String name) {
```

If the application is restarted and you invoke the endpoint
`curl -i http://localhost:8081/greet/outbound/jack`
You get the following response:
```text
HTTP/1.1 403 Forbidden
Content-Length: 0
```

Authorization itself does not imply authentication. Simple way to
enforce authentication is to annotate either class or method as `@Authenticated`:

```java
import io.helidon.security.annotations.Authenticated;
//...

@GET
@Path("/outbound/{name}")
@Fallback(fallbackMethod = "outboundFailed")
@RolesAllowed({"user", "admin"})
@Authenticated
public JsonObject outbound(@PathParam("name") String name) {
```

Now when we restart and re-request the endpoint, we get:
```text
HTTP/1.1 401 Unauthorized
Content-Length: 0
...
```

Now we can request the endpoint as any user in `user` or `admin` role.
You can try the following commands to see the results:
`curl -i -u jack:password http://localhost:8081/greet/outbound/Stuttgart`
`curl -i -u jill:password http://localhost:8081/greet/outbound/Stuttgart`
`curl -i -u joe:password http://localhost:8081/greet/outbound/Stuttgart`
`curl -i -u john:password http://localhost:8081/greet/outbound/Stuttgart`

We should see that `jack` and `jill` get the response, `joe` is forbidden (unauthorized)
and `john` is unauthorized (meaning unauthenticated).
Also investigate the traces in Zipkin, as you should nicely see what happened. 


Let's modify our method to use the username of the logged in user. We will
remove the path parameter and instead use the current username.
Note that you also need to update the `outboundFailed`
fallback method, as the signature changes.
Also we send the current security context, so security can be propagated.

```java
import io.helidon.security.SecurityContext;
//...

@GET
@Path("/outbound")
@Fallback(fallbackMethod = "outboundFailed")
@RolesAllowed({"user", "admin"})
@Authenticated
public JsonObject outbound(@Context SecurityContext context) {
    return target.path(context.userName())
            .request()
            .property(ClientSecurityFeature.PROPERTY_CONTEXT, context)
            .accept(MediaType.APPLICATION_JSON_TYPE)
            .get(JsonObject.class);
}

public JsonObject outboundFailed(SecurityContext context) {
    return Json.createObjectBuilder()
            .add("Failed", context.userName())
            .build();
}
```
You can try the following commands to see the results:
`curl -i -u jack:password http://localhost:8081/greet/outbound`
`curl -i -u jill:password http://localhost:8081/greet/outbound`
`curl -i -u joe:password http://localhost:8081/greet/outbound`
`curl -i -u john:password http://localhost:8081/greet/outbound`

Before connecting to SE, we need to add the following code to our `Main` class of MP, to support security propagation:
```java
// as we use default HTTP connection for Jersey client, we should set this as we set the Authorization header
// when propagating security
System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
```

### Helidon SE

Dependencies:
```xml
<dependency>
    <groupId>io.helidon.security.integration</groupId>
    <artifactId>helidon-security-integration-webserver</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-abac</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-auth</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.security.providers</groupId>
    <artifactId>helidon-security-providers-http-sign</artifactId>
</dependency>
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-object-mapping</artifactId>
</dependency>
```

In SE, we need to explicitly add Security to configuration:

## Running on multiple ports (MP)

Helidon WebServer has the concept of named ports that can have routings assigned to them. 
In Helidon MP, we can run our main application on the default port (all JAX-RS resources) and assign some of the
MP "management" endpoints to different ports.
The following configuration (you can add this to `conf/mp.yaml`) will move metrics and health check endpoints to port
    `9081` (this is commented out in the file in this project, so previous examples work nicely)
     
```yaml
server:
  port: 8081
  host: "localhost"
  sockets:
    admin:
      port: 9081
      bind-address: "localhost"

metrics:
  routing: "admin"

health:
  routing: "admin"
```

After restarting the MP server, you can find metrics and health on the following endpoints:
http://localhost:9081/health
http://localhost:9081/metrics

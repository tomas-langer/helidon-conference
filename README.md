# helidon-conference
Example for conferences

The MP and SE project were generated using helidon quickstart and then
modified to live in a single pom project (e.g. dependency management and 
plugin management is unified in parent project).

## conference-se
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-se \
    -DarchetypeVersion=0.10.2 \
    -DgroupId=io.helidon.examples \
    -DartifactId=conference-se \
    -Dpackage=io.helidon.examples.conference.se
```

## conference-mp
```bash
mvn archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=0.10.2 \
    -DgroupId=io.helidon.examples \
    -DartifactId=conference-mp \
    -Dpackage=io.helidon.examples.conference.mp
```

## Endpoints
Each application opens a few endpoints that return JSON messages.

### conference-se
- http://localhost:8080/greet - unsecured endpoint that returns a "Hello World"
- http://localhost:8080/greet/jack - secured endpoint that requires login (e.g. jack/jackIsGreat or jill/jillToo)
- http://localhost:8080/greet/greeting - secured endpoint that requires a user in "admin" role (jack)
- http://localhost:8080/greet/<message> - unsecured endpoint that returns a "Hello <message>"
- http://localhost:8080/metrics - all available metrics (has "vendor", "basic" and "application" subpaths)

### conference-mp
- http://localhost:8081/greet - secured endpoint that returns "Hello <username>"
- http://localhost:8081/greet/<message> - secured endpoint that returns "Hello <message> (security: <username>)" 
- PUT to http://localhost:8081/greet/greeting - secured endpoint that requires a user in "admin" role (jack) to change the message
- http://localhost:8081/metrics - all available metrics (has "vendor", "basic" and "application" subpaths)
- http://localhost:8081/health - predefined healthchecks (can be extended by custom)

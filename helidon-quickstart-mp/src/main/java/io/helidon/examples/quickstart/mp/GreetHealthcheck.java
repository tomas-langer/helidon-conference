package io.helidon.examples.quickstart.mp;

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
package com.securepay.observability;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Component;

@Component
public class OpenTelemetryRestClientCustomizer implements RestClientCustomizer {
    private static final TextMapSetter<HttpRequest> SETTER = (request, key, value) -> {
        if (request != null) request.getHeaders().set(key, value);
    };

    @Override
    public void customize(org.springframework.web.client.RestClient.Builder builder) {
        var propagator = W3CTraceContextPropagator.getInstance();
        builder.requestInterceptor((request, body, execution) -> {
            if (request.getHeaders().getFirst("traceparent") == null) {
                propagator.inject(Context.current(), request, SETTER);
            }
            return execution.execute(request, body);
        });
    }
}

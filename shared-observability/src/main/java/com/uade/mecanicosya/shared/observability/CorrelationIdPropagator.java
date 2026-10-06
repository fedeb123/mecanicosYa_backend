package com.uade.mecanicosya.shared.observability;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Reenvía el identificador de correlación del request actual en las llamadas REST a otros servicios.
 */
public class CorrelationIdPropagator implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && request.getHeaders().getFirst(CorrelationIdFilter.HEADER) == null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
